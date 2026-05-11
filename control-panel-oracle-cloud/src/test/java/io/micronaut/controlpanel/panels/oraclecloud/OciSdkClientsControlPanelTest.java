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
import com.oracle.bmc.identity.IdentityClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.oraclecloud.clients.reactor.objectstorage.ObjectStorageReactorClient;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OciSdkClientsControlPanelTest {

    private static final String PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----leak-test-----END PRIVATE KEY-----";
    private static final String PASSPHRASE = "test-passphrase";
    private static final String FINGERPRINT = "11:22:33:44";
    private static final String SESSION_TOKEN = "session-token-value";
    private static final String CONFIG_PATH = "/home/dev/.oci/config";
    private static final String PROFILE = "ADMIN_PROFILE";
    private static final String PROXY_PASSWORD = "proxy-secret";
    private static final String TENANCY_OCID = "ocid1.tenancy.oc1..example";
    private static final String USER_OCID = "ocid1.user.oc1..example";
    private static final AtomicInteger CLIENT_CALLS = new AtomicInteger();
    private static final AtomicInteger AUTH_CALLS = new AtomicInteger();

    @Test
    void detectsConfiguredClientAndDoesNotCallOciClientOrAuthProviderMethods() {
        AtomicInteger clientCalls = new AtomicInteger();
        AtomicInteger authCalls = new AtomicInteger();
        try (ApplicationContext context = context(clientCalls, authCalls)) {
            context.getBean(IdentityClient.class);
            OciSdkClientsControlPanel.Body body = context.getBean(OciSdkClientsControlPanel.class).getBody();

            assertEquals(1, body.summary().clientCount());
            OciSdkClientsControlPanel.ClientInfo client = body.clients().get(0);
            assertEquals("identity", client.serviceId());
            assertEquals("blocking", client.clientKind());
            assertEquals("com.oracle.bmc.identity.IdentityClient", client.clientClass());
            assertTrue(client.regionConfigured());
            assertTrue(client.serviceConfigurationPresent());
            assertEquals("simple", client.authProviderType());
            assertEquals(0, clientCalls.get());
            assertEquals(0, authCalls.get());
        }
    }

    @Test
    void detectsServiceSpecificConfigurationWithoutRenderingValues() {
        try (ApplicationContext context = context(new AtomicInteger(), new AtomicInteger())) {
            OciSdkClientsControlPanel.Body body = context.getBean(OciSdkClientsControlPanel.class).getBody();

            OciSdkClientsControlPanel.ServiceConfigurationInfo identity = body.serviceConfigurations()
                .stream()
                .filter(configuration -> "identity".equals(configuration.serviceId()))
                .findFirst()
                .orElseThrow();
            assertTrue(identity.timeoutConfigured());
            assertTrue(identity.configuredCategories().contains("hidden by design"));
            assertFalse(body.toString().contains(PROXY_PASSWORD));
            assertFalse(body.toString().contains("/tmp/identity-keystore.p12"));
        }
    }

    @Test
    void reportsAuthenticationProviderTypeOnly() {
        AtomicInteger authCalls = new AtomicInteger();
        try (ApplicationContext context = context(new AtomicInteger(), authCalls)) {
            OciSdkClientsControlPanel.AuthenticationInfo authentication = context.getBean(OciSdkClientsControlPanel.class).getBody().authentication();

            assertEquals("simple", authentication.providerType());
            assertTrue(authentication.providerClass().contains("Simple"));
            assertTrue(authentication.hiddenFields().contains("private keys"));
            assertEquals(0, authCalls.get());
        }
    }

    @Test
    void redactsSecretAndAccountIdentifyingConfigurationValues() {
        try (ApplicationContext context = context(new AtomicInteger(), new AtomicInteger())) {
            String body = context.getBean(OciSdkClientsControlPanel.class).getBody().toString();

            for (String secret : List.of(PRIVATE_KEY, PASSPHRASE, FINGERPRINT, SESSION_TOKEN, CONFIG_PATH, PROFILE, PROXY_PASSWORD, TENANCY_OCID, USER_OCID)) {
                assertFalse(body.contains(secret), () -> "Body leaked secret value: " + secret);
            }
            assertTrue(body.contains("hidden by design"));
        }
    }

    @Test
    void exposesHttpConflictAndNettyPresenceSignalsOnly() {
        try (ApplicationContext context = context(new AtomicInteger(), new AtomicInteger())) {
            OciSdkClientsControlPanel.Body body = context.getBean(OciSdkClientsControlPanel.class).getBody();

            assertTrue(body.globalHttp().globalOciClientConfigured());
            assertTrue(body.globalHttp().micronautHttpServicesOciConfigured());
            assertTrue(body.globalHttp().conflictWarning());
            assertTrue(body.netty().managedProviderGlobalConfigured());
            assertTrue(body.netty().managedProviderGlobalEnabled());
            assertTrue(body.notices().stream().anyMatch(notice -> "http-config-conflict".equals(notice.code())));
            assertTrue(body.notices().stream().anyMatch(notice -> "no-network".equals(notice.code())));
        }
    }

    @Test
    void infersReactorWrapperServiceIdAndDoesNotDuplicateCurrentKind() {
        try (ApplicationContext context = context(
            new AtomicInteger(),
            new AtomicInteger(),
            Map.of("reactor.fixture.enabled", true)
        )) {
            OciSdkClientsControlPanel.ClientInfo reactorClient = context.getBean(OciSdkClientsControlPanel.class).getBody().clients()
                .stream()
                .filter(client -> client.clientClass().equals("io.micronaut.oraclecloud.clients.reactor.objectstorage.ObjectStorageReactorClient"))
                .findFirst()
                .orElseThrow();

            assertEquals("objectstorage", reactorClient.serviceId());
            assertEquals("reactor", reactorClient.clientKind());
            assertFalse(reactorClient.reactiveVariants().contains("reactor"));
        }
    }

    @Test
    void ignoresNonOracleCloudSensitiveSystemPropertiesForNotices() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "OciSdkClientsControlPanelTest"))) {
            OciSdkClientsControlPanel.Body body = context.getBean(OciSdkClientsControlPanel.class).getBody();

            assertTrue(body.notices().stream().noneMatch(notice -> "permission-hidden".equals(notice.code())));
        }
    }

    @Test
    void panelIsAvailableInOracleCloudCategory() {
        try (ApplicationContext context = context(new AtomicInteger(), new AtomicInteger())) {
            OciSdkClientsControlPanel panel = context.getBean(OciSdkClientsControlPanel.class);
            ControlPanelRepository repository = context.getBean(ControlPanelRepository.class);

            assertEquals("oci-sdk-clients", panel.getName());
            assertEquals("OCI SDK Clients", panel.getTitle());
            assertEquals("Oracle Cloud", panel.getCategory().name());
            assertTrue(repository.findByName(OciSdkClientsControlPanel.NAME).isPresent());
            assertEquals(1, repository.countByCategoryId("oracle-cloud"));
            assertNotNull(panel.getBody());
        }
    }

    @Test
    void panelIsAbsentFromRepositoryWhenDisabled() {
        try (ApplicationContext context = context(
            new AtomicInteger(),
            new AtomicInteger(),
            Map.of(OciSdkClientsControlPanel.ENABLED_PROPERTY, false)
        )) {
            ControlPanelRepository repository = context.getBean(ControlPanelRepository.class);

            assertFalse(context.containsBean(OciSdkClientsControlPanel.class));
            assertTrue(repository.findByName(OciSdkClientsControlPanel.NAME).isEmpty());
            assertEquals(0, repository.countByCategoryId("oracle-cloud"));
        }
    }

    private static ApplicationContext context(AtomicInteger clientCalls, AtomicInteger authCalls) {
        return context(clientCalls, authCalls, Map.of());
    }

    private static ApplicationContext context(AtomicInteger clientCalls, AtomicInteger authCalls, Map<String, Object> additionalProperties) {
        CLIENT_CALLS.set(0);
        AUTH_CALLS.set(0);
        TestFactory.clientCalls = clientCalls;
        TestFactory.authCalls = authCalls;
        Map<String, Object> properties = new HashMap<>(Map.ofEntries(
            Map.entry("spec.name", "OciSdkClientsControlPanelTest"),
            Map.entry("oci.config.enabled", false),
            Map.entry("oci.region", "us-ashburn-1"),
            Map.entry("oci.diagnostics.tenant-id", TENANCY_OCID),
            Map.entry("oci.diagnostics.user-id", USER_OCID),
            Map.entry("oci.diagnostics.fingerprint", FINGERPRINT),
            Map.entry("oci.diagnostics.private-key", PRIVATE_KEY),
            Map.entry("oci.diagnostics.passphrase", PASSPHRASE),
            Map.entry("oci.diagnostics.config.path", CONFIG_PATH),
            Map.entry("oci.diagnostics.config.profile", PROFILE),
            Map.entry("oci.diagnostics.config.session-token", SESSION_TOKEN),
            Map.entry("oci.client.read-timeout", "10s"),
            Map.entry("micronaut.http.services.oci.read-timeout", "5s"),
            Map.entry("oci.clients.identity.read-timeout", "2s"),
            Map.entry("oci.clients.identity.proxy-password", PROXY_PASSWORD),
            Map.entry("oci.clients.identity.ssl.key-store.path", "/tmp/identity-keystore.p12"),
            Map.entry("oci.netty.use-managed-provider-globally", true)
        ));
        properties.putAll(additionalProperties);
        return ApplicationContext.run(properties);
    }

    @Factory
    static final class TestFactory {
        private static AtomicInteger clientCalls = CLIENT_CALLS;
        private static AtomicInteger authCalls = AUTH_CALLS;

        @Singleton
        @Named("identity")
        IdentityClient identityClient() {
            return new IdentityClient(clientCalls);
        }

        @Singleton
        @Requires(property = "reactor.fixture.enabled", value = "true")
        ObjectStorageReactorClient objectStorageReactorClient() {
            return new ObjectStorageReactorClient();
        }

        @Singleton
        AuthenticationDetailsProvider authenticationDetailsProvider() {
            return new SimpleThrowingAuthenticationDetailsProvider(authCalls);
        }
    }

    private static final class SimpleThrowingAuthenticationDetailsProvider implements AuthenticationDetailsProvider {
        private final AtomicInteger calls;

        private SimpleThrowingAuthenticationDetailsProvider(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public String getKeyId() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }

        @Override
        public InputStream getPrivateKey() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }

        @Override
        public String getPassPhrase() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }

        @Override
        public char[] getPassphraseCharacters() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }

        @Override
        public String getFingerprint() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }

        @Override
        public String getTenantId() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }

        @Override
        public String getUserId() {
            calls.incrementAndGet();
            throw new AssertionError("Auth provider credential methods must not be called");
        }
    }
}
