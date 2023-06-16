package io.micronaut.controlpanel.ui;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;

import java.util.Map;

@ContextConfigurer
@Internal
class ControlPanelContextConfigurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
        PropertySource propertySource = PropertySource.of("control-panel", Map.of(
            "micronaut.router.static-resources.control-panel.enabled", "true",
            "micronaut.router.static-resources.control-panel.paths", "classpath:static",
            "micronaut.router.static-resources.control-panel.mapping", "/static/**"
        ));
        builder.propertySources(propertySource);
    }
}
