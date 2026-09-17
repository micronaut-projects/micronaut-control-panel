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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

/**
 * Email control panel configuration.
 */
@ConfigurationProperties(EmailControlPanelConfiguration.PREFIX)
@ReflectiveAccess
public final class EmailControlPanelConfiguration {

    public static final String PREFIX = "micronaut.control-panel.email";

    private TestSendConfiguration testSend = new TestSendConfiguration();

    public TestSendConfiguration getTestSend() {
        return testSend;
    }

    public void setTestSend(TestSendConfiguration testSend) {
        this.testSend = testSend;
    }

    /**
     * Test send configuration.
     */
    @ConfigurationProperties("test-send")
    @ReflectiveAccess
    public static final class TestSendConfiguration {
        private boolean enabled;
        private @Nullable String recipient;
        private String subjectPrefix = "[Micronaut Control Panel]";
        private boolean allowArbitraryRecipient;
        private boolean includeConfigurationSummary;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public @Nullable String getRecipient() {
            return recipient;
        }

        public void setRecipient(@Nullable String recipient) {
            this.recipient = recipient;
        }

        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        public void setSubjectPrefix(String subjectPrefix) {
            this.subjectPrefix = subjectPrefix;
        }

        public boolean isAllowArbitraryRecipient() {
            return allowArbitraryRecipient;
        }

        public void setAllowArbitraryRecipient(boolean allowArbitraryRecipient) {
            this.allowArbitraryRecipient = allowArbitraryRecipient;
        }

        public boolean isIncludeConfigurationSummary() {
            return includeConfigurationSummary;
        }

        public void setIncludeConfigurationSummary(boolean includeConfigurationSummary) {
            this.includeConfigurationSummary = includeConfigurationSummary;
        }
    }
}
