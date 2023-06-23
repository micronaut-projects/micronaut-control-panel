/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.controlpanel.ui.util;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.refresh.RefreshEndpoint;
import io.micronaut.management.endpoint.stop.ServerStopEndpoint;

import java.util.Optional;

/**
 * Utility methods for {@link io.micronaut.management.endpoint.annotation.Endpoint}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Internal
public final class EndpointUtils {

    private EndpointUtils() {
    }

    /**
     * Determines if the control panel can call the refresh endpoint.
     *
     * @param endpoint the endpoint
     * @param beanContext the bean context
     * @return true if the endpoint can be called, false otherwise
     */
    public static boolean canRefresh(@Nullable RefreshEndpoint endpoint, BeanContext beanContext) {
        return endpoint != null && !isSensitive(endpoint, beanContext);
    }

    /**
     * Determines if the control panel can call the stop endpoint.
     *
     * @param endpoint the endpoint
     * @param beanContext the bean context
     * @return true if the endpoint can be called, false otherwise
     */
    public static boolean canStop(@Nullable ServerStopEndpoint endpoint, BeanContext beanContext) {
        return endpoint != null && !isSensitive(endpoint, beanContext);
    }

    /**
     * Determines if an endpoint is sensitive or not.
     *
     * @param endpoint the endpoint
     * @param beanContext the bean context
     * @return true if the endpoint is sensitive, false otherwise
     */
    static boolean isSensitive(Object endpoint, BeanContext beanContext) {
        AnnotationMetadata annotationMetadata = beanContext.resolveMetadata(endpoint);
        Boolean defaultSensitive = annotationMetadata.booleanValue(Endpoint.class, "defaultSensitive").orElse(true);
        String prefix = annotationMetadata.stringValue(Endpoint.class, "prefix").orElse(Endpoint.DEFAULT_PREFIX);
        String id = annotationMetadata.stringValue(Endpoint.class).orElse(null);
        String defaultId = annotationMetadata.stringValue(Endpoint.class, "defaultConfigurationId").orElse("all");

        if (beanContext instanceof PropertyResolver resolver) {
            Optional<Boolean> sensitive = resolver.getProperty(String.format("%s.%s.sensitive", prefix, id), Boolean.class);
            if (sensitive.isPresent()) {
                return sensitive.get();
            } else {
                sensitive = resolver.getProperty(String.format("%s.%s.sensitive", prefix, defaultId), Boolean.class);
                return sensitive.orElse(defaultSensitive);
            }
        } else {
            return defaultSensitive;
        }
    }

}
