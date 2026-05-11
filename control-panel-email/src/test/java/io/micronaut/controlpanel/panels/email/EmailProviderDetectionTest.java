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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailProviderDetectionTest {

    @Test
    void detectsKnownProviderNames() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "email-provider-detection"))) {
            EmailDiagnosticsAnalyzer analyzer = context.getBean(EmailDiagnosticsAnalyzer.class);

            assertEquals(EmailProvider.JAVAMAIL, analyzer.detectProvider(descriptor("javamail")));
            assertEquals(EmailProvider.JAVAMAIL, analyzer.detectProvider(descriptor("javaxmail")));
            assertEquals(EmailProvider.SES, analyzer.detectProvider(descriptor("ses")));
            assertEquals(EmailProvider.SENDGRID, analyzer.detectProvider(descriptor("sendgrid")));
            assertEquals(EmailProvider.MAILJET, analyzer.detectProvider(descriptor("mailjet")));
            assertEquals(EmailProvider.MAILTRAP, analyzer.detectProvider(descriptor("mailtrap")));
            assertEquals(EmailProvider.POSTMARK, analyzer.detectProvider(descriptor("postmark")));
            assertEquals(EmailProvider.CUSTOM, analyzer.detectProvider(descriptor("custom")));
        }
    }

    private static EmailSenderDescriptor descriptor(String name) {
        return new EmailSenderDescriptor(name);
    }
}
