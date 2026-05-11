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

import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.email.Email;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Guarded helper endpoint for controlled test sends.
 */
@Controller(ControlPanelSecurityPaths.EMAIL)
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class EmailController {

    private static final Logger LOG = LoggerFactory.getLogger(EmailController.class);

    private final EmailSenderRegistry registry;
    private final EmailDiagnosticsAnalyzer analyzer;
    private final EmailControlPanelConfiguration configuration;
    private final EmailRedactor redactor;

    EmailController(EmailSenderRegistry registry,
                    EmailDiagnosticsAnalyzer analyzer,
                    EmailControlPanelConfiguration configuration,
                    EmailRedactor redactor) {
        this.registry = registry;
        this.analyzer = analyzer;
        this.configuration = configuration;
        this.redactor = redactor;
    }

    @Post("/{sender}/test-send")
    public HttpResponse<TestSendResult> testSend(String sender, @Nullable @Body TestSendRequest request) {
        long started = System.nanoTime();
        var testSend = configuration.getTestSend();
        if (!testSend.isEnabled()) {
            return HttpResponse.badRequest(TestSendResult.disabled("Test send is disabled.", elapsed(started)));
        }
        String recipient = recipient(request);
        if (StringUtils.isEmpty(recipient)) {
            return HttpResponse.badRequest(TestSendResult.disabled("No safe test recipient is configured.", elapsed(started)));
        }
        if (request != null && StringUtils.isNotEmpty(request.recipient()) && !testSend.isAllowArbitraryRecipient()) {
            return HttpResponse.badRequest(TestSendResult.disabled("Arbitrary recipients are disabled for test send.", elapsed(started)));
        }
        var descriptor = registry.find(sender);
        if (descriptor.isEmpty()) {
            return HttpResponse.notFound(TestSendResult.failure("Unknown sender.", "NotFound", elapsed(started)));
        }
        EmailDiagnosticModel diagnostics = analyzer.analyze(descriptor.get());
        try {
            send(descriptor.get(), recipient, diagnostics);
            return HttpResponse.ok(TestSendResult.success("Test send completed.", elapsed(started)));
        } catch (RuntimeException e) {
            String message = redactor.redact(e.getMessage());
            LOG.debug("Email test send failed for sender '{}': {} {}", sender, e.getClass().getName(), message);
            return HttpResponse.serverError(TestSendResult.failure(message, e.getClass().getSimpleName(), elapsed(started)));
        }
    }

    private String recipient(@Nullable TestSendRequest request) {
        if (request != null && StringUtils.isNotEmpty(request.recipient()) && configuration.getTestSend().isAllowArbitraryRecipient()) {
            return request.recipient();
        }
        return configuration.getTestSend().getRecipient();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void send(EmailSenderDescriptor descriptor, String recipient, EmailDiagnosticModel diagnostics) {
        String subjectPrefix = configuration.getTestSend().getSubjectPrefix();
        if (StringUtils.isEmpty(subjectPrefix)) {
            subjectPrefix = "[Micronaut Control Panel]";
        }
        String body = "Micronaut Control Panel email test\n"
            + "Sender: " + diagnostics.senderName() + "\n"
            + "Provider: " + diagnostics.provider() + "\n"
            + "Timestamp: " + Instant.now() + "\n";
        if (configuration.getTestSend().isIncludeConfigurationSummary()) {
            body += "Default from: " + diagnostics.defaultFromStatus() + "\n";
        }
        Email.Builder builder = Email.builder()
            .to(recipient)
            .subject(subjectPrefix + " Email test")
            .body(body);
        if (descriptor.emailSender().isPresent()) {
            descriptor.emailSender().get().send(builder);
            return;
        }
        Email email = builder.build();
        if (descriptor.transactionalEmailSender().isPresent()) {
            descriptor.transactionalEmailSender().get().send(email);
            return;
        }
        if (descriptor.asyncEmailSender().isPresent()) {
            Mono.from(((io.micronaut.email.AsyncEmailSender) descriptor.asyncEmailSender().get()).sendAsync(builder)).block();
            return;
        }
        if (descriptor.asyncTransactionalEmailSender().isPresent()) {
            Mono.from(((io.micronaut.email.AsyncTransactionalEmailSender) descriptor.asyncTransactionalEmailSender().get()).sendAsync(email)).block();
            return;
        }
        throw new IllegalStateException("No supported send path is available.");
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    /**
     * Test send request body.
     *
     * @param recipient optional recipient, honored only when arbitrary recipients are enabled
     */
    @Introspected
    @Serdeable.Deserializable
    public record TestSendRequest(@Nullable String recipient) {
    }

    /**
     * Test send result body.
     *
     * @param success whether the send completed
     * @param status safe status label
     * @param message redacted human-readable result message
     * @param exception redacted exception class name for failed sends
     * @param elapsedMillis elapsed send time in milliseconds
     */
    @Serdeable
    @Introspected
    public record TestSendResult(boolean success, String status, String message, @Nullable String exception, long elapsedMillis) {
        static TestSendResult success(String message, long elapsedMillis) {
            return new TestSendResult(true, "sent", message, null, elapsedMillis);
        }

        static TestSendResult disabled(String message, long elapsedMillis) {
            return new TestSendResult(false, "unavailable", message, null, elapsedMillis);
        }

        static TestSendResult failure(String message, String exception, long elapsedMillis) {
            return new TestSendResult(false, "failed", message, exception, elapsedMillis);
        }
    }
}
