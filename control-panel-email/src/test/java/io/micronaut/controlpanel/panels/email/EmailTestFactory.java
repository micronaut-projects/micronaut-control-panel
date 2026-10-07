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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "email-control-panel")
class EmailTestFactory {

    static final TestEmailSender MOCK = new TestEmailSender("javamail", false);
    static final TestEmailSender FAILING = new TestEmailSender("failing", true);

    @Singleton
    @Named("mock")
    TestEmailSender mockSender() {
        return MOCK;
    }

    @Singleton
    @Named("failing")
    TestEmailSender failingSender() {
        return FAILING;
    }
}
