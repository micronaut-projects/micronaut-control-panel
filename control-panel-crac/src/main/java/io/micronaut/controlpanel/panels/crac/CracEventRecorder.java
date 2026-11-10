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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.crac.events.AfterRestoreEvent;
import io.micronaut.crac.events.BeforeCheckpointEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Keeps the most recent checkpoint and restore events that Micronaut CRaC publishes for each resource. It only
 * records the class of each resource, not its state.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = BeforeCheckpointEvent.class)
@Requires(property = CracControlPanel.ENABLED_PROPERTY, notEquals = "false")
public class CracEventRecorder {

    static final int MAX_EVENTS = 50;

    private final Deque<CracInfo.Event> events = new ConcurrentLinkedDeque<>();

    /**
     * @param event the event published before a resource is checkpointed
     */
    @EventListener
    void onBeforeCheckpoint(BeforeCheckpointEvent event) {
        record("checkpoint", event.getSource(), event.getNow(), event.getTimeTakenNanos());
    }

    /**
     * @param event the event published after a resource is restored
     */
    @EventListener
    void onAfterRestore(AfterRestoreEvent event) {
        record("restore", event.getSource(), event.getNow(), event.getTimeTakenNanos());
    }

    /**
     * @return the recorded events, newest first
     */
    public List<CracInfo.Event> events() {
        return new ArrayList<>(events);
    }

    private void record(String phase, Object resource, Instant time, long nanos) {
        events.addFirst(new CracInfo.Event(phase, resource.getClass().getName(), time.toString(), CracInspector.format(Duration.ofNanos(nanos))));
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }
}
