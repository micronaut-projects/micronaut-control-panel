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
package io.micronaut.controlpanel.core.config;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.scan.ClassPathResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.function.Function;

/**
 * A {@link ApplicationContextConfigurer} that adds a {@link io.micronaut.context.annotation.PropertySource}
 * to the application context with all the control panel configuration properties.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@ContextConfigurer
public class ControlPanelContextConfigurer implements ApplicationContextConfigurer {

    public static final String PROPERTY_SOURCE_NAME = "micronaut-control-panel";
    public static final String CONTROL_PANEL_PROPERTIES = PROPERTY_SOURCE_NAME + "/" + PROPERTY_SOURCE_NAME + ".properties";

    @Override
    public void configure(@NonNull ApplicationContextBuilder builder) {
        var loader = ClassPathResourceLoader.defaultLoader(getClass().getClassLoader());
        Properties result = loader.getResources(CONTROL_PANEL_PROPERTIES)
            .map(urlToStream())
            .map(streamToProperties())
            .reduce((properties, accumulator) -> {
                accumulator.putAll(properties);
                return accumulator;
            }).orElse(new Properties());
        var propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, result) {
            @Override
            public int getOrder() {
                return SystemPropertiesPropertySource.POSITION + 50;
            }
        };

        builder.propertySources(propertySource);

    }

    private static Function<InputStream, Properties> streamToProperties() {
        return inputStream -> {
            Properties properties = new Properties();
            try {
                properties.load(inputStream);
            } catch (IOException e) {
                // ignore
            }
            return properties;
        };
    }

    private static Function<URL, InputStream> urlToStream() {
        return url -> {
            try {
                return url.openStream();
            } catch (IOException e) {
                return InputStream.nullInputStream();
            }
        };
    }
}
