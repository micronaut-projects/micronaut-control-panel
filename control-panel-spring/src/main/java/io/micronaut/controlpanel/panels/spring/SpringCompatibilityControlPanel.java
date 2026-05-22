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
package io.micronaut.controlpanel.panels.spring;

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Control panel for Micronaut Spring compatibility metadata.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Singleton
public class SpringCompatibilityControlPanel extends AbstractControlPanel<SpringCompatibilityBody> {

    public static final String NAME = "spring-compatibility";
    public static final String ENABLED_PROPERTY_PREFIX = ControlPanelConfiguration.PREFIX + "." + NAME;
    public static final String ENABLED_PROPERTY = ENABLED_PROPERTY_PREFIX + ".enabled";

    private static final Category CATEGORY = new Category("spring", "Spring", "si si-spring");

    private final SpringCompatibilityDiagnostics diagnostics;

    /**
     * @param configuration control panel configuration
     * @param diagnostics diagnostics service
     */
    public SpringCompatibilityControlPanel(@Named(NAME) ControlPanelConfiguration configuration,
                                           SpringCompatibilityDiagnostics diagnostics) {
        super(NAME, configuration);
        this.diagnostics = diagnostics;
    }

    @Override
    public SpringCompatibilityBody getBody() {
        return diagnostics.build();
    }

    @Override
    public String getBadge() {
        SpringCompatibilityBody body = getBody();
        return Integer.toString(body.beanCount() + body.routeCount() + body.endpointCount());
    }

    @Override
    public Category getCategory() {
        return CATEGORY;
    }
}
