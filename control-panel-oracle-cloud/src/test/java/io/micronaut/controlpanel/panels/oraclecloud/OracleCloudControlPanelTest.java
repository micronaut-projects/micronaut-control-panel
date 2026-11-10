package io.micronaut.controlpanel.panels.oraclecloud;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleCloudControlPanelTest {

    private static final String TENANCY = "ocid1.tenancy.oc1..aaaaaaaatenancysecret";
    private static final String USER = "ocid1.user.oc1..aaaaaaaausersecret";
    private static final String FINGERPRINT = "12:34:56:78:90:ab:cd:ef:12:34:56:78:90:ab:cd:ef";

    @Test
    void showsAuthenticationAndClientsWithoutSecrets() throws Exception {
        String privateKey = privateKey();
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "oci.tenant-id", TENANCY,
            "oci.user-id", USER,
            "oci.fingerprint", FINGERPRINT,
            "oci.private-key", privateKey,
            "oci.passphrase", "passphrase-secret",
            "oci.region", "us-phoenix-1",
            "oci.clients.objectstorage.read-timeout", "45s"
        ))) {
            OracleCloudControlPanel panel = context.getBean(OracleCloudControlPanel.class);
            assertEquals("Oracle Cloud", panel.getTitle());
            assertEquals("", panel.getBadge());

            OracleCloudInfo before = panel.getBody();
            assertNotNull(before.authentication(), before.toString());
            assertEquals("API key in the application configuration", before.authentication().label());
            assertEquals("us-phoenix-1", before.region());
            assertEquals("Micronaut HTTP client (Netty)", before.httpProvider());
            OracleCloudInfo.Client objectStorage = before.clients().stream()
                .filter(client -> client.type().equals(ObjectStorageClient.class.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(before.clients().toString()));
            assertEquals("ObjectStorage", objectStorage.service());
            assertFalse(objectStorage.created(), "the panel does not create clients");
            assertEquals(0, before.createdCount());
            assertEquals(List.of(
                new OracleCloudInfo.ClientConfiguration("oci.client", null, "10s"),
                new OracleCloudInfo.ClientConfiguration("oci.clients.objectstorage", null, "45s")
            ), before.clientConfigurations());

            context.getBean(ObjectStorageClient.class);
            OracleCloudInfo after = panel.getBody();
            OracleCloudInfo.Client created = after.clients().stream()
                .filter(client -> client.type().equals(ObjectStorageClient.class.getName()))
                .findFirst()
                .orElseThrow();
            assertTrue(created.created());
            assertEquals(1, after.createdCount());
            assertEquals("https://objectstorage.us-phoenix-1.oraclecloud.com", created.endpoint());

            String rendered = after.toString();
            for (String secret : List.of(TENANCY, USER, FINGERPRINT, "passphrase-secret", privateKey.substring(40, 80))) {
                assertFalse(rendered.contains(secret), "secret shown: " + secret);
            }
            assertTrue(context.getBean(ControlPanelRepository.class).findByName(OracleCloudControlPanel.NAME).isPresent());
        }
    }

    @Test
    void warnsWhenNoAuthenticationIsAvailable() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("oci.config.enabled", false))) {
            OracleCloudInfo body = context.getBean(OracleCloudControlPanel.class).getBody();
            assertEquals(null, body.authentication(), body.toString());
        }
    }

    @Test
    void showsTheProfileOfTheConfigurationFile(@TempDir Path directory) throws Exception {
        Path key = Files.writeString(directory.resolve("oci-key.pem"), privateKey());
        Path config = Files.writeString(directory.resolve("oci-config"), String.join("\n",
            "[STAGING]",
            "user=" + USER,
            "fingerprint=" + FINGERPRINT,
            "tenancy=" + TENANCY,
            "region=us-ashburn-1",
            "key_file=" + key,
            ""));
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "oci.config.profile", "STAGING",
            "oci.config.path", config.toString(),
            "oci.client.connect-timeout", "1500ms"
        ))) {
            OracleCloudInfo body = context.getBean(OracleCloudControlPanel.class).getBody();
            assertNotNull(body.authentication(), body.toString());
            assertEquals("OCI configuration file", body.authentication().label());
            assertEquals("us-ashburn-1", body.region());
            assertEquals("STAGING", body.profile());
            assertTrue(body.customConfigPath());
            for (String secret : List.of(config.toString(), TENANCY, USER, FINGERPRINT)) {
                assertFalse(body.toString().contains(secret), "secret shown: " + secret);
            }
            assertEquals("1500ms", body.clientConfigurations().getFirst().connectTimeout());
        }
    }

    @Test
    void endpointsAreSanitized() {
        assertEquals("https://objectstorage.example.com:8443/n/ns", OracleCloudInspector.sanitize("https://user:secret@objectstorage.example.com:8443/n/ns?token=abc#frag"));
        assertEquals("(hidden)", OracleCloudInspector.sanitize("mailto:someone@example.com"));
        assertEquals("(hidden)", OracleCloudInspector.sanitize("https://bad host/ with spaces"));
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(OracleCloudControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(OracleCloudControlPanel.class));
        }
    }

    private static String privateKey() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}
