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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;

/**
 * Enables the panel only when at least one Micronaut Spring integration marker is present.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Internal
public final class SpringCompatibilityAvailableCondition implements Condition {

    static final String[] MARKER_CLASSES = {
        "io.micronaut.spring.annotation.context.ComponentAnnotationMapper",
        "io.micronaut.spring.web.annotation.RestControllerAnnotationMapper",
        "io.micronaut.spring.boot.annotation.EndpointAnnotationMapper",
        "io.micronaut.spring.context.MicronautApplicationContext"
    };

    @Override
    public boolean matches(ConditionContext context) {
        if (isMicronautSpringPresent(SpringCompatibilityAvailableCondition.class.getClassLoader())) {
            return true;
        }
        context.fail("Micronaut Spring integration classes are not present");
        return false;
    }

    static boolean isMicronautSpringPresent(ClassLoader classLoader) {
        for (String markerClass : MARKER_CLASSES) {
            if (ClassUtils.isPresent(markerClass, classLoader)) {
                return true;
            }
        }
        return false;
    }
}
