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

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only diagnostics for OCI SDK clients and Oracle Cloud authentication/configuration state.
 */
@Singleton
@Requires(classes = AuthenticationDetailsProvider.class)
@Requires(property = OciSdkClientsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class OciSdkClientsControlPanel extends AbstractControlPanel<OciSdkClientsControlPanel.Body> {

    public static final String NAME = "oci-sdk-clients";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("oracle-cloud", "Oracle Cloud", "si si-oracle");

    private static final String OCI_CLIENT_PREFIX = "oci.client.";
    private static final String OCI_CLIENTS_PREFIX = "oci.clients.";
    private static final String MICRONAUT_HTTP_SERVICES_OCI_PREFIX = "micronaut.http.services.oci.";
    private static final String OKE_WORKLOAD_IDENTITY_PREFIX = "oci.config.oke-workload-identity.";
    private static final String METRICS_EXPORT_ORACLECLOUD_PREFIX = "micronaut.metrics.export.oraclecloud.";
    private static final String LOGBACK_ORACLE_CLOUD_PREFIX = "logback.appenders.oracle-cloud.";
    private static final String OCI_NETTY_LEGACY = "oci.netty.legacy-netty-client";
    private static final String OCI_NETTY_MANAGED = "oci.netty.use-managed-provider-globally";
    private static final String NETTY_CONFIGURATION_CLASS = "io.micronaut.oraclecloud.httpclient.netty.OciNettyConfiguration";
    private static final String NOTICE_INFO = "info";
    private static final String NOTICE_WARNING = "warning";
    private static final List<String> HIDDEN_FIELDS = List.of(
        "private keys",
        "passphrases",
        "fingerprints",
        "session and security tokens",
        "resource principal token material",
        "user and tenancy OCIDs",
        "raw config-file contents",
        "config paths and profile values",
        "Vault secret values",
        "proxy credentials",
        "key/trust store paths and passwords"
    );

    private final BeanContext beanContext;
    private final Environment environment;

    public OciSdkClientsControlPanel(BeanContext beanContext,
                                     Environment environment,
                                     @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanContext = beanContext;
        this.environment = environment;
    }

    @Override
    public Body getBody() {
        Set<String> propertyNames = collectPropertyNames();
        AuthenticationInfo authentication = resolveAuthentication(propertyNames);
        List<ClientInfo> clients = resolveClients(propertyNames, authentication.providerType());
        HttpConfigurationInfo http = resolveHttpConfiguration(propertyNames);
        List<ServiceConfigurationInfo> serviceConfigurations = resolveServiceConfigurations(propertyNames);
        NettyInfo netty = resolveNettyInfo(propertyNames);
        List<IntegrationInfo> integrations = resolveIntegrations(propertyNames);
        List<DiagnosticNotice> notices = resolveNotices(clients, propertyNames, http);
        Summary summary = new Summary(clients.size(), clients.stream().anyMatch(ClientInfo::definitionOnly), authentication.providerType(), true, true, notices.size());
        return new Body(summary, clients, authentication, http, serviceConfigurations, netty, integrations, notices);
    }

    @Override
    public String getBadge() {
        return String.valueOf(countOciClientBeans());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getDetailLinkName() {
        return "Open diagnostics";
    }

    private List<ClientInfo> resolveClients(Set<String> propertyNames, String authProviderType) {
        Map<String, ClientInfoBuilder> clients = new LinkedHashMap<>();
        Map<String, Set<String>> variantsByService = new LinkedHashMap<>();

        Collection<BeanRegistration<?>> activeRegistrations = beanContext.getActiveBeanRegistrations(Qualifiers.any());
        for (BeanRegistration<?> registration : activeRegistrations) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            Class<?> beanType = registration.getBeanType();
            if (isOciClientType(beanType)) {
                ClientInfoBuilder builder = clientInfo(definition, beanType, registration.getIdentifier().toString(), false, propertyNames, authProviderType);
                clients.put(builder.key(), builder);
                variantsByService.computeIfAbsent(builder.serviceId(), ignored -> new TreeSet<>()).add(builder.clientKind());
            }
        }

        for (BeanDefinition<?> definition : beanContext.getAllBeanDefinitions()) {
            Class<?> beanType = definition.getBeanType();
            if (isOciClientType(beanType)) {
                ClientInfoBuilder builder = clientInfo(definition, beanType, definition.getName(), true, propertyNames, authProviderType);
                variantsByService.computeIfAbsent(builder.serviceId(), ignored -> new TreeSet<>()).add(builder.clientKind());
                clients.putIfAbsent(builder.key(), builder);
            }
        }

        return clients.values()
            .stream()
            .map(builder -> builder.withReactiveVariants(variantsByService.getOrDefault(builder.serviceId(), Set.of())
                .stream()
                .filter(variant -> !variant.equals(builder.clientKind()))
                .toList()))
            .map(ClientInfoBuilder::build)
            .sorted(Comparator.comparing(ClientInfo::serviceId).thenComparing(ClientInfo::clientClass))
            .toList();
    }

    private ClientInfoBuilder clientInfo(BeanDefinition<?> definition,
                                         Class<?> beanType,
                                         String fallbackName,
                                         boolean definitionOnly,
                                         Set<String> propertyNames,
                                         String authProviderType) {
        Optional<String> qualifier = findQualifierName(definition);
        String beanName = qualifier.orElse(fallbackName);
        String serviceId = inferServiceId(beanType);
        String clientKind = inferClientKind(beanType);
        return new ClientInfoBuilder(
            beanName,
            qualifier.orElse(""),
            beanType.getName(),
            serviceId,
            clientKind,
            List.of(clientKind),
            authProviderType,
            hasProperty(propertyNames, "oci.region") || hasProperty(propertyNames, OCI_CLIENTS_PREFIX + serviceId + ".region"),
            hasProperty(propertyNames, "oci.endpoint") || hasProperty(propertyNames, OCI_CLIENT_PREFIX + "endpoint") || hasProperty(propertyNames, OCI_CLIENTS_PREFIX + serviceId + ".endpoint"),
            hasPrefix(propertyNames, OCI_CLIENTS_PREFIX + serviceId + "."),
            definitionOnly,
            definitionOnly ? List.of("Bean definition detected; client has not been instantiated yet.") : List.of()
        );
    }

    private int countOciClientBeans() {
        Set<String> clientKeys = new TreeSet<>();
        Collection<BeanRegistration<?>> activeRegistrations = beanContext.getActiveBeanRegistrations(Qualifiers.any());
        for (BeanRegistration<?> registration : activeRegistrations) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            Class<?> beanType = registration.getBeanType();
            if (isOciClientType(beanType)) {
                clientKeys.add(clientKey(definition, beanType));
            }
        }
        for (BeanDefinition<?> definition : beanContext.getAllBeanDefinitions()) {
            Class<?> beanType = definition.getBeanType();
            if (isOciClientType(beanType)) {
                clientKeys.add(clientKey(definition, beanType));
            }
        }
        return clientKeys.size();
    }

    private static String clientKey(BeanDefinition<?> definition, Class<?> beanType) {
        String qualifier = findQualifierName(definition).orElse("");
        return clientKey(qualifier, beanType.getName());
    }

    private static Optional<String> findQualifierName(BeanDefinition<?> definition) {
        Qualifier<?> qualifier = definition.getDeclaredQualifier();
        return qualifier == null ? Optional.empty() : Optional.ofNullable(Qualifiers.findName(qualifier));
    }

    private static String clientKey(String qualifier, String clientClass) {
        return (qualifier.isEmpty() ? clientClass : qualifier) + ":" + clientClass;
    }

    private AuthenticationInfo resolveAuthentication(Set<String> propertyNames) {
        Optional<BeanRegistration<AuthenticationDetailsProvider>> authProvider = beanContext.getActiveBeanRegistrations(AuthenticationDetailsProvider.class)
            .stream()
            .findFirst();
        if (authProvider.isPresent()) {
            BeanRegistration<AuthenticationDetailsProvider> registration = authProvider.get();
            Class<?> providerType = registration.getBean().getClass();
            return new AuthenticationInfo(classifyAuthenticationProvider(providerType), providerType.getName(), registration.getIdentifier().toString(), sourceSignals(propertyNames), HIDDEN_FIELDS);
        }

        Optional<BeanRegistration<BasicAuthenticationDetailsProvider>> basicProvider = beanContext.getActiveBeanRegistrations(BasicAuthenticationDetailsProvider.class)
            .stream()
            .findFirst();
        if (basicProvider.isPresent()) {
            BeanRegistration<BasicAuthenticationDetailsProvider> registration = basicProvider.get();
            Class<?> providerType = registration.getBean().getClass();
            return new AuthenticationInfo(classifyAuthenticationProvider(providerType), providerType.getName(), registration.getIdentifier().toString(), sourceSignals(propertyNames), HIDDEN_FIELDS);
        }

        return new AuthenticationInfo("not detected", "", "", sourceSignals(propertyNames), HIDDEN_FIELDS);
    }

    private HttpConfigurationInfo resolveHttpConfiguration(Set<String> propertyNames) {
        boolean globalOciClientConfigured = hasPrefix(propertyNames, OCI_CLIENT_PREFIX);
        boolean micronautHttpServicesOciConfigured = hasPrefix(propertyNames, MICRONAUT_HTTP_SERVICES_OCI_PREFIX);
        boolean conflict = globalOciClientConfigured && micronautHttpServicesOciConfigured;
        List<String> categories = propertyNames.stream()
            .filter(name -> name.startsWith(OCI_CLIENT_PREFIX) || name.startsWith(MICRONAUT_HTTP_SERVICES_OCI_PREFIX))
            .map(OciDiagnosticsRedactor::safePropertyCategory)
            .distinct()
            .sorted()
            .toList();
        return new HttpConfigurationInfo(globalOciClientConfigured, micronautHttpServicesOciConfigured, conflict, categories);
    }

    private List<ServiceConfigurationInfo> resolveServiceConfigurations(Set<String> propertyNames) {
        Map<String, Set<String>> categoriesByService = new LinkedHashMap<>();
        for (String propertyName : propertyNames) {
            if (propertyName.startsWith(OCI_CLIENTS_PREFIX)) {
                String remainder = propertyName.substring(OCI_CLIENTS_PREFIX.length());
                int dot = remainder.indexOf('.');
                if (dot > 0) {
                    String serviceId = remainder.substring(0, dot);
                    categoriesByService.computeIfAbsent(serviceId, ignored -> new TreeSet<>()).add(OciDiagnosticsRedactor.safePropertyCategory(propertyName));
                }
            }
        }
        return categoriesByService.entrySet()
            .stream()
            .map(entry -> {
                Set<String> categories = entry.getValue();
                return new ServiceConfigurationInfo(
                    entry.getKey(),
                    true,
                    categories.contains("timeout"),
                    categories.contains("retry"),
                    categories.contains("proxy"),
                    categories.contains("ssl"),
                    categories.contains("logging"),
                    List.copyOf(categories)
                );
            })
            .sorted(Comparator.comparing(ServiceConfigurationInfo::serviceId))
            .toList();
    }

    private NettyInfo resolveNettyInfo(Set<String> propertyNames) {
        boolean managedConfigured = hasProperty(propertyNames, OCI_NETTY_MANAGED);
        boolean legacyConfigured = hasProperty(propertyNames, OCI_NETTY_LEGACY);
        return new NettyInfo(
            managedConfigured,
            environment.getProperty(OCI_NETTY_MANAGED, Boolean.class).orElse(false),
            legacyConfigured,
            environment.getProperty(OCI_NETTY_LEGACY, Boolean.class).orElse(false),
            ClassUtils.isPresent(NETTY_CONFIGURATION_CLASS, OciSdkClientsControlPanel.class.getClassLoader())
        );
    }

    private List<IntegrationInfo> resolveIntegrations(Set<String> propertyNames) {
        return List.of(
            new IntegrationInfo("Vault config import", hasBeanType("io.micronaut.oraclecloud.vault") || hasPrefix(propertyNames, "oci.vault."), hasPrefix(propertyNames, "oci.vault."), true),
            new IntegrationInfo("Oracle Cloud Micrometer", hasBeanType("io.micronaut.oraclecloud.micrometer") || hasPrefix(propertyNames, METRICS_EXPORT_ORACLECLOUD_PREFIX), hasPrefix(propertyNames, METRICS_EXPORT_ORACLECLOUD_PREFIX), true),
            new IntegrationInfo("OCI Logging appender", hasBeanType("io.micronaut.oraclecloud.logging") || hasPrefix(propertyNames, LOGBACK_ORACLE_CLOUD_PREFIX), hasPrefix(propertyNames, LOGBACK_ORACLE_CLOUD_PREFIX), true),
            new IntegrationInfo("Certificate refresh", hasBeanType("io.micronaut.oraclecloud.certificates") || hasPrefix(propertyNames, "oci.certificates."), hasPrefix(propertyNames, "oci.certificates."), true),
            new IntegrationInfo("OKE Kubernetes client", hasBeanType("io.micronaut.oraclecloud.oke") || hasPrefix(propertyNames, OKE_WORKLOAD_IDENTITY_PREFIX), hasPrefix(propertyNames, OKE_WORKLOAD_IDENTITY_PREFIX), true)
        );
    }

    private List<DiagnosticNotice> resolveNotices(List<ClientInfo> clients, Set<String> propertyNames, HttpConfigurationInfo http) {
        List<DiagnosticNotice> notices = new ArrayList<>();
        notices.add(new DiagnosticNotice(NOTICE_INFO, "no-network", "No OCI network calls or credential accessors were invoked."));
        notices.add(new DiagnosticNotice(NOTICE_INFO, "redacted", "Credential and account-identifying values are hidden by design."));
        if (clients.isEmpty()) {
            notices.add(new DiagnosticNotice(NOTICE_WARNING, "empty", "No instantiated OCI SDK client beans or OCI SDK client bean definitions were detected."));
        } else if (clients.stream().anyMatch(ClientInfo::definitionOnly)) {
            notices.add(new DiagnosticNotice(NOTICE_WARNING, "partial", "Some OCI SDK client bean definitions exist but have not been instantiated yet."));
        }
        if (http.conflictWarning()) {
            notices.add(new DiagnosticNotice(NOTICE_WARNING, "http-config-conflict", "Both oci.client and micronaut.http.services.oci configuration are present; choose one HTTP configuration path."));
        }
        if (hasSensitiveConfiguredKey(propertyNames)) {
            notices.add(new DiagnosticNotice(NOTICE_INFO, "permission-hidden", "Sensitive OCI configuration keys are configured, but their values are hidden."));
        }
        return notices;
    }

    private List<String> sourceSignals(Set<String> propertyNames) {
        List<String> signals = new ArrayList<>();
        if (hasPrefix(propertyNames, "oci.config.instance-principal.")) {
            signals.add("instance principal configuration present");
        }
        if (hasPrefix(propertyNames, OKE_WORKLOAD_IDENTITY_PREFIX)) {
            signals.add("OKE workload identity configuration present");
        }
        if (hasAny(propertyNames, "oci.config.enabled", "oci.config.path", "oci.config.profile", "oci.config.session-token")) {
            signals.add("config-file/session configuration present");
        }
        if (hasProperty(propertyNames, "oci.region")) {
            signals.add("region configured");
        }
        if (hasAny(propertyNames, "oci.tenant-id", "oci.user-id", "oci.fingerprint", "oci.private-key", "oci.private-key-file")) {
            signals.add("simple authentication configuration present");
        }
        return signals;
    }

    private Set<String> collectPropertyNames() {
        Set<String> propertyNames = new TreeSet<>();
        for (PropertySource propertySource : environment.getPropertySources()) {
            for (String propertyName : propertySource) {
                propertyNames.add(propertyName.toLowerCase(Locale.ROOT));
            }
        }
        return propertyNames;
    }

    private boolean hasBeanType(String classNamePrefix) {
        return beanContext.getAllBeanDefinitions().stream().anyMatch(definition -> definition.getBeanType().getName().startsWith(classNamePrefix));
    }

    private static boolean hasProperty(Set<String> propertyNames, String propertyName) {
        return propertyNames.contains(propertyName);
    }

    private static boolean hasAny(Set<String> propertyNames, String... names) {
        for (String name : names) {
            if (hasProperty(propertyNames, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPrefix(Set<String> propertyNames, String prefix) {
        return propertyNames.stream().anyMatch(name -> name.startsWith(prefix));
    }

    private static boolean hasSensitiveConfiguredKey(Set<String> propertyNames) {
        return propertyNames.stream()
            .filter(OciSdkClientsControlPanel::isOracleCloudProperty)
            .anyMatch(OciDiagnosticsRedactor::isSensitiveKey);
    }

    private static boolean isOracleCloudProperty(String propertyName) {
        return propertyName.startsWith("oci.")
            || propertyName.startsWith(MICRONAUT_HTTP_SERVICES_OCI_PREFIX)
            || propertyName.startsWith(METRICS_EXPORT_ORACLECLOUD_PREFIX)
            || propertyName.startsWith(LOGBACK_ORACLE_CLOUD_PREFIX);
    }

    private static boolean isOciClientType(Class<?> beanType) {
        String className = beanType.getName();
        String packageName = beanType.getPackageName();
        String simpleName = beanType.getSimpleName();
        if (className.contains("$") || simpleName.endsWith("Builder") || simpleName.endsWith("Factory") || simpleName.endsWith("Configurator")) {
            return false;
        }
        if (packageName.contains(".auth") || packageName.contains(".http") || packageName.contains(".model") || packageName.contains(".requests") || packageName.contains(".responses") || packageName.contains(".waiters")) {
            return false;
        }
        return (packageName.startsWith("com.oracle.bmc.") && (simpleName.endsWith("Client") || simpleName.endsWith("AsyncClient")))
            || packageName.startsWith("io.micronaut.oraclecloud.clients.reactor.")
            || packageName.startsWith("io.micronaut.oraclecloud.clients.rxjava2.");
    }

    private static String inferClientKind(Class<?> beanType) {
        String className = beanType.getName();
        if (className.startsWith("io.micronaut.oraclecloud.clients.reactor.")) {
            return "reactor";
        }
        if (className.startsWith("io.micronaut.oraclecloud.clients.rxjava2.")) {
            return "rxjava2";
        }
        if (beanType.getSimpleName().endsWith("AsyncClient")) {
            return "async";
        }
        return "blocking";
    }

    private static String inferServiceId(Class<?> beanType) {
        String simpleName = beanType.getSimpleName();
        simpleName = simpleName.replaceFirst("AsyncClient$", "");
        simpleName = simpleName.replaceFirst("Client$", "");
        simpleName = simpleName.replaceFirst("^Reactor", "");
        simpleName = simpleName.replaceFirst("^Rx", "");
        simpleName = simpleName.replaceFirst("Reactor$", "");
        simpleName = simpleName.replaceFirst("RxJava2$", "");
        simpleName = simpleName.replaceFirst("Rx$", "");
        return NameUtils.hyphenate(simpleName).replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String classifyAuthenticationProvider(Class<?> providerType) {
        String simpleName = providerType.getSimpleName();
        if (simpleName.contains("ConfigFile")) {
            return "config file";
        }
        if (simpleName.contains("Simple")) {
            return "simple";
        }
        if (simpleName.contains("InstancePrincipal")) {
            return "instance principal";
        }
        if (simpleName.contains("ResourcePrincipal") || simpleName.contains("ResourcePrincipals")) {
            return "resource principal";
        }
        if (simpleName.contains("SessionToken")) {
            return "session token";
        }
        if (simpleName.contains("OkeWorkloadIdentity")) {
            return "OKE workload identity";
        }
        return "custom";
    }

    /**
     * Summary for card and detail rendering.
     *
     * @param clientCount count of detected OCI SDK clients
     * @param partial true when only bean definitions are available for some clients
     * @param authProviderType effective authentication provider type
     * @param noNetwork true when the diagnostics builder avoided OCI calls
     * @param redacted true when sensitive values are hidden
     * @param noticeCount number of diagnostic notices
     */
    @ReflectiveAccess
    public record Summary(int clientCount,
                          boolean partial,
                          String authProviderType,
                          boolean noNetwork,
                          boolean redacted,
                          int noticeCount) {
    }

    /**
     * OCI SDK client metadata safe for rendering.
     *
     * @param beanName bean name or identifier
     * @param qualifier qualifier name
     * @param clientClass client implementation class
     * @param serviceId inferred OCI service id
     * @param clientKind client kind
     * @param reactiveVariants other client variants detected for the service
     * @param authProviderType authentication provider type
     * @param regionConfigured whether region configuration is present
     * @param endpointConfigured whether endpoint configuration is present
     * @param serviceConfigurationPresent whether service-specific configuration is present
     * @param definitionOnly whether only the bean definition was detected
     * @param notes non-secret diagnostic notes
     */
    @ReflectiveAccess
    public record ClientInfo(String beanName,
                             String qualifier,
                             String clientClass,
                             String serviceId,
                             String clientKind,
                             List<String> reactiveVariants,
                             String authProviderType,
                             boolean regionConfigured,
                             boolean endpointConfigured,
                             boolean serviceConfigurationPresent,
                             boolean definitionOnly,
                             List<String> notes) {
    }

    /**
     * Authentication provider metadata safe for rendering.
     *
     * @param providerType provider type
     * @param providerClass provider class
     * @param beanName provider bean identifier
     * @param sourceSignals non-secret source signals
     * @param hiddenFields hidden field categories
     */
    @ReflectiveAccess
    public record AuthenticationInfo(String providerType,
                                     String providerClass,
                                     String beanName,
                                     List<String> sourceSignals,
                                     List<String> hiddenFields) {
    }

    /**
     * Global HTTP configuration signals.
     *
     * @param globalOciClientConfigured whether `oci.client` configuration is present
     * @param micronautHttpServicesOciConfigured whether `micronaut.http.services.oci` configuration is present
     * @param conflictWarning whether both global HTTP configuration paths are present
     * @param configuredCategories safe configuration categories
     */
    @ReflectiveAccess
    public record HttpConfigurationInfo(boolean globalOciClientConfigured,
                                        boolean micronautHttpServicesOciConfigured,
                                        boolean conflictWarning,
                                        List<String> configuredCategories) {
    }

    /**
     * Service-specific HTTP configuration signals.
     *
     * @param serviceId service id
     * @param configurationPresent whether service configuration is present
     * @param timeoutConfigured whether timeout configuration is present
     * @param retryConfigured whether retry configuration is present
     * @param proxyConfigured whether proxy configuration is present
     * @param sslConfigured whether SSL configuration is present
     * @param loggingConfigured whether logging configuration is present
     * @param configuredCategories safe configuration categories
     */
    @ReflectiveAccess
    public record ServiceConfigurationInfo(String serviceId,
                                           boolean configurationPresent,
                                           boolean timeoutConfigured,
                                           boolean retryConfigured,
                                           boolean proxyConfigured,
                                           boolean sslConfigured,
                                           boolean loggingConfigured,
                                           List<String> configuredCategories) {
    }

    /**
     * Managed Netty configuration signals.
     *
     * @param managedProviderGlobalConfigured whether the managed provider flag is configured
     * @param managedProviderGlobalEnabled whether the managed provider flag is enabled
     * @param legacyNettyConfigured whether the legacy Netty flag is configured
     * @param legacyNettyEnabled whether the legacy Netty flag is enabled
     * @param nettyProviderClassPresent whether the Netty provider configuration class is present
     */
    @ReflectiveAccess
    public record NettyInfo(boolean managedProviderGlobalConfigured,
                            boolean managedProviderGlobalEnabled,
                            boolean legacyNettyConfigured,
                            boolean legacyNettyEnabled,
                            boolean nettyProviderClassPresent) {
    }

    /**
     * Optional Oracle Cloud integration indicator.
     *
     * @param name integration name
     * @param present whether the integration is present
     * @param configured whether integration configuration is present
     * @param detailsHidden whether details are hidden
     */
    @ReflectiveAccess
    public record IntegrationInfo(String name,
                                  boolean present,
                                  boolean configured,
                                  boolean detailsHidden) {
    }

    /**
     * Non-secret diagnostics notice.
     *
     * @param level notice level
     * @param code notice code
     * @param message notice message
     */
    @ReflectiveAccess
    public record DiagnosticNotice(String level, String code, String message) {
    }

    /**
     * Body for the OCI SDK clients panel.
     *
     * @param summary panel summary
     * @param clients client rows
     * @param authentication authentication metadata
     * @param globalHttp global HTTP configuration metadata
     * @param serviceConfigurations service configuration rows
     * @param netty Netty configuration metadata
     * @param integrations integration rows
     * @param notices diagnostic notices
     */
    @ReflectiveAccess
    public record Body(Summary summary,
                       List<ClientInfo> clients,
                       AuthenticationInfo authentication,
                       HttpConfigurationInfo globalHttp,
                       List<ServiceConfigurationInfo> serviceConfigurations,
                       NettyInfo netty,
                       List<IntegrationInfo> integrations,
                       List<DiagnosticNotice> notices) {
    }

    private record ClientInfoBuilder(String beanName,
                                     String qualifier,
                                     String clientClass,
                                     String serviceId,
                                     String clientKind,
                                     List<String> reactiveVariants,
                                     String authProviderType,
                                     boolean regionConfigured,
                                     boolean endpointConfigured,
                                     boolean serviceConfigurationPresent,
                                     boolean definitionOnly,
                                     List<String> notes) {
        private String key() {
            return clientKey(qualifier, clientClass);
        }

        private ClientInfoBuilder withReactiveVariants(List<String> reactiveVariants) {
            return new ClientInfoBuilder(beanName, qualifier, clientClass, serviceId, clientKind, reactiveVariants, authProviderType, regionConfigured, endpointConfigured, serviceConfigurationPresent, definitionOnly, notes);
        }

        private ClientInfo build() {
            return new ClientInfo(beanName, qualifier, clientClass, serviceId, clientKind, reactiveVariants, authProviderType, regionConfigured, endpointConfigured, serviceConfigurationPresent, definitionOnly, notes);
        }
    }
}
