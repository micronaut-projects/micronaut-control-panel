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

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Singleton
final class TelegramWebhookStatusClient {

    private static final Argument<Map<String, Object>> MAP_ARGUMENT = Argument.mapOf(String.class, Object.class);

    private final JsonMapper jsonMapper;
    private final HttpClient client;

    TelegramWebhookStatusClient(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.client = HttpClient.newHttpClient();
    }

    ChatbotsControlPanel.WebhookRow resolve(String botName,
                                            String apiToken,
                                            String expectedUrl,
                                            ChatbotsPanelConfiguration.WebhookStatus configuration) {
        HttpRequest request;
        try {
            Duration timeout = timeout(configuration);
            URI uri = URI.create(trimTrailingSlash(configuration.getBaseUrl()) + "/bot" + apiToken + "/getWebhookInfo");
            request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .GET()
                .build();
        } catch (RuntimeException e) {
            return unavailable(botName, "lookup configuration error", expectedUrl);
        }
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return unavailable(botName, "invalid API token", expectedUrl);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable(botName, "Telegram API returned HTTP " + response.statusCode(), expectedUrl);
            }
            return fromBody(botName, expectedUrl, response.body());
        } catch (IOException e) {
            return unavailable(botName, "network error", expectedUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable(botName, "lookup interrupted", expectedUrl);
        } catch (RuntimeException e) {
            return unavailable(botName, "parse error", expectedUrl);
        }
    }

    @SuppressWarnings("unchecked")
    private ChatbotsControlPanel.WebhookRow fromBody(String botName, String expectedUrl, String body) throws IOException {
        Map<String, Object> decoded = jsonMapper.readValue(body, MAP_ARGUMENT);
        Object ok = decoded.get("ok");
        if (Boolean.FALSE.equals(ok)) {
            return unavailable(botName, "Telegram API error", expectedUrl);
        }
        Object result = decoded.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            return unavailable(botName, "parse error", expectedUrl);
        }
        Map<String, Object> values = (Map<String, Object>) resultMap;
        String returnedUrl = string(values.get("url"));
        return new ChatbotsControlPanel.WebhookRow(
            botName,
            "available",
            true,
            expectedUrl,
            returnedUrl,
            matchStatus(expectedUrl, returnedUrl),
            string(values.get("pending_update_count")),
            lastError(values),
            string(values.get("max_connections")),
            allowedUpdates(values.get("allowed_updates")),
            Boolean.TRUE.equals(values.get("has_custom_certificate")) ? "present" : "not reported"
        );
    }

    static ChatbotsControlPanel.WebhookRow unavailable(String botName, String state, String expectedUrl) {
        return new ChatbotsControlPanel.WebhookRow(botName, state, false, expectedUrl, "-", "-", "-", "-", "-", "-", "-");
    }

    private static Duration timeout(ChatbotsPanelConfiguration.WebhookStatus configuration) {
        return Duration.ofMillis(Math.max(1, configuration.getTimeoutMillis()));
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String string(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String matchStatus(String expectedUrl, String returnedUrl) {
        if (expectedUrl == null || expectedUrl.isBlank()) {
            return "expected URL not configured";
        }
        if (returnedUrl == null || returnedUrl.isBlank() || "-".equals(returnedUrl)) {
            return "not configured";
        }
        return expectedUrl.equals(returnedUrl) ? "matches expected URL" : "configured but points elsewhere";
    }

    private static String lastError(Map<String, Object> values) {
        String message = string(values.get("last_error_message"));
        String date = string(values.get("last_error_date"));
        if ("-".equals(message) && "-".equals(date)) {
            return "none";
        }
        if ("-".equals(date)) {
            return message;
        }
        if ("-".equals(message)) {
            return date;
        }
        return date + ": " + message;
    }

    private static String allowedUpdates(Object value) {
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "all update types" : String.join(", ", list.stream().map(String::valueOf).toList());
        }
        return string(value);
    }
}
