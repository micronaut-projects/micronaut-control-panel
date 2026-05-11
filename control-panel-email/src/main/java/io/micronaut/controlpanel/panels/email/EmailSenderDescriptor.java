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

import io.micronaut.core.annotation.Internal;
import io.micronaut.email.AsyncEmailSender;
import io.micronaut.email.AsyncTransactionalEmailSender;
import io.micronaut.email.EmailSender;
import io.micronaut.email.TransactionalEmailSender;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Internal
final class EmailSenderDescriptor {
    private final String name;
    private EmailSender<?, ?> emailSender;
    private TransactionalEmailSender<?, ?> transactionalEmailSender;
    private AsyncEmailSender<?, ?> asyncEmailSender;
    private AsyncTransactionalEmailSender<?, ?> asyncTransactionalEmailSender;
    private final Set<String> implementationTypes = new LinkedHashSet<>();

    EmailSenderDescriptor(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    Optional<EmailSender<?, ?>> emailSender() {
        return Optional.ofNullable(emailSender);
    }

    Optional<TransactionalEmailSender<?, ?>> transactionalEmailSender() {
        return Optional.ofNullable(transactionalEmailSender);
    }

    Optional<AsyncEmailSender<?, ?>> asyncEmailSender() {
        return Optional.ofNullable(asyncEmailSender);
    }

    Optional<AsyncTransactionalEmailSender<?, ?>> asyncTransactionalEmailSender() {
        return Optional.ofNullable(asyncTransactionalEmailSender);
    }

    Set<String> supportedInterfaces() {
        Set<String> interfaces = new LinkedHashSet<>();
        if (emailSender != null) {
            interfaces.add("EmailSender");
        }
        if (transactionalEmailSender != null) {
            interfaces.add("TransactionalEmailSender");
        }
        if (asyncEmailSender != null) {
            interfaces.add("AsyncEmailSender");
        }
        if (asyncTransactionalEmailSender != null) {
            interfaces.add("AsyncTransactionalEmailSender");
        }
        return interfaces;
    }

    Set<String> implementationTypes() {
        return implementationTypes;
    }

    void addEmailSender(EmailSender<?, ?> sender) {
        this.emailSender = sender;
        implementationTypes.add(sender.getClass().getName());
    }

    void addTransactionalEmailSender(TransactionalEmailSender<?, ?> sender) {
        this.transactionalEmailSender = sender;
        implementationTypes.add(sender.getClass().getName());
    }

    void addAsyncEmailSender(AsyncEmailSender<?, ?> sender) {
        this.asyncEmailSender = sender;
        implementationTypes.add(sender.getClass().getName());
    }

    void addAsyncTransactionalEmailSender(AsyncTransactionalEmailSender<?, ?> sender) {
        this.asyncTransactionalEmailSender = sender;
        implementationTypes.add(sender.getClass().getName());
    }
}
