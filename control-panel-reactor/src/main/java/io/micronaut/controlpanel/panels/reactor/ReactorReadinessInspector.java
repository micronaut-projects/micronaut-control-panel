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
package io.micronaut.controlpanel.panels.reactor;

import io.micronaut.context.BeanContext;
import io.micronaut.core.reflect.ClassUtils;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports read-only Reactor context propagation and meter readiness.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
final class ReactorReadinessInspector {

    private static final String STATUS_PRESENT = "present";
    private static final String STATUS_NOT_PRESENT = "not present";
    private static final String CONTEXT_REGISTRY = "io.micrometer.context.ContextRegistry";
    private static final String METER_REGISTRY = "io.micrometer.core.instrument.MeterRegistry";

    private final BeanContext beanContext;

    ReactorReadinessInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    List<ReactorDiagnosticsBody.ReadinessCheck> readiness() {
        List<ReactorDiagnosticsBody.ReadinessCheck> checks = new ArrayList<>();
        checks.add(contextPropagation());
        checks.addAll(meterReadiness());
        return List.copyOf(checks);
    }

    private ReactorDiagnosticsBody.ReadinessCheck contextPropagation() {
        boolean present = ClassUtils.isPresent(CONTEXT_REGISTRY, getClass().getClassLoader());
        return new ReactorDiagnosticsBody.ReadinessCheck(
            "Context propagation artifact",
            present ? STATUS_PRESENT : STATUS_NOT_PRESENT,
            present
                ? "Micrometer context-propagation classes are on the classpath. This does not prove automatic propagation is enabled."
                : "io.micrometer:context-propagation is not on the classpath."
        );
    }

    private List<ReactorDiagnosticsBody.ReadinessCheck> meterReadiness() {
        if (!ClassUtils.isPresent(METER_REGISTRY, getClass().getClassLoader())) {
            return List.of(
                new ReactorDiagnosticsBody.ReadinessCheck("MeterRegistry bean", STATUS_NOT_PRESENT, "Micrometer core is not on the classpath."),
                new ReactorDiagnosticsBody.ReadinessCheck("Reactor meter names", STATUS_NOT_PRESENT, "No MeterRegistry is available.")
            );
        }
        return meterReadinessWithMicrometer();
    }

    private List<ReactorDiagnosticsBody.ReadinessCheck> meterReadinessWithMicrometer() {
        return MeterRegistryReadiness.readiness(beanContext);
    }
}
