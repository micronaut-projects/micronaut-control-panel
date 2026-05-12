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

import io.micronaut.chatbots.core.Handler;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregate diagnostics panel for Micronaut Chatbots.
 *
 * @since 2.0.0
 */
@Singleton
@Refreshable
@Requires(classes = Handler.class)
@Requires(property = ChatbotsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ChatbotsControlPanel extends AbstractControlPanel<ChatbotsControlPanel.Body> {

    public static final String NAME = "chatbots";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "Chatbots", "fas fa-robot", 25);

    private final Collection<ChatbotsContributor> contributors;

    public ChatbotsControlPanel(Collection<ChatbotsContributor> contributors,
                                @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.contributors = contributors;
    }

    @Override
    public String getTitle() {
        return "Chatbots";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getBadge() {
        Body body = getBody();
        return body.summary().totalBots() + " bots / " + body.summary().handlerCount() + " handlers";
    }

    @Override
    public Body getBody() {
        ChatbotsDiagnostics diagnostics = new ChatbotsDiagnostics();
        contributors.forEach(contributor -> contributor.contribute(diagnostics));
        List<BotRow> bots = diagnostics.bots.stream()
            .sorted(Comparator.comparing(BotRow::channel).thenComparing(BotRow::name))
            .toList();
        List<EndpointRow> endpoints = diagnostics.endpoints.stream()
            .sorted(Comparator.comparing(EndpointRow::channel))
            .toList();
        List<HandlerRow> handlers = diagnostics.handlers.stream()
            .sorted(Comparator.comparingInt(HandlerRow::order).thenComparing(HandlerRow::beanName).thenComparing(HandlerRow::className))
            .toList();
        List<WebhookRow> webhooks = diagnostics.webhooks.stream()
            .sorted(Comparator.comparing(WebhookRow::botName))
            .toList();
        return new Body(new Summary(
            bots.size(),
            (int) bots.stream().filter(BotRow::enabled).count(),
            endpoints.size(),
            (int) endpoints.stream().filter(EndpointRow::routeRegistered).count(),
            handlers.size(),
            webhookSummary(webhooks)
        ), bots, endpoints, handlers, webhooks, diagnostics.setupHints);
    }

    private static String webhookSummary(List<WebhookRow> webhooks) {
        if (webhooks.isEmpty()) {
            return "No Telegram bots";
        }
        long available = webhooks.stream().filter(WebhookRow::available).count();
        if (available > 0) {
            return available + " available";
        }
        return webhooks.get(0).state();
    }

    @ReflectiveAccess
    public record Body(Summary summary,
                       List<BotRow> bots,
                       List<EndpointRow> endpoints,
                       List<HandlerRow> handlers,
                       List<WebhookRow> webhooks,
                       List<SetupHint> setupHints) { }

    @ReflectiveAccess
    public record Summary(int totalBots,
                          int enabledBots,
                          int endpointCount,
                          int registeredEndpointCount,
                          int handlerCount,
                          String webhookLookupState) { }

    @ReflectiveAccess
    public record BotRow(String channel,
                         String name,
                         String username,
                         boolean enabled,
                         String tokenStatus) { }

    @ReflectiveAccess
    public record EndpointRow(String channel,
                              boolean modulePresent,
                              boolean configuredEnabled,
                              boolean routeRegistered,
                              String path,
                              String state,
                              String source) { }

    @ReflectiveAccess
    public record HandlerRow(String beanName,
                             String className,
                             String channel,
                             int order,
                             String outputType,
                             String sourceInterface) { }

    @ReflectiveAccess
    public record WebhookRow(String botName,
                             String state,
                             boolean available,
                             String expectedUrl,
                             String returnedUrl,
                             String matchStatus,
                             String pendingUpdates,
                             String lastError,
                             String maxConnections,
                             String allowedUpdates,
                             String customCertificate) { }

    @ReflectiveAccess
    public record SetupHint(String channel,
                            String label,
                            String command) { }
}
