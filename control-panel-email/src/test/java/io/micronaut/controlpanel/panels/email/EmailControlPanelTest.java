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
import io.micronaut.email.Contact;
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
            "javamail.properties.smtp.auth", "true",
            "javamail.authentication.username", "mailer",
            "javamail.authentication.password", "s3cret"
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            EmailControlPanel panel = context.getBean(EmailControlPanel.class);
            assertEquals("Email", panel.getTitle());
            assertEquals("", panel.getBadge());

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
            // A key JavaMail ignores is shown as bound, not rewritten to mail.smtp.auth
            assertTrue(values.containsKey("smtp.auth"), values.toString());
            assertFalse(values.containsKey("mail.smtp.auth"), values.toString());
            assertTrue(javaMail.properties().stream().filter(property -> property.name().equals("smtp.auth")).allMatch(EmailInfo.Property::ignored));
            assertTrue(javaMail.properties().stream().filter(property -> property.name().startsWith("mail.")).noneMatch(EmailInfo.Property::ignored));
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
        assertEquals("Amazon SES", EmailInspector.provider("com.example.AmazonSender"));
        assertEquals("Mailjet", EmailInspector.provider("io.micronaut.email.mailjet.MailjetEmailSender"));
        assertEquals("Postmark", EmailInspector.provider("io.micronaut.email.postmark.PostmarkEmailSender"));
        assertEquals("Mailtrap", EmailInspector.provider("io.micronaut.email.mailtrap.MailtrapEmailSender"));
        assertEquals("Mailpit", EmailInspector.provider("com.example.mailpit.MailpitSender"));
    }

    @Test
    void sendersAndContactsAreDescribed() {
        assertEquals("sync", new EmailInfo.Sender("a", "custom", "A", true, false).modes());
        assertEquals("async", new EmailInfo.Sender("a", "custom", "A", false, true).modes());
        assertNull(EmailInspector.format(null));
        assertNull(EmailInspector.format(new Contact(null)));
        assertEquals("noreply@example.com", EmailInspector.format(new Contact("noreply@example.com")));
        assertEquals("Example <noreply@example.com>", EmailInspector.format(new Contact("noreply@example.com", "Example")));
    }

    @Test
    void showsNoSenderOrAuthenticationWhenNoneIsConfigured() {
        try (ApplicationContext context = ApplicationContext.run()) {
            EmailInfo body = context.getBean(EmailControlPanel.class).getBody();
            assertNull(body.from());
            assertNotNull(body.javaMail());
            assertEquals("none", body.javaMail().authentication());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(EmailControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(EmailControlPanel.class));
        }
    }
}
