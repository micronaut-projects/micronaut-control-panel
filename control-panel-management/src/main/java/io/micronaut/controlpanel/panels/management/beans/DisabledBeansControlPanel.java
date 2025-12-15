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
package io.micronaut.controlpanel.panels.management.beans;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DisabledBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Comparator;

/**
 * A control panel that displays information about disabled beans in the application context.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
@Refreshable
@Requires(property = DisabledBeansControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class DisabledBeansControlPanel extends AbstractControlPanel<DisabledBeansControlPanel.Body> {

    public static final String NAME = "disabled-beans";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private static final Comparator<Object> COMPARATOR_BY_NAME = Comparator.comparing(bd -> bd.getClass().getName());

    private final Body body;
    private final long beanDefinitionsCount;

    protected DisabledBeansControlPanel(@Named(NAME) ControlPanelConfiguration configuration, BeanContext beanContext) {
        super(NAME, configuration);
        var disabledBeans = beanContext.getDisabledBeans().stream().sorted(Comparator.comparing(BeanDefinition::getName)).toList();
        this.body = new Body(disabledBeans);
        this.beanDefinitionsCount = disabledBeans.size();
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public Category getCategory() {
        return new Category(BeansControlPanel.NAME, "Beans", "fa-plug");
    }

    @Override
    public String getBadge() {
        return String.valueOf(beanDefinitionsCount);
    }

    /**
     * The body of this control panel, containing a collection of disabled beans.
     *
     * @param disabledBeans the collection of disabled beans
     */
    @ReflectiveAccess
    public record Body(Collection<DisabledBean<?>> disabledBeans) { }
}
