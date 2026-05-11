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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailRedactorTest {

    private final EmailRedactor redactor = new EmailRedactor();

    @Test
    void redactsSecretKeys() {
        assertTrue(redactor.isSecretKey("sendgrid.api-key"));
        assertTrue(redactor.isSecretKey("mailtrap.api-token"));
        assertTrue(redactor.isSecretKey("aws.secret-access-key"));
        assertFalse(redactor.isSecretKey("javamail.properties.mail.smtp.host"));
        assertEquals("present (hidden)", redactor.safeValue("mailtrap.api-token", "mt-secret"));
    }

    @Test
    void redactsSecretFragmentsFromMessages() {
        String redacted = redactor.redact("smtp://user:password@smtp.example token abc123 Authorization: Bearer abc apiKey=SG.secret password: guess");

        assertFalse(redacted.contains("password@smtp"));
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("SG.secret"));
        assertFalse(redacted.contains("guess"));
        assertTrue(redacted.contains(EmailRedactor.REDACTED));
    }
}
