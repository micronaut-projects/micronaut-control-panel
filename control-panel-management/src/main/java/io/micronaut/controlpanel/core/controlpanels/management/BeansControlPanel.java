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
package io.micronaut.controlpanel.core.controlpanels.management;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.management.endpoint.beans.BeansEndpoint;
import io.micronaut.management.endpoint.beans.impl.DefaultBeanDefinitionData;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Control panel that displays information about the beans loaded in the application.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Refreshable
@Requires(beans = BeansEndpoint.class)
@Requires(property = BeansControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class BeansControlPanel extends AbstractControlPanel<BeansControlPanel.Body> {

    public static final String NAME = "beans";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private static final Function<BeanDefinition<?>, String> BY_PACKAGE =
        bd -> bd.getBeanType().getPackage().getName();

    private static final Function<BeanDefinition<?>, String> BY_MICRONAUT_PACKAGE =
        bd -> bd.getBeanType().getPackage().getName().replaceAll("io\\.micronaut\\.(\\w+).*", "$1");

    private static final Comparator<Object> COMPARATOR_BY_NAME = Comparator.comparing(bd -> bd.getClass().getName());

    private static final Predicate<BeanDefinition<?>> IS_MICRONAUT_PACKAGE =
        bd -> bd.getBeanType().getPackage().getName().startsWith("io.micronaut");

    private final Body body;

    private final long beanDefinitionsCount;

    public BeansControlPanel(BeanContext beanContext, DefaultBeanDefinitionData beanDefinitionData, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        var micronautBeansByPackage = computeBeansByPackage(beanContext, beanDefinitionData, IS_MICRONAUT_PACKAGE, BY_MICRONAUT_PACKAGE);
        var otherBeansByPackage = computeBeansByPackage(beanContext, beanDefinitionData, IS_MICRONAUT_PACKAGE.negate(), BY_PACKAGE);
        this.body = new Body(micronautBeansByPackage, otherBeansByPackage);
        this.beanDefinitionsCount = beanContext.getAllBeanDefinitions().size();
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public String getBadge() {
        return String.valueOf(beanDefinitionsCount);
    }

    private LinkedHashMap<String, List<Map<String, Object>>> computeBeansByPackage(BeanContext beanContext, DefaultBeanDefinitionData beanDefinitionData, Predicate<BeanDefinition<?>> filter, Function<BeanDefinition<?>, String> groupBy) {
        return beanContext.getAllBeanDefinitions()
            .stream()
            .filter(filter)
            .sorted(COMPARATOR_BY_NAME)
            .collect(Collectors.groupingBy(groupBy, LinkedHashMap::new, Collectors.mapping(beanDefinitionData::getData, Collectors.toList())));
    }

    record Body(
        Map<String, List<Map<String, Object>>> micronautBeansByPackage,
        Map<String, List<Map<String, Object>>> otherBeansByPackage) {
    }
}
