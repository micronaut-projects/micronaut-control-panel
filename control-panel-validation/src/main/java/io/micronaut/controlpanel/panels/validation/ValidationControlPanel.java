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
package io.micronaut.controlpanel.panels.validation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.validation.validator.ValidatorConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel that displays Micronaut Validation compile-time metadata and provider wiring.
 *
 * @since 2.0.0
 */
@Singleton
@Refreshable
@Requires(classes = ValidatorConfiguration.class)
@Requires(property = ValidationControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ValidationControlPanel extends AbstractControlPanel<ValidationDiagnostics> {

    public static final String NAME = "validation";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final ValidationDiagnosticsCollector collector;

    public ValidationControlPanel(ValidationDiagnosticsCollector collector,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.collector = collector;
    }

    @Override
    public ValidationDiagnostics getBody() {
        return collector.collect();
    }

    @Override
    public String getBadge() {
        int count = getBody().totalConstraints();
        return count == 0 ? EMPTY_STRING : String.valueOf(count);
    }
}
