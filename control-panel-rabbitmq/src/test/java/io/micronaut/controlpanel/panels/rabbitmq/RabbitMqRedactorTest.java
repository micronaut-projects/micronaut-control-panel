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
package io.micronaut.controlpanel.panels.rabbitmq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitMqRedactorTest {

    @Test
    void redactsUriUserInfo() {
        assertEquals("amqp://redacted@localhost:5672/%2f", RabbitMqRedactor.redact("amqp://user:secret@localhost:5672/%2f"));
    }

    @Test
    void redactsSecretKeyValuePairs() {
        assertEquals(RabbitMqRedactor.REDACTED, RabbitMqRedactor.redact("password=secret"));
        assertEquals(RabbitMqRedactor.REDACTED, RabbitMqRedactor.redactKeyValue("server.password", "secret"));
        assertEquals(RabbitMqRedactor.REDACTED, RabbitMqRedactor.redactCredential("developer"));
    }
}
