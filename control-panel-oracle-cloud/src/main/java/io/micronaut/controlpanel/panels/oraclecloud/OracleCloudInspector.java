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
package io.micronaut.controlpanel.panels.oraclecloud;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.RegionProvider;
import com.oracle.bmc.http.internal.BaseAsyncClient;
import com.oracle.bmc.http.internal.BaseSyncClient;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.oraclecloud.core.OracleCloudClientConfigurationProperties;
import io.micronaut.oraclecloud.core.OracleCloudConfigFileConfigurationProperties;
import io.micronaut.oraclecloud.core.ServiceOracleCloudClientConfigurationProperties;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads how the application connects to Oracle Cloud.
 *
 * <p>It only reads bean definitions and beans that already exist: it never creates an authentication provider, which
 * can call the instance metadata service, or an SDK client. It does not send requests to Oracle Cloud, and it does not
 * show keys, passphrases, fingerprints, OCIDs or the path of the OCI configuration file.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
public class OracleCloudInspector {

    static final String NETTY_PROVIDER = "io.micronaut.oraclecloud.httpclient.netty.ManagedNettyHttpProvider";

    private static final Logger LOG = LoggerFactory.getLogger(OracleCloudInspector.class);

    private static final Map<String, String> AUTHENTICATION_LABELS = Map.of(
        "ConfigFileAuthenticationDetailsProvider", "OCI configuration file",
        "SessionTokenAuthenticationDetailsProvider", "Session token from the OCI configuration file",
        "SimpleAuthenticationDetailsProvider", "API key in the application configuration",
        "InstancePrincipalsAuthenticationDetailsProvider", "Instance principal",
        "ResourcePrincipalAuthenticationDetailsProvider", "Resource principal",
        "OkeWorkloadIdentityAuthenticationDetailsProvider", "OKE workload identity"
    );

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public OracleCloudInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return how the application connects to Oracle Cloud
     */
    public OracleCloudInfo inspect() {
        Set<Class<?>> createdAuthentication = created(AbstractAuthenticationDetailsProvider.class).stream()
            .map(registration -> registration.getBeanDefinition().getBeanType())
            .collect(Collectors.toSet());
        List<OracleCloudInfo.Authentication> candidates = beanContext.getBeanDefinitions(AbstractAuthenticationDetailsProvider.class).stream()
            .map(definition -> authentication(definition, createdAuthentication))
            .sorted(Comparator.comparing(OracleCloudInfo.Authentication::label))
            .toList();
        OracleCloudInfo.Authentication authentication = selected(createdAuthentication);
        Optional<OracleCloudConfigFileConfigurationProperties> configFile = configFile(authentication);
        return new OracleCloudInfo(
            authentication,
            candidates.size() > 1 ? candidates : List.of(),
            configFile.map(OracleCloudConfigFileConfigurationProperties::profile).orElse(null),
            configFile.map(OracleCloudConfigFileConfigurationProperties::path).isPresent(),
            region(),
            beanContext.getBeanDefinitions(Object.class).stream().anyMatch(definition -> definition.getBeanType().getName().equals(NETTY_PROVIDER))
                ? "Micronaut HTTP client (Netty)"
                : "OCI SDK default",
            clients(),
            clientConfigurations()
        );
    }

    private OracleCloudInfo.@Nullable Authentication selected(Set<Class<?>> created) {
        try {
            return beanContext.findBeanDefinition(AbstractAuthenticationDetailsProvider.class)
                .map(definition -> authentication(definition, created))
                .orElse(null);
        } catch (RuntimeException e) {
            // More than one provider and none of them is primary: the clients fail to start
            LOG.debug("Unable to resolve the Oracle Cloud authentication provider", e);
            return null;
        }
    }

    private static OracleCloudInfo.Authentication authentication(BeanDefinition<?> definition, Set<Class<?>> created) {
        Class<?> type = definition.getBeanType();
        String label = AUTHENTICATION_LABELS.getOrDefault(type.getSimpleName(), type.getSimpleName());
        return new OracleCloudInfo.Authentication(label, type.getName(), created.contains(type));
    }

