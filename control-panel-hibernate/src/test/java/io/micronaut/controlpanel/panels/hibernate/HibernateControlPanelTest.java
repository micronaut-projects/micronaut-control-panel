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
package io.micronaut.controlpanel.panels.hibernate;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateBody;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateSessionFactoryInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateStatisticsInfo;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HibernateControlPanelTest {

    @Mock
    private HibernateRuntimeService runtimeService;

    @Mock
    private ControlPanelConfiguration configuration;

    @Test
    void contextCreatesPanelForEachRuntimeService() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(HibernateControlPanel.ENABLED_PROPERTY, true))) {
            var sessionFactory = mock(SessionFactory.class);
            context.registerSingleton(SessionFactory.class, sessionFactory, Qualifiers.byName("default"));

            var panels = context.getBeansOfType(HibernateControlPanel.class);

            assertEquals(1, panels.size());
            var panel = panels.iterator().next();
            assertEquals("default", panel.getTitle());
            assertEquals("hibernate-default", panel.getName());
        }
    }

    @Test
    void exposesSessionFactoryPanelMetadata() {
        var body = body();
        when(runtimeService.getBody()).thenReturn(body);

        var panel = new HibernateControlPanel("default", runtimeService, configuration);

        assertEquals("default", panel.getTitle());
        assertEquals("hibernate-default", panel.getName());
        assertEquals("hibernate", panel.getPanelName());
        assertEquals("Details", panel.getDetailLinkName());
        assertEquals("fa-cubes", panel.getIcon());
        assertEquals("", panel.getBadge());
        assertSame(body, panel.getBody());
        assertEquals("Hibernate", panel.getCategory().name());
    }

    private static HibernateBody body() {
        return new HibernateBody(
            new HibernateSessionFactoryInfo("default", "default", false, true, true, true, "", "", "", Map.of()),
            new HibernateStatisticsInfo(true, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }
}
