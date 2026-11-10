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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.email.AsyncTransactionalEmailSender;
import io.micronaut.email.Contact;
import io.micronaut.email.TransactionalEmailSender;
import io.micronaut.email.configuration.FromConfiguration;
import io.micronaut.email.javamail.sender.JavaMailConfiguration;
import io.micronaut.email.javamail.sender.authentication.JavaMailAuthenticationConfiguration;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Collects the email senders and configuration of the application.
 *
 * <p>Senders are read from their bean definitions, so no sender or provider client is created, and nothing is sent.
 * Passwords, API keys and tokens are never shown: JavaMail properties are shown with their value only when they are
 * on a list of settings known to be safe, and credentials are reported only as configured or not.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = TransactionalEmailSender.class)
public class EmailInspector {

    /**
     * JavaMail settings whose values are shown. The values of any other property are hidden.
     */
    static final Set<String> SAFE_PROPERTIES = Set.of(
        "mail.transport.protocol",
        "mail.host",
        "mail.debug",
        "mail.smtp.host",
        "mail.smtp.port",
        "mail.smtp.auth",
        "mail.smtp.starttls.enable",
        "mail.smtp.starttls.required",
        "mail.smtp.ssl.enable",
        "mail.smtp.ssl.protocols",
        "mail.smtp.ssl.trust",
        "mail.smtp.connectiontimeout",
        "mail.smtp.timeout",
        "mail.smtp.writetimeout",
        "mail.smtps.host",
        "mail.smtps.port",
        "mail.smtps.auth"
    );

    private static final Logger LOG = LoggerFactory.getLogger(EmailInspector.class);
    private static final String JAVAMAIL_CONFIGURATION = "io.micronaut.email.javamail.sender.JavaMailConfiguration";
    private static final String TEMPLATE_BODY = "io.micronaut.email.template.TemplateBody";

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public EmailInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return the email senders and configuration
     */
    public EmailInfo inspect() {
        ClassLoader classLoader = getClass().getClassLoader();
        return new EmailInfo(
            from(),
            senders(),
            ClassUtils.isPresent(TEMPLATE_BODY, classLoader),
            ClassUtils.isPresent(JAVAMAIL_CONFIGURATION, classLoader) ? JavaMailSettings.read(beanContext) : null
        );
    }

    private @Nullable String from() {
        try {
            return beanContext.findBean(FromConfiguration.class)
                .map(FromConfiguration::getFrom)
                .map(EmailInspector::format)
                .orElse(null);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the default email sender", e);
            return null;
        }
    }

    static @Nullable String format(@Nullable Contact contact) {
        if (contact == null || contact.getEmail() == null) {
            return null;
        }
        return contact.getName() == null ? contact.getEmail() : contact.getName() + " <" + contact.getEmail() + ">";
    }

    private List<EmailInfo.Sender> senders() {
        // A sender class often implements both the synchronous and the asynchronous interface, so it is listed once
        Map<String, EmailInfo.Sender> senders = new TreeMap<>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(TransactionalEmailSender.class)) {
            add(senders, definition, false);
        }
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(AsyncTransactionalEmailSender.class)) {
            add(senders, definition, true);
        }
        return List.copyOf(senders.values());
    }

    private static void add(Map<String, EmailInfo.Sender> senders, BeanDefinition<?> definition, boolean async) {
        Class<?> type = definition.getBeanType();
        String name = definition.stringValue(Named.class).orElse(type.getSimpleName());
        senders.merge(
            name + " " + type.getName(),
            new EmailInfo.Sender(name, provider(type.getName()), type.getName(), !async, async),
            (a, b) -> new EmailInfo.Sender(a.name(), a.provider(), a.type(), a.sync() || b.sync(), a.async() || b.async())
        );
    }

    /**
     * @param type the sender class name
     * @return the provider of the sender, inferred from its package
     */
    static String provider(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains(".javamail.")) {
            return "JavaMail (SMTP)";
        }
        if (lower.contains(".ses.") || lower.contains("amazon")) {
            return "Amazon SES";
        }
        if (lower.contains(".sendgrid.")) {
            return "SendGrid";
        }
        if (lower.contains(".mailjet.")) {
            return "Mailjet";
        }
        if (lower.contains(".postmark.")) {
            return "Postmark";
        }
        if (lower.contains(".mailtrap.")) {
            return "Mailtrap";
        }
        if (lower.contains(".mailpit.")) {
            return "Mailpit";
        }
        return "custom";
    }

    /**
     * Reads the JavaMail settings, in its own class so that it is only loaded when JavaMail is on the classpath.
     */
    private static final class JavaMailSettings {

        static EmailInfo.@Nullable JavaMail read(BeanContext beanContext) {
            try {
                Map<String, EmailInfo.Property> properties = new TreeMap<>();
                beanContext.findBean(JavaMailConfiguration.class)
                    .filter(JavaMailConfiguration::isEnabled)
                    .map(JavaMailConfiguration::getProperties)
                    .ifPresent(values -> values.forEach((key, value) -> properties.put(key, property(key, value))));
                String authentication = beanContext.findBean(JavaMailAuthenticationConfiguration.class)
                    .filter(JavaMailAuthenticationConfiguration::isEnabled)
                    .filter(configuration -> configuration.getUsername() != null)
                    .map(configuration -> "username and password configured")
                    .orElse("none");
                return new EmailInfo.JavaMail(List.copyOf(properties.values()), authentication);
            } catch (RuntimeException e) {
                LOG.debug("Unable to read the JavaMail configuration", e);
                return null;
            }
        }

        private static EmailInfo.Property property(String key, @Nullable Object value) {
            String name = key.startsWith("mail.") ? key : "mail." + key;
            boolean safe = SAFE_PROPERTIES.contains(name.toLowerCase(Locale.ROOT));
            return new EmailInfo.Property(name, safe && value != null ? String.valueOf(value) : null);
        }
    }
}
