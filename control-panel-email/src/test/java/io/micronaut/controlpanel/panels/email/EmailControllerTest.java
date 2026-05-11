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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailControllerTest {

    @Test
    void disabledTestSendDoesNotInvokeSender() {
        EmailTestFactory.MOCK.invocations();
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, baseProperties(false, null, false));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            int before = EmailTestFactory.MOCK.invocations();
            HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.POST("/control-panel/email-control-panel-controller/javamail/test-send", Map.of()), EmailController.TestSendResult.class));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
            assertEquals(before, EmailTestFactory.MOCK.invocations());
        }
    }

    @Test
    void enabledTestSendSucceeds() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, baseProperties(true, "safe@example.test", false));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            EmailController.TestSendResult result = client.toBlocking().retrieve(
                HttpRequest.POST("/control-panel/email-control-panel-controller/javamail/test-send", Map.of()),
                EmailController.TestSendResult.class
            );

            assertTrue(result.success());
            assertEquals("sent", result.status());
        }
    }

    @Test
    void arbitraryRecipientIsRejectedByDefault() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, baseProperties(true, "safe@example.test", false));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.POST("/control-panel/email-control-panel-controller/javamail/test-send", Map.of("recipient", "other@example.test")), EmailController.TestSendResult.class));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        }
    }

    @Test
    void failureResponseIsRedacted() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, baseProperties(true, "safe@example.test", false));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.POST("/control-panel/email-control-panel-controller/failing/test-send", Map.of()), EmailController.TestSendResult.class));

            EmailController.TestSendResult result = exception.getResponse().getBody(EmailController.TestSendResult.class).orElseThrow();
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
            assertEquals("failed", result.status());
            assertFalse(result.message().contains("SG.secret"));
            assertFalse(result.message().contains("abc123"));
            assertTrue(result.message().contains(EmailRedactor.REDACTED));
        }
    }

    private static Map<String, Object> baseProperties(boolean enabled, String recipient, boolean arbitrary) {
        return Map.of(
            "spec.name", "email-control-panel",
            "micronaut.email.from.email", "configured@example.test",
            "micronaut.control-panel.email.test-send.enabled", enabled,
            "micronaut.control-panel.email.test-send.recipient", recipient == null ? "" : recipient,
            "micronaut.control-panel.email.test-send.allow-arbitrary-recipient", arbitrary
        );
    }
}
