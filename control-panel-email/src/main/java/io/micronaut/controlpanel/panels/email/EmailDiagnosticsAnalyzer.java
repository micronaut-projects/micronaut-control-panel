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

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.email.configuration.FromConfiguration;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Internal
@Singleton
class EmailDiagnosticsAnalyzer {

    private static final String DEFAULT_FROM_PROPERTY = "micronaut.email.from.email";
    private static final String AVAILABLE = "available";
    private static final String CONFIGURED = "configured";
    private static final String DISABLED = "disabled";
    private static final String HIDDEN = "hidden";
    private static final String MISSING = "missing";
    private static final String NOT_AVAILABLE = "not available";
    private static final String NOT_CONFIGURED = "not configured";

    private final Environment environment;
    private final BeanContext beanContext;
    private final EmailControlPanelConfiguration configuration;
    private final EmailRedactor redactor;

    EmailDiagnosticsAnalyzer(Environment environment,
                             BeanContext beanContext,
                             EmailControlPanelConfiguration configuration,
                             EmailRedactor redactor) {
        this.environment = environment;
        this.beanContext = beanContext;
        this.configuration = configuration;
        this.redactor = redactor;
    }

    EmailDiagnosticModel analyze(EmailSenderDescriptor descriptor) {
        EmailProvider provider = detectProvider(descriptor);
        List<EmailDiagnosticModel.ConfigurationEntry> entries = new ArrayList<>();
        addProviderSettings(provider, entries);
        DefaultFrom defaultFrom = defaultFrom();
        boolean templatePresent = classPresent("io.micronaut.email.template.TemplateBody");
        EmailDiagnosticModel.TestSendState testSend = testSendState();
        List<EmailDiagnosticModel.ChecklistItem> checklist = new ArrayList<>();
        checklist.add(new EmailDiagnosticModel.ChecklistItem("Sender bean", AVAILABLE, "Sender bean is active."));
        checklist.add(new EmailDiagnosticModel.ChecklistItem("Default from", defaultFrom.configured() ? CONFIGURED : MISSING, defaultFrom.message()));
        checklist.add(new EmailDiagnosticModel.ChecklistItem("Secrets", HIDDEN, "Secret-like settings are shown only as presence indicators."));
        checklist.add(new EmailDiagnosticModel.ChecklistItem("Templates", templatePresent ? AVAILABLE : NOT_AVAILABLE, templatePresent ? "Micronaut Email template support is on the classpath." : "Template support is not on the classpath."));
        checklist.add(new EmailDiagnosticModel.ChecklistItem("Test send", testSend.configured() ? CONFIGURED : DISABLED, testSend.message()));
        return new EmailDiagnosticModel(
            descriptor.name(),
            provider.label(),
            descriptor.supportedInterfaces().stream().toList(),
            descriptor.implementationTypes().stream().map(redactor::redact).sorted().toList(),
            defaultFrom.configured() ? CONFIGURED : MISSING,
            defaultFrom.value(),
            templatePresent ? "present" : NOT_AVAILABLE,
            entries,
            checklist,
            testSend,
            false
        );
    }

    EmailDiagnosticModel empty() {
        return new EmailDiagnosticModel(
            "no-senders",
            "Custom/Unknown",
            List.of(),
            List.of(),
            MISSING,
            NOT_CONFIGURED,
            classPresent("io.micronaut.email.template.TemplateBody") ? "present" : NOT_AVAILABLE,
            List.of(),
            List.of(
                new EmailDiagnosticModel.ChecklistItem("Sender beans", MISSING, "No Micronaut Email sender beans were detected."),
                new EmailDiagnosticModel.ChecklistItem("Micronaut Email", AVAILABLE, "Micronaut Email API is on the classpath."),
                new EmailDiagnosticModel.ChecklistItem("Provider module", NOT_CONFIGURED, "Add and configure a Micronaut Email provider module.")
            ),
            testSendState(),
            true
        );
    }

    EmailProvider detectProvider(EmailSenderDescriptor descriptor) {
        String haystack = (descriptor.name() + " " + String.join(" ", descriptor.implementationTypes())).toLowerCase(Locale.ROOT);
        if (haystack.contains("javamail") || haystack.contains("javaxmail") || haystack.contains("smtp")) {
            return EmailProvider.JAVAMAIL;
        }
        if (haystack.contains("sendgrid")) {
            return EmailProvider.SENDGRID;
        }
        if (haystack.contains("mailjet")) {
            return EmailProvider.MAILJET;
        }
        if (haystack.contains("mailtrap")) {
            return EmailProvider.MAILTRAP;
        }
        if (haystack.contains("postmark")) {
            return EmailProvider.POSTMARK;
        }
        if (haystack.equals("ses") || haystack.startsWith("ses ") || haystack.contains(".ses") || haystack.contains("amazon") || haystack.contains("aws") || haystack.contains(" ses")) {
            return EmailProvider.SES;
        }
        return EmailProvider.CUSTOM;
    }

