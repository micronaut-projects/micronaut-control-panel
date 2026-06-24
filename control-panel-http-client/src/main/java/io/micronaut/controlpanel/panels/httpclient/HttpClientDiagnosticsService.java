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
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import static io.micronaut.controlpanel.panels.httpclient.HttpClientDiagnostics.ConfigurationInfo;
import static io.micronaut.controlpanel.panels.httpclient.HttpClientDiagnostics.DiscoveryInfo;
import static io.micronaut.controlpanel.panels.httpclient.HttpClientDiagnostics.FilterInfo;
import static io.micronaut.controlpanel.panels.httpclient.HttpClientDiagnostics.HttpClientInfo;
import static io.micronaut.controlpanel.panels.httpclient.HttpClientDiagnostics.ServiceInstanceInfo;
import static io.micronaut.controlpanel.panels.httpclient.HttpClientDiagnostics.TargetInfo;

/**
 * Collects read-only diagnostics for Micronaut HTTP clients from bean definitions and named client configuration.
 */
@Singleton
public class HttpClientDiagnosticsService {

    private static final String NOT_CONFIGURED = "Not configured";
    private static final String MASKED = "Masked";
    private static final Set<String> LOCAL_TARGETS = Set.of("/", "");

    private final BeanContext beanContext;
    private final BeanProvider<DiscoveryClient> discoveryClientProvider;
    private final BeanProvider<HttpClientFilterResolver<ClientFilterResolutionContext>> filterResolverProvider;

    public HttpClientDiagnosticsService(BeanContext beanContext,
                                        BeanProvider<DiscoveryClient> discoveryClientProvider,
                                        BeanProvider<HttpClientFilterResolver<ClientFilterResolutionContext>> filterResolverProvider) {
        this.beanContext = beanContext;
        this.discoveryClientProvider = discoveryClientProvider;
        this.filterResolverProvider = filterResolverProvider;
    }

    /**
     * Collect diagnostics without issuing HTTP test requests or mutating application state.
     *
     * @return diagnostics
     */
    public HttpClientDiagnostics collect() {
        Map<String, ClientBuilder> builders = new LinkedHashMap<>();
        collectClientBeanDefinitions(builders);
        collectServiceConfigurations(builders);
        boolean discoveryAvailable = discoveryClientProvider.isResolvable();
        List<ServiceInstanceList> serviceInstanceLists = beanContext.getBeansOfType(ServiceInstanceList.class).stream()
            .sorted(Comparator.comparing(ServiceInstanceList::getID))
            .toList();

        List<HttpClientInfo> clients = builders.values().stream()
            .map(builder -> builder.toInfo(discoveryAvailable, serviceInstanceLists, resolveFilters(builder.id)))
            .sorted(Comparator.comparing(HttpClientInfo::id))
            .toList();
        return HttpClientDiagnostics.of(clients, discoveryAvailable);
    }

    private void collectClientBeanDefinitions(Map<String, ClientBuilder> builders) {
        for (BeanDefinition<Object> definition : beanContext.getAllBeanDefinitions()) {
            Optional<AnnotationValue<Client>> client = definition.findAnnotation(Client.class);
            if (client.isEmpty()) {
                continue;
            }
            String id = clientId(client.get(), definition);
            ClientBuilder builder = builders.computeIfAbsent(id, ClientBuilder::new);
            builder.beanType = definition.getBeanType().getName();
            builder.source = mergeSource(builder.source, "Declarative @Client");
            builder.annotationTarget = client.get().stringValue().orElse(StringUtils.EMPTY_STRING);
            builder.annotationPath = client.get().stringValue("path").orElse(StringUtils.EMPTY_STRING);
        }
    }

    private void collectServiceConfigurations(Map<String, ClientBuilder> builders) {
        for (BeanDefinition<ServiceHttpClientConfiguration> definition : beanContext.getBeanDefinitions(ServiceHttpClientConfiguration.class)) {
            ServiceHttpClientConfiguration configuration = beanContext.getBean(definition);
            String id = configuration.getServiceId();
            if (id == null || id.isBlank()) {
                id = definition.getName();
            }
            ClientBuilder builder = builders.computeIfAbsent(id, ClientBuilder::new);
            builder.source = mergeSource(builder.source, "Named configuration");
            builder.configuration = configuration;
        }
    }

