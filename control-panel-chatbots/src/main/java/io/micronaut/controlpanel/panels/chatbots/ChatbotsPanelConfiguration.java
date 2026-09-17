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
package io.micronaut.controlpanel.panels.chatbots;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for Chatbots diagnostics in Control Panel.
 *
 * @since 2.0.0
 */
@ConfigurationProperties(ChatbotsPanelConfiguration.PREFIX)
public class ChatbotsPanelConfiguration {

    public static final String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".chatbots";

    private Optional<String> publicUrl = Optional.empty();
    private Telegram telegram = new Telegram();

    /**
     * @return Public application URL used to build expected webhook URLs.
     */
    public Optional<String> getPublicUrl() {
        return publicUrl;
    }

    /**
     * @param publicUrl Public application URL used to build expected webhook URLs.
     */
    public void setPublicUrl(Optional<String> publicUrl) {
        this.publicUrl = publicUrl;
    }

    /**
     * @return Telegram diagnostics configuration.
     */
    public Telegram getTelegram() {
        return telegram;
    }

    /**
     * @param telegram Telegram diagnostics configuration.
     */
    public void setTelegram(Telegram telegram) {
        this.telegram = telegram;
    }

    /**
     * Telegram diagnostics configuration.
     */
    @ConfigurationProperties("telegram")
    public static class Telegram {
        private WebhookStatus webhookStatus = new WebhookStatus();

        /**
         * @return Telegram webhook status lookup configuration.
         */
        public WebhookStatus getWebhookStatus() {
            return webhookStatus;
        }

        /**
         * @param webhookStatus Telegram webhook status lookup configuration.
         */
        public void setWebhookStatus(WebhookStatus webhookStatus) {
            this.webhookStatus = webhookStatus;
        }
    }

    /**
     * Telegram webhook status lookup configuration.
     */
    @ConfigurationProperties("webhook-status")
    public static class WebhookStatus {
        public static final String DEFAULT_BASE_URL = "https://api.telegram.org";
        public static final int DEFAULT_TIMEOUT_MILLIS = 2_000;

        private boolean enabled;
        private String baseUrl = DEFAULT_BASE_URL;
        private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private Map<String, String> apiTokens = new LinkedHashMap<>();

        /**
         * @return Whether Telegram getWebhookInfo lookup is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether Telegram getWebhookInfo lookup is enabled.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return Base URL for the Telegram Bot API.
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * @param baseUrl Base URL for the Telegram Bot API.
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * @return Timeout in milliseconds for each webhook status lookup.
         */
        public int getTimeoutMillis() {
            return timeoutMillis;
        }

        /**
         * @param timeoutMillis Timeout in milliseconds for each webhook status lookup.
         */
        public void setTimeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        /**
         * @return Per-bot Telegram Bot API tokens used only for getWebhookInfo lookup.
         */
        public Map<String, String> getApiTokens() {
            return apiTokens;
        }

        /**
         * @param apiTokens Per-bot Telegram Bot API tokens used only for getWebhookInfo lookup.
         */
        public void setApiTokens(Map<String, String> apiTokens) {
            this.apiTokens = apiTokens;
        }
    }
}
