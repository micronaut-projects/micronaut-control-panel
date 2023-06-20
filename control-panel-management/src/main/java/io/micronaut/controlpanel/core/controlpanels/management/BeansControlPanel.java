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
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.management.endpoint.beans.BeansEndpoint;
import io.micronaut.management.endpoint.beans.impl.DefaultBeanDefinitionData;
import io.micronaut.runtime.context.scope.Refreshable;
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
@Requires(beans = BeansEndpoint.class)
@Refreshable
public class BeansControlPanel implements ControlPanel<BeansControlPanel.Body> {

    private final Body body;
    private final long beanDefinitionsCount;

    public BeansControlPanel(BeanContext beanContext, DefaultBeanDefinitionData beanDefinitionData) {
        Function<BeanDefinition<?>, String> byPackage = beanDefinition -> beanDefinition.getBeanType().getPackage().getName();
        Function<BeanDefinition<?>, String> byMicronautPackage = beanDefinition -> beanDefinition.getBeanType().getPackage().getName().replaceAll("io\\.micronaut\\.(\\w+).*", "$1");
        var byName = Comparator.comparing(bd -> bd.getClass().getName());
        Predicate<BeanDefinition<?>> isMicronautPackage = bd -> bd.getBeanType().getPackage().getName().startsWith("io.micronaut");
        var allBeanDefinitions = beanContext.getAllBeanDefinitions();
        var micronautBeansByPackage = allBeanDefinitions
            .stream()
            .filter(isMicronautPackage)
            .sorted(byName)
            .collect(Collectors.groupingBy(byMicronautPackage, LinkedHashMap::new, Collectors.mapping(beanDefinitionData::getData, Collectors.toList())));

        var otherBeansByPackage = allBeanDefinitions
            .stream()
            .filter(isMicronautPackage.negate())
            .sorted(byName)
            .collect(Collectors.groupingBy(byPackage, LinkedHashMap::new, Collectors.mapping(beanDefinitionData::getData, Collectors.toList())));

        this.body = new Body(micronautBeansByPackage, otherBeansByPackage);
        this.beanDefinitionsCount = allBeanDefinitions.size();
    }

    @Override
    public String getTitle() {
        return "Bean Definitions";
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }

    @Override
    public String getName() {
        return "beans";
    }

    @Override
    public String getBadge() {
        return String.valueOf(beanDefinitionsCount);
    }

    @Override
    public int getOrder() {
        return HealthControlPanel.ORDER + 30;
    }

    @Override
    public String getIcon() {
        return "fa-plug";
    }

    record Body(
        Map<String, List<Map<String, Object>>> micronautBeansByPackage,
        Map<String, List<Map<String, Object>>> otherBeansByPackage) {
    }
}
