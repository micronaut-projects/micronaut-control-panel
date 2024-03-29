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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.Set;


/**
 * Condition that checks if the control panel module should be enabled.
 */
@Internal
public class ControlPanelEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        var configuration = context.findBean(ControlPanelModuleConfiguration.class);
        var environment = context.getBean(Environment.class);
        var allowedEnvironments = configuration
            .map(ControlPanelModuleConfiguration::getAllowedEnvironments)
            .orElse(Set.of(ControlPanelModuleConfiguration.DEFAULT_ALLOWED_ENVIRONMENTS.split(",")));
        var enabled = configuration
            .map(ControlPanelModuleConfiguration::isEnabled)
            .orElse(Boolean.valueOf(ControlPanelModuleConfiguration.DEFAULT_ENABLED));
        if (!Collections.disjoint(allowedEnvironments, environment.getActiveNames())) {
            return enabled;
        } else {
            return false;
        }
    }

}
