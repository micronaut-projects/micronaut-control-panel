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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.email.AsyncEmailSender;
import io.micronaut.email.AsyncTransactionalEmailSender;
import io.micronaut.email.EmailSender;
import io.micronaut.email.TransactionalEmailSender;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Internal
@Singleton
class EmailSenderRegistry {

    @SuppressWarnings("unchecked")
    private static final Argument<EmailSender<?, ?>> EMAIL_SENDER_ARGUMENT = (Argument<EmailSender<?, ?>>) (Argument<?>) Argument.of(EmailSender.class, Argument.ofTypeVariable(Object.class, "I"), Argument.ofTypeVariable(Object.class, "O"));
    @SuppressWarnings("unchecked")
    private static final Argument<TransactionalEmailSender<?, ?>> TRANSACTIONAL_EMAIL_SENDER_ARGUMENT = (Argument<TransactionalEmailSender<?, ?>>) (Argument<?>) Argument.of(TransactionalEmailSender.class, Argument.ofTypeVariable(Object.class, "I"), Argument.ofTypeVariable(Object.class, "O"));
    @SuppressWarnings("unchecked")
    private static final Argument<AsyncEmailSender<?, ?>> ASYNC_EMAIL_SENDER_ARGUMENT = (Argument<AsyncEmailSender<?, ?>>) (Argument<?>) Argument.of(AsyncEmailSender.class, Argument.ofTypeVariable(Object.class, "I"), Argument.ofTypeVariable(Object.class, "O"));
    @SuppressWarnings("unchecked")
    private static final Argument<AsyncTransactionalEmailSender<?, ?>> ASYNC_TRANSACTIONAL_EMAIL_SENDER_ARGUMENT = (Argument<AsyncTransactionalEmailSender<?, ?>>) (Argument<?>) Argument.of(AsyncTransactionalEmailSender.class, Argument.ofTypeVariable(Object.class, "I"), Argument.ofTypeVariable(Object.class, "O"));

    private final BeanContext beanContext;

    EmailSenderRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    List<EmailSenderDescriptor> descriptors() {
        Map<String, EmailSenderDescriptor> descriptors = new TreeMap<>();
        beanContext.getBeanDefinitions(EMAIL_SENDER_ARGUMENT).forEach(beanDefinition -> {
            EmailSender<?, ?> sender = beanContext.getBeanRegistration(beanDefinition).getBean();
            descriptor(descriptors, beanDefinition.getName(), sender).addEmailSender(sender);
        });
        beanContext.getBeanDefinitions(TRANSACTIONAL_EMAIL_SENDER_ARGUMENT).forEach(beanDefinition -> {
            TransactionalEmailSender<?, ?> sender = beanContext.getBeanRegistration(beanDefinition).getBean();
            descriptor(descriptors, beanDefinition.getName(), sender).addTransactionalEmailSender(sender);
        });
        beanContext.getBeanDefinitions(ASYNC_EMAIL_SENDER_ARGUMENT).forEach(beanDefinition -> {
            AsyncEmailSender<?, ?> sender = beanContext.getBeanRegistration(beanDefinition).getBean();
            descriptor(descriptors, beanDefinition.getName(), sender).addAsyncEmailSender(sender);
        });
        beanContext.getBeanDefinitions(ASYNC_TRANSACTIONAL_EMAIL_SENDER_ARGUMENT).forEach(beanDefinition -> {
            AsyncTransactionalEmailSender<?, ?> sender = beanContext.getBeanRegistration(beanDefinition).getBean();
            descriptor(descriptors, beanDefinition.getName(), sender).addAsyncTransactionalEmailSender(sender);
        });
        return descriptors.values().stream().toList();
    }

    Optional<EmailSenderDescriptor> find(String senderName) {
        return descriptors().stream()
            .filter(descriptor -> descriptor.name().equals(senderName))
            .findFirst();
    }

    private EmailSenderDescriptor descriptor(Map<String, EmailSenderDescriptor> descriptors, String beanName, Named sender) {
        String senderName = sender.getName();
        if (StringUtils.isEmpty(senderName)) {
            senderName = beanName;
        }
        if (StringUtils.isEmpty(senderName)) {
            senderName = "default";
        }
        return descriptors.computeIfAbsent(senderName, EmailSenderDescriptor::new);
    }
}
