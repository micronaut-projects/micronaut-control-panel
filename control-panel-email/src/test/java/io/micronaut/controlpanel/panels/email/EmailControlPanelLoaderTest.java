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
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailControlPanelLoaderTest {

    @Test
    void groupsDuplicateSenderInterfacesBySenderName() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            ControlPanelRepository repository = context.getBean(ControlPanelRepository.class);

            assertTrue(repository.findByName("email-javamail").isPresent());
            assertTrue(repository.findByName("email-failing").isPresent());
            assertEquals(2, repository.findAllByCategory(EmailControlPanel.NAME).size());
            EmailControlPanel mockPanel = (EmailControlPanel) repository.findByName("email-javamail").orElseThrow();
            EmailDiagnosticModel body = mockPanel.getBody();

            assertEquals("javamail", body.senderName());
            assertTrue(body.supportedInterfaces().contains("EmailSender"));
            assertTrue(body.supportedInterfaces().contains("TransactionalEmailSender"));
            assertEquals("Email: javamail", mockPanel.getTitle());
            assertEquals("", mockPanel.getBadge());
            assertEquals(EmailControlPanel.CATEGORY, mockPanel.getCategory());
            assertEquals(EmailControlPanel.DEFAULT_ICON_CLASS, mockPanel.getIcon());
        }
    }

    @Test
    void modelContainsOnlySafeConfigurationValues() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            EmailControlPanel panel = (EmailControlPanel) context.getBean(ControlPanelRepository.class)
                .findByName("email-javamail")
                .orElseThrow();

            String renderedModel = panel.getBody().toString();

            assertTrue(renderedModel.contains("configured@example.test"));
            assertTrue(renderedModel.contains("present (hidden)"));
            assertFalse(renderedModel.contains("smtp-password"));
        }
    }

    @Test
    void emptyStatePanelIsAvailableWhenNoSendersExist() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "email-empty"))) {
            ControlPanelRepository repository = context.getBean(ControlPanelRepository.class);

            assertTrue(repository.findByName("email").isPresent());
            EmptyEmailControlPanel panel = (EmptyEmailControlPanel) repository.findByName("email").orElseThrow();
            EmailDiagnosticModel body = panel.getBody();
            assertTrue(body.empty());
            assertEquals("no-senders", body.senderName());
            assertFalse(body.hasConfiguration());
            assertTrue(body.hasChecklist());
            assertEquals("Email", panel.getTitle());
            assertEquals("/views/email/body", panel.getBodyView().file());
            assertEquals("/views/email/detail", panel.getDetailedView().file());
            assertEquals(EmailControlPanel.CATEGORY, panel.getCategory());
            assertEquals(EmailControlPanel.DEFAULT_ICON_CLASS, panel.getIcon());
        }
    }

    @Test
    void configurationPropertiesAreMutable() {
        EmailControlPanelConfiguration configuration = new EmailControlPanelConfiguration();
        EmailControlPanelConfiguration.TestSendConfiguration testSend = new EmailControlPanelConfiguration.TestSendConfiguration();

        testSend.setEnabled(true);
        testSend.setRecipient("safe@example.test");
        testSend.setSubjectPrefix("[Test]");
        testSend.setAllowArbitraryRecipient(true);
        testSend.setIncludeConfigurationSummary(true);
        configuration.setTestSend(testSend);

        assertTrue(configuration.getTestSend().isEnabled());
        assertEquals("safe@example.test", configuration.getTestSend().getRecipient());
        assertEquals("[Test]", configuration.getTestSend().getSubjectPrefix());
        assertTrue(configuration.getTestSend().isAllowArbitraryRecipient());
        assertTrue(configuration.getTestSend().isIncludeConfigurationSummary());
    }

    private static Map<String, Object> properties() {
        return Map.of(
            "spec.name", "email-control-panel",
            "micronaut.email.from.email", "configured@example.test",
            "javamail.properties.mail.smtp.host", "localhost",
            "javamail.properties.mail.smtp.password", "smtp-password",
            "micronaut.control-panel.email.test-send.enabled", true,
            "micronaut.control-panel.email.test-send.recipient", "safe@example.test"
        );
    }
}
