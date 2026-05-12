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

import com.sun.net.httpserver.HttpServer;
import io.micronaut.chatbots.basecamp.api.Query;
import io.micronaut.chatbots.basecamp.core.BasecampBotConfiguration;
import io.micronaut.chatbots.basecamp.core.BasecampHandler;
import io.micronaut.chatbots.core.BotConfiguration;
import io.micronaut.chatbots.core.Handler;
import io.micronaut.chatbots.telegram.api.Update;
import io.micronaut.chatbots.telegram.core.TelegramBotConfiguration;
import io.micronaut.chatbots.telegram.core.TelegramHandler;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class ChatbotsControlPanelTest {

    private static final String TELEGRAM_SECRET = "telegram-secret-token-value";
    private static final String BOT_API_TOKEN = "123456:bot-api-token-value";

    @Test
    void panelRendersConfiguredBotsEndpointsHandlersAndRedactsSecrets() {
        try (ApplicationContext context = ApplicationContext.run(properties())) {
            ChatbotsControlPanel.Body body = context.getBean(ChatbotsControlPanel.class).getBody();

            Assertions.assertEquals(2, body.summary().totalBots());
            Assertions.assertEquals(3, body.summary().handlerCount());
            Assertions.assertTrue(body.bots().stream().anyMatch(bot ->
                bot.channel().equals("Telegram")
                    && bot.name().equals("support")
                    && bot.username().equals("@SupportExampleBot")
                    && bot.enabled()
                    && bot.tokenStatus().equals("configured")));
            Assertions.assertTrue(body.bots().stream().anyMatch(bot ->
                bot.channel().equals("Basecamp")
                    && bot.name().equals("ops")
                    && bot.enabled()
                    && bot.tokenStatus().equals("not applicable")));
            Assertions.assertTrue(body.endpoints().stream().anyMatch(endpoint ->
                endpoint.channel().equals("Telegram")
                    && endpoint.path().equals("/custom-telegram")
                    && endpoint.configuredEnabled()));
            Assertions.assertTrue(body.handlers().stream().anyMatch(handler ->
                handler.channel().equals("Telegram")
                    && handler.order() == -5));
            Assertions.assertTrue(body.handlers().stream().anyMatch(handler ->
                handler.channel().equals("Basecamp")
                    && handler.order() == 10));
            Assertions.assertFalse(body.toString().contains(TELEGRAM_SECRET));
            Assertions.assertFalse(body.toString().contains(BOT_API_TOKEN));
            Assertions.assertTrue(body.setupHints().stream().anyMatch(hint -> hint.command().contains("<BOT_API_TOKEN>")));
            Assertions.assertFalse(SupportTelegramHandler.canHandleCalled.get());
            Assertions.assertFalse(SupportTelegramHandler.handleCalled.get());
        }
    }

    @Test
    void disabledPanelDoesNotRegister() {
        Map<String, Object> properties = properties();
        properties.put("micronaut.control-panel.panels.chatbots.enabled", false);
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            Assertions.assertFalse(context.containsBean(ChatbotsControlPanel.class));
        }
    }

    @Test
    void webhookLookupIsOptInAndDegradesPerBot() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/bot" + BOT_API_TOKEN + "/getWebhookInfo", exchange -> {
            byte[] bytes = """
                {"ok":true,"result":{"url":"https://dev.example.test/custom-telegram","pending_update_count":0,"max_connections":40,"allowed_updates":["message"],"has_custom_certificate":false}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            Map<String, Object> properties = properties();
            properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.enabled", true);
            properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.base-url", "http://localhost:" + server.getAddress().getPort());
            properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.api-tokens.support", BOT_API_TOKEN);
            try (ApplicationContext context = ApplicationContext.run(properties)) {
                ChatbotsControlPanel.Body body = context.getBean(ChatbotsControlPanel.class).getBody();
                Optional<ChatbotsControlPanel.WebhookRow> webhook = body.webhooks().stream()
                    .filter(row -> row.botName().equals("support"))
                    .findFirst();

                Assertions.assertTrue(webhook.isPresent());
                Assertions.assertTrue(webhook.get().available(), webhook.get()::toString);
                Assertions.assertEquals("matches expected URL", webhook.get().matchStatus());
                Assertions.assertEquals("0", webhook.get().pendingUpdates());
                Assertions.assertFalse(webhook.get().toString().contains(BOT_API_TOKEN));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webhookLookupFailureDoesNotFailPanel() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/bot" + BOT_API_TOKEN + "/getWebhookInfo", exchange -> {
            byte[] bytes = """
                {"ok":false,"description":"Unauthorized"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            Map<String, Object> properties = properties();
            properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.enabled", true);
            properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.base-url", "http://localhost:" + server.getAddress().getPort());
            properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.api-tokens.support", BOT_API_TOKEN);
            try (ApplicationContext context = ApplicationContext.run(properties)) {
                ChatbotsControlPanel.Body body = context.getBean(ChatbotsControlPanel.class).getBody();
                Optional<ChatbotsControlPanel.WebhookRow> webhook = body.webhooks().stream()
                    .filter(row -> row.botName().equals("support"))
                    .findFirst();

                Assertions.assertTrue(webhook.isPresent());
                Assertions.assertFalse(webhook.get().available(), webhook.get()::toString);
                Assertions.assertEquals("invalid API token", webhook.get().state());
                Assertions.assertEquals(2, body.summary().totalBots());
                Assertions.assertFalse(body.toString().contains(BOT_API_TOKEN));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidWebhookLookupConfigurationDoesNotExposeToken() {
        Map<String, Object> properties = properties();
        properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.enabled", true);
        properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.base-url", "http://[invalid");
        properties.put("micronaut.control-panel.chatbots.telegram.webhook-status.api-tokens.support", BOT_API_TOKEN);
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            ChatbotsControlPanel.Body body = context.getBean(ChatbotsControlPanel.class).getBody();
            Optional<ChatbotsControlPanel.WebhookRow> webhook = body.webhooks().stream()
                .filter(row -> row.botName().equals("support"))
                .findFirst();

            Assertions.assertTrue(webhook.isPresent());
            Assertions.assertFalse(webhook.get().available(), webhook.get()::toString);
            Assertions.assertEquals("lookup configuration error", webhook.get().state());
            Assertions.assertEquals(2, body.summary().totalBots());
            Assertions.assertFalse(body.toString().contains(BOT_API_TOKEN));
        }
    }

    private static Map<String, Object> properties() {
        return new LinkedHashMap<>(Map.ofEntries(
            Map.entry("micronaut.application.name", "chatbots-test"),
            Map.entry("micronaut.control-panel.chatbots.public-url", "https://dev.example.test"),
            Map.entry("micronaut.chatbots.telegram.endpoint.path", "/custom-telegram"),
            Map.entry("micronaut.chatbots.basecamp.endpoint.path", "/custom-basecamp"),
            Map.entry("micronaut.chatbots.telegram.bots.support.token", TELEGRAM_SECRET),
            Map.entry("micronaut.chatbots.telegram.bots.support.at-username", "@SupportExampleBot"),
            Map.entry("micronaut.chatbots.telegram.bots.support.enabled", true),
            Map.entry("micronaut.chatbots.basecamp.bots.ops.enabled", true)
        ));
    }

    @Singleton
    @Named("supportTelegramHandler")
    static final class SupportTelegramHandler implements TelegramHandler<String> {
        static final AtomicBoolean canHandleCalled = new AtomicBoolean();
        static final AtomicBoolean handleCalled = new AtomicBoolean();

        @Override
        public int getOrder() {
            return -5;
        }

        @Override
        public boolean canHandle(TelegramBotConfiguration bot, Update input) {
            canHandleCalled.set(true);
            return false;
        }

        @Override
        public Optional<String> handle(TelegramBotConfiguration bot, Update input) {
            handleCalled.set(true);
            return Optional.empty();
        }
    }

    @Singleton
    @Named("opsBasecampHandler")
    static final class OpsBasecampHandler implements BasecampHandler {
        @Override
        public int getOrder() {
            return 10;
        }

        @Override
        public boolean canHandle(BasecampBotConfiguration bot, Query input) {
            return false;
        }

        @Override
        public Optional<String> handle(BasecampBotConfiguration bot, Query input) {
            return Optional.empty();
        }
    }

    @Singleton
    @Named("genericChatbotsHandler")
    static final class GenericChatbotsHandler implements Handler<BotConfiguration, Object, Object> {
        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public boolean canHandle(BotConfiguration bot, Object input) {
            return false;
        }

        @Override
        public Optional<Object> handle(BotConfiguration bot, Object input) {
            return Optional.empty();
        }
    }
}
