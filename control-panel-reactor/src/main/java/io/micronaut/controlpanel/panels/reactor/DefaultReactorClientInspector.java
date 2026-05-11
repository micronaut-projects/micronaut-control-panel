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
package io.micronaut.controlpanel.panels.reactor;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorSseClient;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Singleton
@Requires(classes = ReactorHttpClient.class)
final class DefaultReactorClientInspector implements ReactorClientInspector {

    private static final String REDACTED_LABEL = "redacted";

    private final BeanContext beanContext;

    DefaultReactorClientInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<ReactorDiagnosticsBody.ReactorClient> clients() {
        return Stream.of(
                clientDefinitions(ReactorHttpClient.class, "ReactorHttpClient"),
                clientDefinitions(ReactorSseClient.class, "ReactorSseClient"),
                clientDefinitions(ReactorStreamingHttpClient.class, "ReactorStreamingHttpClient")
            )
            .flatMap(stream -> stream)
            .distinct()
            .sorted(Comparator.comparing(ReactorDiagnosticsBody.ReactorClient::beanName)
                .thenComparing(ReactorDiagnosticsBody.ReactorClient::type))
            .toList();
    }

    private <T> Stream<ReactorDiagnosticsBody.ReactorClient> clientDefinitions(Class<T> beanType, String exposedType) {
        return beanContext.getBeanDefinitions(beanType)
            .stream()
            .filter(definition -> !definition.hasStereotype(Secondary.class))
            .map(definition -> toClient(definition, exposedType));
    }

    private static ReactorDiagnosticsBody.ReactorClient toClient(BeanDefinition<?> definition, String exposedType) {
        String beanName = safeLabel(definition.stringValue(Named.class).orElse(definition.getName()));
        return new ReactorDiagnosticsBody.ReactorClient(beanName, exposedType, "");
    }

    private static String safeLabel(String value) {
        if (value.isBlank()) {
            return REDACTED_LABEL;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("://")
            || normalized.contains("@")
            || normalized.contains("@client")
            || normalized.contains("authorization")
            || normalized.contains("bearer ")
            || normalized.contains("cookie")
            || normalized.contains("credential")
            || normalized.contains("password")
            || normalized.contains("passwd")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("api-key")
            || normalized.contains("apikey")) {
            return REDACTED_LABEL;
        }
        return value;
    }
}
