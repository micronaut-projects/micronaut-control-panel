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
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Comparator;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;
import static io.micronaut.controlpanel.panels.management.beans.BeansControlPanel.BEANS_CATEGORY;

/**
 * A control panel that displays information about disabled beans in the application context.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
@Requires(property = DisabledBeansControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class DisabledBeansControlPanel extends AbstractControlPanel<DisabledBeansControlPanel.Body> {

    public static final String NAME = "disabled-beans";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final Body body;

    protected DisabledBeansControlPanel(@Named(NAME) ControlPanelConfiguration configuration, BeanContext beanContext) {
        super(NAME, configuration);
        var disabledBeans = beanContext.getDisabledBeans().stream().sorted(Comparator.comparing(BeanDefinition::getName)).toList();
        this.body = new Body(disabledBeans);
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public Category getCategory() {
        return BEANS_CATEGORY;
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    /**
     * The body of this control panel, containing a collection of disabled beans.
     *
     * @param disabledBeans the collection of disabled beans
     */
    @ReflectiveAccess
    @TypeHint(value = { DisabledBean.class }, accessType = TypeHint.AccessType.ALL_PUBLIC)
    public record Body(Collection<DisabledBean<?>> disabledBeans) { }
}