    private Optional<OracleCloudConfigFileConfigurationProperties> configFile(OracleCloudInfo.@Nullable Authentication authentication) {
        if (authentication == null || !authentication.label().contains("configuration file")) {
            return Optional.empty();
        }
        try {
            return beanContext.findBean(OracleCloudConfigFileConfigurationProperties.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the OCI configuration file settings", e);
            return Optional.empty();
        }
    }

    private @Nullable String region() {
        for (BeanRegistration<?> registration : created(AbstractAuthenticationDetailsProvider.class)) {
            if (registration.getBean() instanceof RegionProvider provider) {
                try {
                    if (provider.getRegion() != null) {
                        return provider.getRegion().getRegionId();
                    }
                } catch (RuntimeException e) {
                    LOG.debug("Unable to read the Oracle Cloud region", e);
                }
            }
        }
        return beanContext.findBean(Environment.class).flatMap(environment -> environment.getProperty("oci.region", String.class)).orElse(null);
    }

    private List<OracleCloudInfo.Client> clients() {
        List<OracleCloudInfo.Client> result = new ArrayList<>();
        // The common base class of the SDK clients is not public, so synchronous and asynchronous clients are read apart
        for (Class<?> base : List.of(BaseSyncClient.class, BaseAsyncClient.class)) {
            Map<Class<?>, Object> created = new HashMap<>();
            for (BeanRegistration<?> registration : created(base)) {
                created.put(registration.getBeanDefinition().getBeanType(), registration.getBean());
            }
            for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(base)) {
                Class<?> type = definition.getBeanType();
                String name = type.getSimpleName();
                boolean async = base == BaseAsyncClient.class;
                String suffix = async ? "AsyncClient" : "Client";
                String service = name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
                Object client = created.get(type);
                result.add(new OracleCloudInfo.Client(service, type.getName(), async, client != null, client == null ? null : endpoint(client)));
            }
        }
        result.sort(Comparator.comparing(OracleCloudInfo.Client::service).thenComparing(OracleCloudInfo.Client::async));
        return result;
    }

    private static @Nullable String endpoint(Object client) {
        try {
            String endpoint = null;
            if (client instanceof BaseSyncClient sync) {
                endpoint = sync.getEndpoint();
            } else if (client instanceof BaseAsyncClient async) {
                endpoint = async.getEndpoint();
            }
            return endpoint == null ? null : sanitize(endpoint);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Removes the user info, query and fragment of a URL.
     *
     * @param url the URL
     * @return the URL without user info, query or fragment
     */
    static String sanitize(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null) {
                return "(hidden)";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            return "(hidden)";
        }
    }

    private List<OracleCloudInfo.ClientConfiguration> clientConfigurations() {
        List<OracleCloudInfo.ClientConfiguration> result = new ArrayList<>();
        try {
            beanContext.findBean(OracleCloudClientConfigurationProperties.class)
                .map(configuration -> clientConfiguration("oci.client", configuration))
                .ifPresent(result::add);
            beanContext.getBeansOfType(ServiceOracleCloudClientConfigurationProperties.class).stream()
                .map(configuration -> clientConfiguration("oci.clients." + configuration.getName(), configuration))
                .sorted(Comparator.comparing(OracleCloudInfo.ClientConfiguration::name))
                .forEach(result::add);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the Oracle Cloud client configuration", e);
        }
        return result;
    }

    private static OracleCloudInfo.ClientConfiguration clientConfiguration(String name, HttpClientConfiguration configuration) {
        return new OracleCloudInfo.ClientConfiguration(
            name,
            format(configuration.getConnectTimeout().orElse(null)),
            format(configuration.getReadTimeout().orElse(null))
        );
    }

    private Collection<BeanRegistration<?>> created(Class<?> type) {
        try {
            return List.copyOf(beanContext.getActiveBeanRegistrations(Qualifiers.byType(type)));
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the created {} beans", type.getSimpleName(), e);
            return List.of();
        }
    }

    private static @Nullable String format(@Nullable Duration duration) {
        if (duration == null) {
            return null;
        }
        return duration.toMillis() % 1000 == 0 ? duration.toSeconds() + "s" : duration.toMillis() + "ms";
    }
}
