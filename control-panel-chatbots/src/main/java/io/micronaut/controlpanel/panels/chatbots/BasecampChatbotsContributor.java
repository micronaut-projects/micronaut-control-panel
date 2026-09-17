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

import io.micronaut.chatbots.basecamp.core.BasecampBotConfiguration;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.web.router.Router;
import jakarta.inject.Singleton;

@Singleton
@Requires(classes = BasecampBotConfiguration.class)
final class BasecampChatbotsContributor implements ChatbotsContributor {

    private static final String CHANNEL = "Basecamp";
    private static final String CONTROLLER_CLASS = "io.micronaut.chatbots.basecamp.http.BasecampController";
    private static final String ENDPOINT_PREFIX = "micronaut.chatbots.basecamp.endpoint";
    private static final String DEFAULT_PATH = "/basecamp";

    private final BeanContext beanContext;
    private final Environment environment;
    private final Router router;

    BasecampChatbotsContributor(BeanContext beanContext,
                                Environment environment,
                                Router router) {
        this.beanContext = beanContext;
        this.environment = environment;
        this.router = router;
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
        for (BeanRegistration<BasecampBotConfiguration> registration : beanContext.getBeanRegistrations(BasecampBotConfiguration.class)) {
            BasecampBotConfiguration bot = registration.getBean();
            diagnostics.bots.add(new ChatbotsControlPanel.BotRow(
                CHANNEL,
                bot.getName(),
                "-",
                bot.isEnabled(),
                "not applicable"
            ));
            diagnostics.setupHints.add(new ChatbotsControlPanel.SetupHint(
                CHANNEL,
                "command_url for " + bot.getName(),
                commandUrl(endpoint.path())
            ));
        }
    }

    private static String commandUrl(String path) {
        return "Configure the Basecamp chat integration command_url as https://example.test" + path;
    }
}
