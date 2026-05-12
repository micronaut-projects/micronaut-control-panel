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
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Optional;

@Singleton
@Requires(classes = Handler.class)
final class HandlerChatbotsContributor implements ChatbotsContributor {

    private static final String TELEGRAM_HANDLER = "io.micronaut.chatbots.telegram.core.TelegramHandler";
    private static final String BASECAMP_HANDLER = "io.micronaut.chatbots.basecamp.core.BasecampHandler";

    private final BeanContext beanContext;
    private final Optional<Class<?>> telegramHandlerType;
    private final Optional<Class<?>> basecampHandlerType;

    HandlerChatbotsContributor(BeanContext beanContext) {
        this.beanContext = beanContext;
        ClassLoader classLoader = beanContext.getClassLoader();
        this.telegramHandlerType = ClassUtils.forName(TELEGRAM_HANDLER, classLoader);
        this.basecampHandlerType = ClassUtils.forName(BASECAMP_HANDLER, classLoader);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void contribute(ChatbotsDiagnostics diagnostics) {
        Collection<BeanRegistration<Handler>> registrations = beanContext.getBeanRegistrations(Handler.class);
        for (BeanRegistration<Handler> registration : registrations) {
            Handler handler = registration.getBean();
            BeanDefinition<Handler> definition = registration.getBeanDefinition();
            diagnostics.handlers.add(new ChatbotsControlPanel.HandlerRow(
                registration.getIdentifier().getName(),
                handler.getClass().getName(),
                channel(handler),
                handler instanceof Ordered ordered ? ordered.getOrder() : Ordered.LOWEST_PRECEDENCE,
                outputType(definition),
                sourceInterface(handler)
            ));
        }
    }

    private String channel(Handler<?, ?, ?> handler) {
        if (telegramHandlerType.filter(type -> type.isInstance(handler)).isPresent()) {
            return "Telegram";
        }
        if (basecampHandlerType.filter(type -> type.isInstance(handler)).isPresent()) {
            return "Basecamp";
        }
        return "Generic";
    }

    private String sourceInterface(Handler<?, ?, ?> handler) {
        if (telegramHandlerType.filter(type -> type.isInstance(handler)).isPresent()) {
            return "TelegramHandler";
        }
        if (basecampHandlerType.filter(type -> type.isInstance(handler)).isPresent()) {
            return "BasecampHandler";
        }
        return "Handler";
    }

    private static String outputType(BeanDefinition<?> definition) {
        return definition.getTypeArguments(Handler.class).stream()
            .skip(2)
            .findFirst()
            .map(Argument::getSimpleName)
            .orElse("unknown");
    }
}
