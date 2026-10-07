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

import io.micronaut.email.Email;
import io.micronaut.email.EmailException;
import io.micronaut.email.EmailSender;
import io.micronaut.email.TransactionalEmailSender;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class TestEmailSender implements EmailSender<Object, Object>, TransactionalEmailSender<Object, Object> {

    private final String name;
    private final boolean fail;
    private final AtomicInteger invocations = new AtomicInteger();

    TestEmailSender(String name, boolean fail) {
        this.name = name;
        this.fail = fail;
    }

    @Override
    public Object send(Email.Builder builder, Consumer<Object> requestCustomizer) throws EmailException {
        invocations.incrementAndGet();
        if (fail) {
            throw new EmailException("provider rejected apiKey=SG.secret token abc123");
        }
        return new Object();
    }

    @Override
    public Object send(Email email, Consumer<Object> requestCustomizer) throws EmailException {
        invocations.incrementAndGet();
        if (fail) {
            throw new EmailException("provider rejected apiKey=SG.secret token abc123");
        }
        return new Object();
    }

    @Override
    public String getName() {
        return name;
    }

    int invocations() {
        return invocations.get();
    }
}
