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
package io.micronaut.controlpanel.panels.email;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailProviderDetectionTest {

    @Test
    void detectsKnownProviderNames() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "email-provider-detection"))) {
            EmailDiagnosticsAnalyzer analyzer = context.getBean(EmailDiagnosticsAnalyzer.class);

            assertEquals(EmailProvider.JAVAMAIL, analyzer.detectProvider(descriptor("javamail")));
            assertEquals(EmailProvider.JAVAMAIL, analyzer.detectProvider(descriptor("javaxmail")));
            assertEquals(EmailProvider.SES, analyzer.detectProvider(descriptor("ses")));
            assertEquals(EmailProvider.SENDGRID, analyzer.detectProvider(descriptor("sendgrid")));
            assertEquals(EmailProvider.MAILJET, analyzer.detectProvider(descriptor("mailjet")));
            assertEquals(EmailProvider.MAILTRAP, analyzer.detectProvider(descriptor("mailtrap")));
            assertEquals(EmailProvider.POSTMARK, analyzer.detectProvider(descriptor("postmark")));
            assertEquals(EmailProvider.CUSTOM, analyzer.detectProvider(descriptor("custom")));
        }
    }

    @Test
    void analyzesSafeProviderConfiguration() {
        Map<String, Object> properties = Map.ofEntries(
            Map.entry("spec.name", "email-provider-detection"),
            Map.entry("micronaut.email.from.email", "configured@example.test"),
            Map.entry("micronaut.control-panel.email.test-send.enabled", true),
            Map.entry("micronaut.control-panel.email.test-send.recipient", "safe@example.test"),
            Map.entry("javamail.properties.mail.smtp.host", "localhost"),
            Map.entry("javamail.properties.mail.smtp.port", 1025),
            Map.entry("javamail.properties.mail.smtp.password", "smtp-secret"),
            Map.entry("sendgrid.api-key", "SG.secret"),
            Map.entry("mailjet.version", "v3.1"),
            Map.entry("mailjet.api-key", "mj-key"),
            Map.entry("mailjet.api-secret", "mj-secret"),
            Map.entry("mailtrap.api-url", "http://localhost:8025"),
            Map.entry("mailtrap.inbox-id", "123"),
            Map.entry("mailtrap.api-token", "mt-secret"),
            Map.entry("postmark.track-opens", true),
            Map.entry("postmark.server-token", "pm-secret"),
            Map.entry("aws.region", "us-east-1"),
            Map.entry("aws.access-key-id", "AKIA-secret"),
            Map.entry("aws.secret-access-key", "aws-secret")
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            EmailDiagnosticsAnalyzer analyzer = context.getBean(EmailDiagnosticsAnalyzer.class);

            assertEntry(analyzer.analyze(descriptor("javamail")).configuration(), "SMTP host", "localhost", "configured");
            assertEntry(analyzer.analyze(descriptor("sendgrid")).configuration(), "API key", "present (hidden)", "hidden");
            assertEntry(analyzer.analyze(descriptor("mailjet")).configuration(), "API version", "v3.1", "configured");
            assertEntry(analyzer.analyze(descriptor("mailtrap")).configuration(), "API URL", "http://localhost:8025", "configured");
            assertEntry(analyzer.analyze(descriptor("postmark")).configuration(), "Track opens", "true", "configured");
            assertEntry(analyzer.analyze(descriptor("ses")).configuration(), "Region", "us-east-1", "configured");

            EmailDiagnosticModel custom = analyzer.analyze(descriptor("custom"));
            assertTrue(custom.configuration().isEmpty());
            assertEquals("configured", custom.defaultFromStatus());
            assertTrue(custom.testSend().configured());
            assertFalse(custom.toString().contains("smtp-secret"));
            assertFalse(custom.toString().contains("SG.secret"));
        }
    }

    private static EmailSenderDescriptor descriptor(String name) {
        return new EmailSenderDescriptor(name);
    }

    private static void assertEntry(List<EmailDiagnosticModel.ConfigurationEntry> entries, String label, String value, String status) {
        EmailDiagnosticModel.ConfigurationEntry entry = entries.stream()
            .filter(candidate -> candidate.label().equals(label))
            .findFirst()
            .orElseThrow();
        assertEquals(value, entry.value());
        assertEquals(status, entry.status());
    }
}
