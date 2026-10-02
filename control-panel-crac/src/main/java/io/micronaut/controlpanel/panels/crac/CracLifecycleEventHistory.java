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
package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.crac.OrderedResource;
import io.micronaut.crac.events.AfterRestoreEvent;
import io.micronaut.crac.events.BeforeCheckpointEvent;
import jakarta.inject.Singleton;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Bounded in-memory history of CRaC resource lifecycle events.
 */
@Singleton
@Requires(classes = OrderedResource.class)
final class CracLifecycleEventHistory implements ApplicationEventListener<BeforeCheckpointEvent> {

    private static final int CAPACITY = 64;

    private final BeanContext beanContext;
    private final Deque<CracDiagnostics.LifecycleEvent> events = new ArrayDeque<>(CAPACITY);

    CracLifecycleEventHistory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(BeforeCheckpointEvent event) {
        recordEvent("beforeCheckpoint", (OrderedResource) event.getSource(), event.getNow().toString(), event.getTimeTakenNanos());
    }

    @io.micronaut.runtime.event.annotation.EventListener
    void onAfterRestore(AfterRestoreEvent event) {
        recordEvent("afterRestore", (OrderedResource) event.getSource(), event.getNow().toString(), event.getTimeTakenNanos());
    }

    synchronized List<CracDiagnostics.LifecycleEvent> recentEvents() {
        return events.stream()
            .sorted(Comparator.comparing(CracDiagnostics.LifecycleEvent::completedAt).reversed())
            .toList();
    }

    private synchronized void recordEvent(String phase, OrderedResource resource, String completedAt, long timeTakenNanos) {
        if (events.size() == CAPACITY) {
            events.removeFirst();
        }
        String category = CracResourceClassifier.category(resource);
        events.addLast(new CracDiagnostics.LifecycleEvent(
            phase,
            resource.getOrder(),
            category,
            beanName(resource),
            resource.getClass().getName(),
            CracResourceClassifier.simpleName(resource),
            completedAt,
            CracSupportDetector.formatNanos(timeTakenNanos)
        ));
    }

    private String beanName(OrderedResource resource) {
        return beanContext.mapOfType(OrderedResource.class).entrySet()
            .stream()
            .filter(entry -> entry.getValue() == resource)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unresolved");
    }
}
