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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateBody;
import jakarta.inject.Named;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel for Hibernate runtime metadata and caches.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@EachBean(HibernateRuntimeService.class)
public class HibernateControlPanel extends AbstractEachBeanControlPanel<HibernateBody> {

    public static final String NAME = "hibernate";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final String DEFAULT_ICON_CLASS = "fas fa-layer-group";

    private final String beanName;
    private final HibernateRuntimeService runtimeService;

    /**
     * Constructor.
     *
     * @param beanName the session factory bean name
     * @param runtimeService the runtime service for the session factory
     * @param configuration the control panel configuration
     */
    public HibernateControlPanel(@Parameter String beanName,
                                 @Parameter HibernateRuntimeService runtimeService,
                                 @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.runtimeService = runtimeService;
    }

    @Override
    protected String getBeanName() {
        return beanName;
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public HibernateBody getBody() {
        return runtimeService.getBody();
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getIcon() {
        return "fa-cubes";
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Hibernate", DEFAULT_ICON_CLASS);
    }
}
