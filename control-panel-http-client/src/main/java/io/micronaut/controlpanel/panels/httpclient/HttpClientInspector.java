/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.httpclient;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Collects the HTTP clients of the application: declarative {@link Client} interfaces, the services configured
 * under {@value ServiceHttpClientConfiguration#PREFIX}, and the instances service discovery reports for them.
 *
 * <p>Nothing is sent over the network. URLs are shown without user info or query strings, which can carry
 * credentials.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
public class HttpClientInspector {

    static final String SAME_SERVER = "same server";

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientInspector.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public HttpClientInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return the HTTP clients, sorted by id
     */
    public List<HttpClientInfo> inspect() {
        Map<String, Builder> clients = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Object.class)) {
            definition.findAnnotation(Client.class).ifPresent(client -> addDeclarativeClient(clients, definition, client));
        }
        for (ServiceHttpClientConfiguration configuration : beanContext.getBeansOfType(ServiceHttpClientConfiguration.class)) {
            clients.computeIfAbsent(configuration.getServiceId(), Builder::new).configuration = configuration;
        }
        for (ServiceInstanceList instanceList : beanContext.getBeansOfType(ServiceInstanceList.class)) {
            clients.computeIfAbsent(instanceList.getID(), Builder::new).instanceList = instanceList;
        }
        List<HttpClientInfo> result = new ArrayList<>(clients.size());
        clients.values().forEach(builder -> result.add(builder.build(filters(builder))));
        return result;
    }

    private static void addDeclarativeClient(Map<String, Builder> clients, BeanDefinition<?> definition, AnnotationValue<Client> client) {
        String id = client.stringValue("id").filter(value -> !value.isBlank())
            .or(() -> client.stringValue().filter(value -> !value.isBlank()))
            .orElse("/");
        Builder builder;
        if (id.equals("/")) {
            builder = clients.computeIfAbsent(SAME_SERVER, Builder::new);
        } else if (isUrl(id)) {
            String url = sanitize(id);
            builder = clients.computeIfAbsent(url, Builder::new);
            builder.annotationUrl = url;
        } else {
            builder = clients.computeIfAbsent(id, Builder::new);
        }
        Class<?> type = definition instanceof AdvisedBeanType<?> advised ? advised.getInterceptedType() : definition.getBeanType();
        if (!builder.declarativeTypes.contains(type.getName())) {
            builder.declarativeTypes.add(type.getName());
            // Filters are resolved at runtime with the raw @Client id and the interface's own annotation metadata
            builder.filterContexts.add(new ClientFilterResolutionContext(List.of(id), definition.getAnnotationMetadata()));
        }
        client.stringValue("path").filter(path -> !path.isBlank()).ifPresent(builder.annotationPaths::add);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<String> filters(Builder builder) {
        if (builder.id.equals(SAME_SERVER)) {
            return List.of();
        }
        List<ClientFilterResolutionContext> contexts = builder.filterContexts.isEmpty()
            ? List.of(new ClientFilterResolutionContext(List.of(builder.id), AnnotationMetadata.EMPTY_METADATA))
            : builder.filterContexts;
        try {
            HttpClientFilterResolver resolver = beanContext.findBean(HttpClientFilterResolver.class).orElse(null);
            if (resolver == null) {
                return List.of();
            }
            return contexts.stream()
                .flatMap(context -> ((List<?>) resolver.resolveFilterEntries(context)).stream())
                .map(entry -> filterName((HttpFilterResolver.FilterEntry) entry))
                .distinct()
                .sorted()
                .toList();
        } catch (RuntimeException e) {
            LOG.debug("Unable to resolve the filters of HTTP client '{}'", builder.id, e);
            return List.of();
        }
    }

    private String filterName(HttpFilterResolver.FilterEntry entry) {
        // Method-based filters (@ClientFilter with @RequestFilter or @ResponseFilter methods) are wrapped in an internal
        // type, so the declaring bean is read from the filter method's metadata instead
        if (entry.getAnnotationMetadata() instanceof ExecutableMethod<?, ?> method) {
            return method.getDeclaringType().getName();
        }
        // HttpClientFilter beans are wrapped in an internal type too, and carry the metadata of their bean definition
        for (BeanDefinition<HttpClientFilter> definition : beanContext.getBeanDefinitions(HttpClientFilter.class)) {
            if (definition.getAnnotationMetadata() == entry.getAnnotationMetadata()) {
                return definition.getBeanType().getName();
            }
        }
        return entry.getFilter().getClass().getName();
    }

    private static boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    /**
     * Removes the user info, query and fragment of a URL, which can carry credentials.
     *
     * @param url the URL
     * @return the URL without user info, query or fragment
     */
    static String sanitize(String url) {
        // Parsed by hand: java.net.URI falls back to a registry-based authority, with no user info, for hosts such as
        // my_service, and would then keep the credentials
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return "(hidden)";
        }
        int authorityStart = schemeEnd + 3;
        int authorityEnd = authorityStart;
        while (authorityEnd < url.length() && "/?#".indexOf(url.charAt(authorityEnd)) < 0) {
            authorityEnd++;
        }
        String authority = url.substring(authorityStart, authorityEnd);
        String host = authority.substring(authority.lastIndexOf('@') + 1);
        int pathEnd = authorityEnd;
        while (pathEnd < url.length() && "?#".indexOf(url.charAt(pathEnd)) < 0) {
            pathEnd++;
        }
        return url.substring(0, authorityStart) + host + url.substring(authorityEnd, pathEnd);
    }

    private static final class Builder {
        private final String id;
        private final List<String> declarativeTypes = new ArrayList<>();
        private @Nullable String annotationUrl;
        private final Set<String> annotationPaths = new TreeSet<>();
        private final List<ClientFilterResolutionContext> filterContexts = new ArrayList<>();
        private @Nullable ServiceHttpClientConfiguration configuration;
        private @Nullable ServiceInstanceList instanceList;

        private Builder(String id) {
            this.id = id;
        }

        private HttpClientInfo build(List<String> filters) {
            List<String> urls = new ArrayList<>();
            if (configuration != null) {
                configuration.getUrls().forEach(url -> urls.add(sanitize(url.toString())));
            }
            if (urls.isEmpty() && annotationUrl != null) {
                urls.add(annotationUrl);
            }
            String path = Optional.ofNullable(configuration).flatMap(ServiceHttpClientConfiguration::getPath)
                .orElse(annotationPaths.isEmpty() ? null : String.join(", ", annotationPaths));
            return new HttpClientInfo(
                id,
                declarativeTypes.stream().sorted().toList(),
                configuration != null,
                urls,
                path,
                configuration == null ? null : format(configuration.getConnectTimeout().orElse(null)),
                configuration == null ? null : format(configuration.getReadTimeout().orElse(null)),
                configuration != null && configuration.isHealthCheck(),
                instances(),
                filters
            );
        }

        private @Nullable List<HttpClientInfo.Instance> instances() {
            if (instanceList == null) {
                return null;
            }
            try {
                return instanceList.getInstances().stream()
                    .map(instance -> new HttpClientInfo.Instance(sanitize(instance.getURI().toString()), health(instance)))
                    .sorted(Comparator.comparing(HttpClientInfo.Instance::uri))
                    .toList();
            } catch (RuntimeException e) {
                LOG.debug("Unable to read the instances of HTTP client '{}'", id, e);
                return List.of();
            }
        }

        private static String health(ServiceInstance instance) {
            return String.valueOf(instance.getHealthStatus().getName());
        }

        private static @Nullable String format(@Nullable Duration duration) {
            if (duration == null) {
                return null;
            }
            return duration.toMillis() % 1000 == 0 ? duration.toSeconds() + "s" : duration.toMillis() + "ms";
        }
    }
}
