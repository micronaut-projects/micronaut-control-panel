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

import io.micronaut.chatbots.telegram.core.TelegramBotConfiguration;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.web.router.Router;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Requires(classes = TelegramBotConfiguration.class)
final class TelegramChatbotsContributor implements ChatbotsContributor {

    private static final String CHANNEL = "Telegram";
    private static final String CONTROLLER_CLASS = "io.micronaut.chatbots.telegram.http.TelegramController";
    private static final String ENDPOINT_PREFIX = "micronaut.chatbots.telegram.endpoint";
    private static final String DEFAULT_PATH = "/telegram";
    private static final String WEBHOOK_STATUS_PREFIX = ChatbotsPanelConfiguration.PREFIX + ".telegram.webhook-status";
    private static final String API_TOKEN_PLACEHOLDER = "<BOT_API_TOKEN>";
    private static final String SECRET_PLACEHOLDER = "<SECRET_TOKEN>";

    private final BeanContext beanContext;
    private final Environment environment;
    private final Router router;
    private final ChatbotsPanelConfiguration configuration;
    private final TelegramWebhookStatusClient webhookStatusClient;

    TelegramChatbotsContributor(BeanContext beanContext,
                                Environment environment,
                                Router router,
                                ChatbotsPanelConfiguration configuration,
                                TelegramWebhookStatusClient webhookStatusClient) {
        this.beanContext = beanContext;
        this.environment = environment;
        this.router = router;
        this.configuration = configuration;
        this.webhookStatusClient = webhookStatusClient;
    }

    @Override
    public void contribute(ChatbotsDiagnostics diagnostics) {
        ChatbotsControlPanel.EndpointRow endpoint = EndpointResolver.endpoint(
            environment,
            router,
            CHANNEL,
            CONTROLLER_CLASS,
            ENDPOINT_PREFIX + ".enabled",
            ENDPOINT_PREFIX + ".path",
            DEFAULT_PATH
        );
        diagnostics.endpoints.add(endpoint);
        for (BeanRegistration<TelegramBotConfiguration> registration : beanContext.getBeanRegistrations(TelegramBotConfiguration.class)) {
            TelegramBotConfiguration bot = registration.getBean();
            diagnostics.bots.add(new ChatbotsControlPanel.BotRow(
                CHANNEL,
                bot.getName(),
                bot.getAtUsername(),
                bot.isEnabled(),
                tokenStatus(bot.getToken())
            ));
            String expectedUrl = expectedUrl(endpoint.path());
            if (diagnostics.includeWebhookLookup()) {
                diagnostics.webhooks.add(webhookRow(bot.getName(), expectedUrl));
            }
            diagnostics.setupHints.add(new ChatbotsControlPanel.SetupHint(
                CHANNEL,
                "setWebhook for " + bot.getName(),
                setupCommand(expectedUrl, endpoint.path())
            ));
        }
    }

    private ChatbotsControlPanel.WebhookRow webhookRow(String botName, String expectedUrl) {
        ChatbotsPanelConfiguration.WebhookStatus webhookStatus = webhookStatusConfiguration(botName);
        if (!webhookStatus.isEnabled()) {
            return TelegramWebhookStatusClient.unavailable(botName, "lookup disabled", expectedUrl);
        }
        Map<String, String> apiTokens = webhookStatus.getApiTokens();
        String apiToken = apiTokens == null ? null : apiTokens.get(botName);
        if (apiToken == null || apiToken.isBlank()) {
            return TelegramWebhookStatusClient.unavailable(botName, "API token not configured", expectedUrl);
        }
        return webhookStatusClient.resolve(botName, apiToken, expectedUrl, webhookStatus);
    }

    private ChatbotsPanelConfiguration.WebhookStatus webhookStatusConfiguration(String botName) {
        ChatbotsPanelConfiguration.WebhookStatus webhookStatus = new ChatbotsPanelConfiguration.WebhookStatus();
        webhookStatus.setEnabled(environment.getProperty(WEBHOOK_STATUS_PREFIX + ".enabled", Boolean.class).orElse(false));
        webhookStatus.setBaseUrl(environment.getProperty(WEBHOOK_STATUS_PREFIX + ".base-url", String.class)
            .orElse(ChatbotsPanelConfiguration.WebhookStatus.DEFAULT_BASE_URL));
        webhookStatus.setTimeoutMillis(environment.getProperty(WEBHOOK_STATUS_PREFIX + ".timeout-millis", Integer.class)
            .orElse(ChatbotsPanelConfiguration.WebhookStatus.DEFAULT_TIMEOUT_MILLIS));
        environment.getProperty(WEBHOOK_STATUS_PREFIX + ".api-tokens." + botName, String.class)
            .ifPresent(token -> webhookStatus.setApiTokens(Map.of(botName, token)));
        return webhookStatus;
    }

    private String expectedUrl(String endpointPath) {
        return configuration.getPublicUrl()
            .filter(url -> !url.isBlank())
            .map(url -> trimTrailingSlash(url) + endpointPath)
            .orElse("");
    }

    private static String setupCommand(String expectedUrl, String endpointPath) {
        String url = expectedUrl == null || expectedUrl.isBlank() ? "https://example.test" + normalizePath(endpointPath) : expectedUrl;
        return "curl -X POST \"https://api.telegram.org/bot" + API_TOKEN_PLACEHOLDER + "/setWebhook\" "
            + "-d \"url=" + url + "\" "
            + "-d \"secret_token=" + SECRET_PLACEHOLDER + "\"";
    }

    private static String tokenStatus(String token) {
        return token == null || token.isBlank() ? "missing" : "configured";
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_PATH;
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
