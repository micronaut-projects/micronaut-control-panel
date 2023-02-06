package io.micronaut.controlpanel.core.management;

import io.micronaut.context.BeanContext;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.management.endpoint.beans.impl.DefaultBeanDefinitionData;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class BeansControlPanel implements ControlPanel {

    Map<String, List<Map<String, Object>>> micronautBeansByPackage;
    Map<String, List<Map<String, Object>>> otherBeansByPackage;
    private final long beanDefinitionsCount;

    public BeansControlPanel(BeanContext beanContext, DefaultBeanDefinitionData beanDefinitionData) {
        Function<BeanDefinition<?>, String> byPackage = beanDefinition -> beanDefinition.getBeanType().getPackage().getName();
        Function<BeanDefinition<?>, String> byMicronautPackage = beanDefinition -> beanDefinition.getBeanType().getPackage().getName().replaceAll("io\\.micronaut\\.(\\w+).*", "$1");
        Comparator<BeanDefinition<?>> byName = Comparator.comparing(bd -> bd.getClass().getName());
        Predicate<BeanDefinition<?>> isMicronautPackage = bd -> bd.getBeanType().getPackage().getName().startsWith("io.micronaut");
        Collection<BeanDefinition<?>> allBeanDefinitions = beanContext.getAllBeanDefinitions();
        this.micronautBeansByPackage = allBeanDefinitions
            .stream()
            .filter(isMicronautPackage)
            .sorted(byName)
            .collect(Collectors.groupingBy(byMicronautPackage, LinkedHashMap::new, Collectors.mapping(beanDefinitionData::getData, Collectors.toList())));

        this.otherBeansByPackage = allBeanDefinitions
            .stream()
            .filter(isMicronautPackage.negate())
            .sorted(byName)
            .collect(Collectors.groupingBy(byPackage, LinkedHashMap::new, Collectors.mapping(beanDefinitionData::getData, Collectors.toList())));
        this.beanDefinitionsCount = allBeanDefinitions.size();
    }

    @Override
    public String getTitle() {
        return "Bean Definitions";
    }

    @Override
    public Map<String, Object> getModel() {
        return Map.of(
            "micronautBeansByPackage", micronautBeansByPackage,
            "otherBeansByPackage", otherBeansByPackage
        );
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
}
