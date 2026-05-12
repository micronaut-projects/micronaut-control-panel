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
package io.micronaut.controlpanel.panels.spring;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.web.router.Router;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpringMetadataProvenanceTest {

    @Test
    void springSourceAnnotationsAreRetainedInRuntimeMetadata() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "SpringMetadataProvenanceTest",
            "feature.enabled", "true"
        ))) {
            BeanDefinition<SpringService> service = context.getBeanDefinition(SpringService.class);
            assertTrue(service.getAnnotationMetadata().hasAnnotation(Service.class.getName()));

            boolean routeHasSpringMapping = context.getBean(Router.class)
                .uriRoutes()
                .anyMatch(route -> (route.getTargetMethod().getDeclaringType().getName().equals(SpringController.class.getName())
                    || route.getTargetMethod().getDeclaringType().getName().contains(SpringController.class.getSimpleName()))
                    && route.getTargetMethod().getAnnotationMetadata().hasAnnotation(RestController.class.getName()));
            assertTrue(routeHasSpringMapping, () -> context.getBean(Router.class)
                .uriRoutes()
                .map(route -> route.getTargetMethod().getDeclaringType().getName() + "#"
                    + route.getTargetMethod().getName() + " "
                    + route.getUriMatchTemplate() + " "
                    + route.getTargetMethod().getAnnotationMetadata().getAnnotationNames())
                .collect(Collectors.joining("\n")));

            BeanDefinition<ConditionalSpringService> conditional = context.getBeanDefinition(ConditionalSpringService.class);
            assertTrue(conditional.getAnnotationMetadata().hasAnnotation(ConditionalOnProperty.class.getName())
                    || conditional.getAnnotationMetadata().hasAnnotation("org.springframework.boot.autoconfigure.condition.ConditionalOnProperties"),
                conditional.getAnnotationMetadata().getAnnotationNames().toString());

            BeanDefinition<SpringEndpoint> endpoint = context.getBeanDefinition(SpringEndpoint.class);
            assertTrue(endpoint.getAnnotationMetadata().hasAnnotation(Endpoint.class.getName()));
            assertTrue(endpoint.getExecutableMethods().stream()
                .anyMatch(method -> method.getAnnotationMetadata().hasAnnotation(ReadOperation.class.getName())));

            SpringCompatibilityBody body = context.getBean(SpringCompatibilityControlPanel.class).getBody();
            assertTrue(body.beanCount() >= 3);
            assertTrue(body.routeCount() >= 1);
            assertTrue(body.conditionCount() >= 1);
            assertTrue(body.endpointCount() >= 1);
        }
    }

    @Service
    public static class SpringService {
    }

    @Service
    @ConditionalOnProperty(prefix = "feature", name = "enabled", havingValue = "true")
    public static class ConditionalSpringService {
    }

    @RestController
    public static class SpringController {

        @GetMapping("/spring/provenance")
        public String provenance() {
            return "ok";
        }
    }

    @Endpoint(id = "spring-provenance")
    public static class SpringEndpoint {

        @ReadOperation
        public String read() {
            return "ok";
        }
    }
}
