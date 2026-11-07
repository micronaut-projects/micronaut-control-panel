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
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailControlPanelTest {

    @Test
    void showsSendersAndJavaMailSettingsWithoutSecrets() {
        Map<String, Object> properties = Map.of(
            "micronaut.email.from.email", "noreply@example.com",
            "micronaut.email.from.name", "Example",
            "javamail.properties.mail.smtp.host", "smtp.example.com",
            "javamail.properties.mail.smtp.port", "587",
            "javamail.properties.mail.smtp.password", "s3cret",
            "javamail.authentication.username", "mailer",
            "javamail.authentication.password", "s3cret"
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            EmailControlPanel panel = context.getBean(EmailControlPanel.class);
            assertEquals("Email", panel.getTitle());

            EmailInfo body = panel.getBody();
            assertEquals("Example <noreply@example.com>", body.from());
            assertFalse(body.templates());
            assertEquals(1, body.senders().size(), body.senders().toString());
            EmailInfo.Sender sender = body.senders().getFirst();
            assertEquals("JavaMail (SMTP)", sender.provider());
            assertEquals("sync and async", sender.modes());

            EmailInfo.JavaMail javaMail = body.javaMail();
            assertNotNull(javaMail);
            assertEquals("username and password configured", javaMail.authentication());
            Map<String, String> values = new java.util.HashMap<>();
            javaMail.properties().forEach(property -> values.put(property.name(), property.value()));
            assertEquals("smtp.example.com", values.get("mail.smtp.host"), javaMail.toString());
            assertEquals("587", values.get("mail.smtp.port"));
            assertTrue(values.containsKey("mail.smtp.password"));
            assertNull(values.get("mail.smtp.password"));
            assertFalse(body.toString().contains("s3cret"));
            assertFalse(body.toString().contains("mailer"));

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(EmailControlPanel.NAME).isPresent());
        }
    }

    @Test
    void providerIsInferredFromTheSenderPackage() {
        assertEquals("Amazon SES", EmailInspector.provider("io.micronaut.email.ses.SesEmailSender"));
        assertEquals("SendGrid", EmailInspector.provider("io.micronaut.email.sendgrid.SendgridEmailSender"));
        assertEquals("custom", EmailInspector.provider("com.example.MySender"));
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(EmailControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(EmailControlPanel.class));
        }
    }
}
