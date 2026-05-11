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

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.NoSuchBeanException;

import java.util.Comparator;
import java.util.List;

final class MeterRegistryReadiness {

    private MeterRegistryReadiness() {
    }

    static List<ReactorDiagnosticsBody.ReadinessCheck> readiness(BeanContext beanContext) {
        try {
            MeterRegistry registry = beanContext.getBean(MeterRegistry.class);
            List<String> reactorNames = registry.getMeters()
                .stream()
                .map(meter -> meter.getId().getName())
                .filter(name -> name.startsWith("reactor."))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
            return List.of(
                new ReactorDiagnosticsBody.ReadinessCheck("MeterRegistry bean", "present", "A Micrometer MeterRegistry bean is available."),
                new ReactorDiagnosticsBody.ReadinessCheck("Reactor meter names", reactorNames.isEmpty() ? "not present" : "present", reactorNames.size() + " reactor.* meter name(s) are registered.")
            );
        } catch (NoSuchBeanException e) {
            return List.of(
                new ReactorDiagnosticsBody.ReadinessCheck("MeterRegistry bean", "not present", "No Micrometer MeterRegistry bean is available."),
                new ReactorDiagnosticsBody.ReadinessCheck("Reactor meter names", "not present", "No MeterRegistry is available.")
            );
        }
    }
}