    private void addProviderSettings(EmailProvider provider, List<EmailDiagnosticModel.ConfigurationEntry> entries) {
        switch (provider) {
            case JAVAMAIL -> {
                addSafe(entries, "SMTP host", "javamail.properties.mail.smtp.host");
                addSafe(entries, "SMTP port", "javamail.properties.mail.smtp.port");
                addSafe(entries, "SMTP auth", "javamail.properties.mail.smtp.auth");
                addSafe(entries, "SMTP startTLS", "javamail.properties.mail.smtp.starttls.enable");
                addHiddenPresence(entries, "SMTP password", "javamail.properties.mail.smtp.password");
            }
            case SENDGRID -> addHiddenPresence(entries, "API key", "sendgrid.api-key");
            case MAILJET -> {
                addSafe(entries, "API version", "mailjet.version");
                addHiddenPresence(entries, "API key", "mailjet.api-key");
                addHiddenPresence(entries, "API secret", "mailjet.api-secret");
            }
            case MAILTRAP -> {
                addSafe(entries, "API URL", "mailtrap.api-url");
                addSafe(entries, "Inbox ID", "mailtrap.inbox-id");
                addHiddenPresence(entries, "API token", "mailtrap.api-token");
            }
            case POSTMARK -> {
                addSafe(entries, "Track opens", "postmark.track-opens");
                addSafe(entries, "Track links", "postmark.track-links");
                addHiddenPresence(entries, "Server token", "postmark.server-token");
            }
            case SES -> {
                addSafe(entries, "Region", "aws.region");
                addSafe(entries, "Endpoint override", "aws.services.ses.endpoint-override");
                addHiddenPresence(entries, "Access key", "aws.access-key-id");
                addHiddenPresence(entries, "Secret key", "aws.secret-access-key");
            }
            case CUSTOM -> {
                // Custom senders have no provider-specific safe settings to display.
            }
            default -> throw new IllegalStateException("Unknown email provider: " + provider);
        }
        entries.sort(Comparator.comparing(EmailDiagnosticModel.ConfigurationEntry::label));
    }

    private void addSafe(List<EmailDiagnosticModel.ConfigurationEntry> entries, String label, String key) {
        Optional<Object> value = environment.getProperty(key, Object.class);
        value.ifPresent(object -> entries.add(new EmailDiagnosticModel.ConfigurationEntry(label, redactor.safeValue(key, object), "configured")));
    }

    private void addHiddenPresence(List<EmailDiagnosticModel.ConfigurationEntry> entries, String label, String key) {
        boolean configured = environment.containsProperty(key);
        entries.add(new EmailDiagnosticModel.ConfigurationEntry(label, configured ? "present (hidden)" : NOT_CONFIGURED, configured ? HIDDEN : MISSING));
    }

    private DefaultFrom defaultFrom() {
        Optional<String> property = environment.getProperty(DEFAULT_FROM_PROPERTY, String.class)
            .filter(StringUtils::isNotEmpty);
        if (property.isPresent()) {
            return new DefaultFrom(true, redactor.redact(property.get()), "Default from is configured.");
        }
        Optional<FromConfiguration> fromConfiguration = beanContext.findBean(FromConfiguration.class);
        if (fromConfiguration.isPresent()) {
            return new DefaultFrom(true, redactor.redact(fromConfiguration.get().getFrom().getEmail()), "Default from configuration bean is available.");
        }
        return new DefaultFrom(false, NOT_CONFIGURED, "No default from address was detected.");
    }

    private EmailDiagnosticModel.TestSendState testSendState() {
        var testSend = configuration.getTestSend();
        boolean hasRecipient = StringUtils.isNotEmpty(testSend.getRecipient());
        boolean configured = testSend.isEnabled() && hasRecipient;
        String status = configured ? AVAILABLE : DISABLED;
        String message;
        if (!testSend.isEnabled()) {
            message = "Test send is disabled.";
        } else if (!hasRecipient) {
            message = "Test send is enabled but no safe recipient is configured.";
        } else {
            message = "Test send will use the configured safe recipient.";
        }
        return new EmailDiagnosticModel.TestSendState(testSend.isEnabled(), configured, testSend.isAllowArbitraryRecipient(), status, message);
    }

    private boolean classPresent(String className) {
        try {
            Class.forName(className, false, beanContext.getClassLoader());
            return true;
        } catch (ClassNotFoundException _) {
            return false;
        }
    }

    private record DefaultFrom(boolean configured, String value, String message) {
    }
}