    private List<FilterInfo> resolveFilters(String clientId) {
        if (!filterResolverProvider.isResolvable()) {
            return List.of();
        }
        ClientFilterResolutionContext context = new ClientFilterResolutionContext(List.of(clientId), AnnotationMetadata.EMPTY_METADATA);
        try {
            return filterResolverProvider.get().resolveFilterEntries(context).stream()
                .map(HttpClientDiagnosticsService::toFilterInfo)
                .sorted(Comparator.comparing(FilterInfo::type).thenComparing(FilterInfo::order))
                .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static FilterInfo toFilterInfo(HttpFilterResolver.FilterEntry entry) {
        GenericHttpFilter filter = entry.getFilter();
        String order = filter instanceof Ordered ordered ? String.valueOf(ordered.getOrder()) : NOT_CONFIGURED;
        String patterns = entry.hasPatterns() ? String.join(", ", entry.getPatterns()) : "All paths";
        String methods = entry.hasMethods() ? joinMethods(entry.getFilterMethods()) : "All methods";
        return new FilterInfo(filter.getClass().getName(), order, patterns, methods);
    }

    private static String joinMethods(Collection<HttpMethod> methods) {
        return methods.stream()
            .map(HttpMethod::name)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("All methods");
    }

    private static String clientId(AnnotationValue<Client> client, BeanDefinition<?> definition) {
        String id = client.stringValue("id").orElse(StringUtils.EMPTY_STRING);
        if (id.isBlank()) {
            id = client.stringValue().orElse(StringUtils.EMPTY_STRING);
        }
        if (id.isBlank()) {
            id = definition.getName();
        }
        return NameUtils.hyphenate(id.replace("${", StringUtils.EMPTY_STRING).replace("}", StringUtils.EMPTY_STRING));
    }

    private static String mergeSource(String existing, String source) {
        if (existing == null || existing.isBlank()) {
            return source;
        }
        if (existing.contains(source)) {
            return existing;
        }
        return existing + ", " + source;
    }

    private static String sanitizeTarget(String target) {
        if (target == null || target.isBlank()) {
            return NOT_CONFIGURED;
        }
        try {
            URI uri = new URI(target);
            if (uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null) {
                return target;
            }
            String authority = uri.getHost();
            if (uri.getPort() > -1) {
                authority += ":" + uri.getPort();
            }
            URI sanitized = new URI(
                uri.getScheme(),
                uri.getUserInfo() == null ? null : MASKED,
                authority,
                uri.getPort(),
                uri.getPath(),
                uri.getQuery() == null ? null : MASKED,
                uri.getFragment() == null ? null : MASKED
            );
            return sanitized.toString();
        } catch (URISyntaxException e) {
            return target
                .replaceAll("//[^/@]+@", "//" + MASKED + "@")
                .replaceAll("\\?[^#]*", "?" + MASKED)
                .replaceAll("#.*", "#" + MASKED);
        }
    }

    private static String duration(Optional<Duration> duration) {
        return duration.map(Duration::toString).orElse(NOT_CONFIGURED);
    }

    private static String duration(@Nullable Duration duration) {
        return duration == null ? NOT_CONFIGURED : duration.toString();
    }

    private static String optionalString(Optional<?> value) {
        return value.map(Object::toString).orElse(NOT_CONFIGURED);
    }

    private static String socketAddress(Optional<SocketAddress> value) {
        return value.map(Object::toString).orElse(NOT_CONFIGURED);
    }

    private static String tlsSummary(SslConfiguration sslConfiguration) {
        if (sslConfiguration == null) {
            return NOT_CONFIGURED;
        }
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add("enabled=" + sslConfiguration.isEnabled());
        sslConfiguration.getClientAuthentication().ifPresent(auth -> joiner.add("client-auth=" + auth));
        sslConfiguration.getProtocol().ifPresent(protocol -> joiner.add("protocol=" + protocol));
        if (sslConfiguration.getKey().getPassword().isPresent()
            || sslConfiguration.getKeyStore().getPassword().isPresent()
            || sslConfiguration.getKeyStore().getPath().isPresent()
            || sslConfiguration.getTrustStore().getPassword().isPresent()
            || sslConfiguration.getTrustStore().getPath().isPresent()) {
            joiner.add("key/trust material=" + MASKED.toLowerCase(Locale.ROOT));
        }
        return joiner.toString();
    }

    private static String connectionPool(HttpClientConfiguration.ConnectionPoolConfiguration pool) {
        if (pool == null) {
            return NOT_CONFIGURED;
        }
        return "enabled=" + pool.isEnabled()
            + ", version=" + pool.getVersion()
            + ", max-pending-acquires=" + pool.getMaxPendingAcquires()
            + ", max-http1=" + pool.getMaxConcurrentHttp1Connections()
            + ", max-http2=" + pool.getMaxConcurrentHttp2Connections();
    }

    private static String alpnModes(List<String> modes) {
        return modes == null || modes.isEmpty() ? NOT_CONFIGURED : String.join(", ", modes);
    }

    private static final class ClientBuilder {
        private final String id;
        private String beanType = NOT_CONFIGURED;
        private String source = "Unknown";
        private String annotationTarget = StringUtils.EMPTY_STRING;
        private String annotationPath = StringUtils.EMPTY_STRING;
        @Nullable
        private ServiceHttpClientConfiguration configuration;

        private ClientBuilder(String id) {
            this.id = id;
        }

        private HttpClientInfo toInfo(boolean discoveryAvailable, List<ServiceInstanceList> serviceInstanceLists, List<FilterInfo> filters) {
            TargetInfo target = targetInfo();
            return new HttpClientInfo(
                id,
                beanType,
                source,
                target,
                configurationInfo(),
                discoveryInfo(target, discoveryAvailable, serviceInstanceLists),
                filters
            );
        }

        private TargetInfo targetInfo() {
            List<URI> urls = configuration == null ? List.of() : configuration.getUrls();
            if (!urls.isEmpty()) {
                String joined = urls.stream().map(URI::toString).map(HttpClientDiagnosticsService::sanitizeTarget).reduce((left, right) -> left + ", " + right).orElse(NOT_CONFIGURED);
                return new TargetInfo("Fixed URL", joined, "badge-success");
            }
            if (annotationTarget != null && !annotationTarget.isBlank() && !LOCAL_TARGETS.contains(annotationTarget)) {
                if (annotationTarget.startsWith("http://") || annotationTarget.startsWith("https://")) {
                    return new TargetInfo("Fixed URL", sanitizeTarget(annotationTarget), "badge-success");
                }
                return new TargetInfo("Service ID", annotationTarget, "badge-warning");
            }
            if (configuration != null) {
                return new TargetInfo("Service ID", configuration.getServiceId(), "badge-warning");
            }
            if (annotationTarget != null && LOCAL_TARGETS.contains(annotationTarget)) {
                return new TargetInfo("Local", annotationTarget.isBlank() ? "/" : annotationTarget, "badge-secondary");
            }
            return new TargetInfo("Unknown", NOT_CONFIGURED, "badge-secondary");
        }

        private ConfigurationInfo configurationInfo() {
            HttpClientConfiguration cfg = configuration;
            String path = configuration == null ? annotationPath : configuration.getPath().orElse(annotationPath);
            if (path == null || path.isBlank()) {
                path = NOT_CONFIGURED;
            }
            return new ConfigurationInfo(
                path,
                cfg == null ? NOT_CONFIGURED : duration(cfg.getConnectTimeout()),
                cfg == null ? NOT_CONFIGURED : duration(cfg.getReadTimeout()),
                cfg == null ? NOT_CONFIGURED : duration(cfg.getRequestTimeout()),
                cfg == null ? NOT_CONFIGURED : String.valueOf(cfg.isFollowRedirects()),
                NOT_CONFIGURED,
                cfg == null ? NOT_CONFIGURED : String.valueOf(cfg.getHttpVersion()),
                cfg == null ? NOT_CONFIGURED : String.valueOf(cfg.getPlaintextMode()),
                cfg == null ? NOT_CONFIGURED : alpnModes(cfg.getAlpnModes()),
                cfg == null ? NOT_CONFIGURED : cfg.getProxyType() + " " + socketAddress(cfg.getProxyAddress()),
                cfg == null ? NOT_CONFIGURED : proxyCredentials(cfg),
                cfg == null ? NOT_CONFIGURED : tlsSummary(cfg.getSslConfiguration()),
                cfg == null ? NOT_CONFIGURED : connectionPool(cfg.getConnectionPoolConfiguration())
            );
        }

        private static String proxyCredentials(HttpClientConfiguration cfg) {
            if (cfg.getProxyUsername().isPresent() || cfg.getProxyPassword().isPresent()) {
                return MASKED;
            }
            return NOT_CONFIGURED;
        }

        private DiscoveryInfo discoveryInfo(TargetInfo target, boolean discoveryAvailable, List<ServiceInstanceList> serviceInstanceLists) {
            if (!Objects.equals(target.kind(), "Service ID")) {
                return new DiscoveryInfo(false, "Fixed or local targets do not use service discovery.", List.of());
            }
            if (!discoveryAvailable && serviceInstanceLists.isEmpty()) {
                return new DiscoveryInfo(false, "No discovery client or service instance list is available in this application context.", List.of());
            }
            Optional<ServiceInstanceList> matchingList = serviceInstanceLists.stream()
                .filter(list -> Objects.equals(list.getID(), id) || Objects.equals(list.getID(), target.value()))
                .findFirst();
            if (matchingList.isEmpty()) {
                return new DiscoveryInfo(false, "No application-visible service instances were reported for this service id.", List.of());
            }
            List<ServiceInstanceInfo> instances = matchingList.get().getInstances().stream()
                .map(ClientBuilder::toServiceInstanceInfo)
                .sorted(Comparator.comparing(ServiceInstanceInfo::uri))
                .toList();
            if (instances.isEmpty()) {
                return new DiscoveryInfo(false, "A service instance list exists, but it currently reports no instances.", List.of());
            }
            return new DiscoveryInfo(true, "Instances are read from Micronaut service-instance lists visible to this application.", instances);
        }

        private static ServiceInstanceInfo toServiceInstanceInfo(ServiceInstance instance) {
            return new ServiceInstanceInfo(
                optionalString(instance.getInstanceId()).equals(NOT_CONFIGURED) ? instance.getId() : optionalString(instance.getInstanceId()),
                sanitizeTarget(instance.getURI().toString()),
                String.valueOf(instance.getHealthStatus()),
                optionalString(instance.getZone()),
                optionalString(instance.getRegion())
            );
        }
    }
}
