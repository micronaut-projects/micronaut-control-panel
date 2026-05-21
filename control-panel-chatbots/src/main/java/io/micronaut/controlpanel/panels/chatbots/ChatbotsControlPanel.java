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
@SuppressWarnings("InjectMoreThanOneScopeAnnotationOnClass")
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
        ChatbotsDiagnostics diagnostics = diagnostics(false);
        return diagnostics.bots.size() + " bots / " + diagnostics.handlers.size() + " handlers";
    }

    @Override
    public Body getBody() {
        ChatbotsDiagnostics diagnostics = diagnostics(true);
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

    private ChatbotsDiagnostics diagnostics(boolean includeWebhookLookup) {
        ChatbotsDiagnostics diagnostics = new ChatbotsDiagnostics(includeWebhookLookup);
        contributors.forEach(contributor -> contributor.contribute(diagnostics));
        return diagnostics;
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

    /**
     * Body returned by the Chatbots control panel.
     *
     * @param summary    summary counts
     * @param bots       configured bot rows
     * @param endpoints  endpoint rows
     * @param handlers   handler rows
     * @param webhooks   Telegram webhook status rows
     * @param setupHints setup command hints
     */
    @ReflectiveAccess
    public record Body(Summary summary,
                       List<BotRow> bots,
                       List<EndpointRow> endpoints,
                       List<HandlerRow> handlers,
                       List<WebhookRow> webhooks,
                       List<SetupHint> setupHints) { }

    /**
     * Summary counts for the Chatbots control panel.
     *
     * @param totalBots               total configured bots
     * @param enabledBots             total enabled bots
     * @param endpointCount           total inspected endpoints
     * @param registeredEndpointCount total registered endpoints
     * @param handlerCount            total discovered handlers
     * @param webhookLookupState      webhook lookup summary state
     */
    @ReflectiveAccess
    public record Summary(int totalBots,
                          int enabledBots,
                          int endpointCount,
                          int registeredEndpointCount,
                          int handlerCount,
                          String webhookLookupState) { }

    /**
     * Configured bot row for the Chatbots control panel.
     *
     * @param channel     bot channel
     * @param name        bot name
     * @param username    bot username
     * @param enabled     whether the bot is enabled
     * @param tokenStatus redacted token status
     */
    @ReflectiveAccess
    public record BotRow(String channel,
                         String name,
                         String username,
                         boolean enabled,
                         String tokenStatus) { }

    /**
     * HTTP endpoint row for the Chatbots control panel.
     *
     * @param channel           endpoint channel
     * @param modulePresent     whether the HTTP module is present
     * @param configuredEnabled whether the endpoint is enabled by configuration
     * @param routeRegistered   whether the endpoint route is registered
     * @param path              effective endpoint path
     * @param state             endpoint state label
     * @param source            endpoint path source
     */
    @ReflectiveAccess
    public record EndpointRow(String channel,
                              boolean modulePresent,
                              boolean configuredEnabled,
                              boolean routeRegistered,
                              String path,
                              String state,
                              String source) { }

    /**
     * Handler bean row for the Chatbots control panel.
     *
     * @param beanName        bean name
     * @param className       handler class name
     * @param channel         handler channel
     * @param order           handler order
     * @param outputType      handler output type
     * @param sourceInterface handler source interface
     */
    @ReflectiveAccess
    public record HandlerRow(String beanName,
                             String className,
                             String channel,
                             int order,
                             String outputType,
                             String sourceInterface) { }

    /**
     * Telegram webhook status row for the Chatbots control panel.
     *
     * @param botName           bot name
     * @param state             webhook lookup state
     * @param available         whether webhook status is available
     * @param expectedUrl       expected webhook URL
     * @param returnedUrl       returned webhook URL
     * @param matchStatus       webhook URL match status
     * @param pendingUpdates    pending update count
     * @param lastError         last Telegram webhook error
     * @param maxConnections    configured maximum connections
     * @param allowedUpdates    allowed update types
     * @param customCertificate custom certificate status
     */
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

    /**
     * Setup hint row for the Chatbots control panel.
     *
     * @param channel setup channel
     * @param label   setup hint label
     * @param command setup command with placeholders
     */
    @ReflectiveAccess
    public record SetupHint(String channel,
                            String label,
                            String command) { }
}
