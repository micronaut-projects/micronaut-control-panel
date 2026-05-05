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
package io.micronaut.controlpanel.core.security;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlPanelSecurityConfigurationTest {

    @Test
    void defaultsToAuthorizedAccessWithDefaultRole() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ControlPanelSecurityConfiguration configuration = context.getBean(ControlPanelSecurityConfiguration.class);

            assertEquals(ControlPanelSecurityConfiguration.Access.AUTHORIZED, configuration.access());
            assertEquals(ControlPanelSecurityConfiguration.DEFAULT_ROLE, configuration.role());
        }
    }

    @Test
    void bindsAnonymousAccess() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS"
        ))) {
            ControlPanelSecurityConfiguration configuration = context.getBean(ControlPanelSecurityConfiguration.class);

            assertEquals(ControlPanelSecurityConfiguration.Access.ANONYMOUS, configuration.access());
        }
    }

    @Test
    void bindsAuthenticatedAccess() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "AUTHENTICATED"
        ))) {
            ControlPanelSecurityConfiguration configuration = context.getBean(ControlPanelSecurityConfiguration.class);

            assertEquals(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, configuration.access());
        }
    }

    @Test
    void bindsAuthorizedAccessAndCustomRole() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "AUTHORIZED",
            ControlPanelSecurityConfiguration.PROPERTY_ROLE, "ROLE_ADMIN"
        ))) {
            ControlPanelSecurityConfiguration configuration = context.getBean(ControlPanelSecurityConfiguration.class);

            assertEquals(ControlPanelSecurityConfiguration.Access.AUTHORIZED, configuration.access());
            assertEquals("ROLE_ADMIN", configuration.role());
        }
    }

    @Test
    void rejectsBlankRoleWhenAuthorizedAccessIsEnabled() {
        assertThrows(IllegalArgumentException.class, () -> new ControlPanelSecurityConfiguration(
            ControlPanelSecurityConfiguration.Access.AUTHORIZED,
            " "
        ));
    }
}
