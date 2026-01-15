/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlPanelModuleConfigurationTest {

    private ApplicationContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) ctx.stop();
    }

    @Test
    void itIsEnabledByDefaultInEnvironmentDev() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments(Environment.DEVELOPMENT)
            .start();
        assertFalse(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }

    @Test
    void itIsEnabledByDefaultInEnvironmentTest() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments(Environment.TEST)
            .start();
        assertFalse(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }

    @Test
    void itCanBeDisabled() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments(Environment.TEST)
            .properties(java.util.Map.of(ControlPanelModuleConfiguration.PROPERTY_ENABLED, false))
            .start();
        assertTrue(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }

    @Test
    void itIsDisabledInNonAllowedEnvironment() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("prod")
            .start();
        assertTrue(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }

    @Test
    void anEnvironmentCanBeAllowed() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("prod")
            .properties(java.util.Map.of(ControlPanelModuleConfiguration.PROPERTY_ALLOWED_ENVIRONMENTS, "prod"))
            .start();
        assertFalse(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }

    @Test
    void itIsDisabledWhenNoEnvironmentActivated() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .start();
        assertTrue(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }

    @Test
    void itIsDisabledWhenActiveEnvsDontMatchAllowed() {
        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("prod")
            .properties(java.util.Map.of(ControlPanelModuleConfiguration.PROPERTY_ALLOWED_ENVIRONMENTS, "staging"))
            .start();
        assertTrue(ctx.getBeansOfType(ControlPanel.class).isEmpty());
    }
}
