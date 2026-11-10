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
import io.micronaut.controlpanel.ui.ControlPanelRenderer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the panel templates with the Control Panel Handlebars instance, so that a renamed accessor or a template
 * error fails the build instead of the page.
 */
class EmailTemplatesTest {

    private static ApplicationContext context;
    private static ControlPanelRenderer renderer;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run();
        renderer = context.getBean(ControlPanelRenderer.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void rendersSendersAndJavaMailWithoutSecrets() {
        var info = new EmailInfo(
            "Example <noreply@example.com>",
            List.of(new EmailInfo.Sender("javaMailEmailSender", "JavaMail (SMTP)", "io.micronaut.email.javamail.sender.JavaMailEmailSender", true, true)),
            true,
            new EmailInfo.JavaMail(List.of(
                new EmailInfo.Property("mail.smtp.host", "<b>smtp.example.com</b>"),
                new EmailInfo.Property("mail.smtp.password", null),
                new EmailInfo.Property("smtp.port", null)
            ), "username and password configured")
        );

        String body = render("body", info);
        assertTrue(body.contains("<code>javaMailEmailSender</code>"), body);
        assertTrue(body.contains("Default sender Example &lt;noreply@example.com&gt;."), body);

        String detail = render("detail", info);
        assertTrue(detail.contains("<code>&lt;b&gt;smtp.example.com&lt;/b&gt;</code>"), "values are escaped");
        assertTrue(detail.contains("<code>mail.smtp.password</code></dt>"), detail);
        assertTrue(detail.contains("(hidden)"), detail);
        assertTrue(detail.contains("<code>smtp.port</code> <span class=\"badge badge-destructive\""), detail);
        assertTrue(detail.contains("ignored by JavaMail"), detail);
        assertFalse(detail.contains("<code>mail.smtp.host</code> <span"), "properties JavaMail reads are not flagged");
        assertTrue(detail.contains("username and password configured"), detail);
        assertTrue(detail.contains("sync and async"), detail);
    }

    @Test
    void rendersAnApplicationWithoutSenders() {
        var info = new EmailInfo(null, List.of(), false, null);

        assertTrue(render("body", info).contains("There are no email senders."));
        assertTrue(render("body", info).contains("No default sender is configured."));
        String detail = render("detail", info);
        assertTrue(detail.contains("There are no email senders. Add a Micronaut Email provider"), detail);
        assertFalse(detail.contains("JavaMail</h3>"), detail);
    }

    private static String render(String template, EmailInfo info) {
        return renderer.render("views/email/" + template, Map.of("controlPanel", Map.of("body", info, "detailedBody", info)));
    }
}
