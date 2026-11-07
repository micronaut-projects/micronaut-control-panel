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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.management.endpoint.threads.ThreadInfoMapper;
import io.micronaut.management.endpoint.threads.impl.DefaultThreadInfoMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadDumpControlPanelTest {

    private static final ControlPanelConfiguration CONFIGURATION = new ControlPanelConfiguration(ThreadDumpControlPanel.NAME);

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("endpoints.threaddump.enabled", true))) {
            ThreadDumpControlPanel panel = ctx.getBean(ThreadDumpControlPanel.class);
            assertEquals("Thread Dump", panel.getTitle());
            assertEquals("fa-layer-group", panel.getIcon());
            assertEquals(50, panel.getOrder());
            assertEquals("", panel.getBadge());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "endpoints.threaddump.enabled", true,
            ThreadDumpControlPanel.ENABLED_PROPERTY, false
        ))) {
            assertFalse(ctx.containsBean(ThreadDumpControlPanel.class));
        }
    }

    @Test
    void itRequiresTheThreadDumpEndpoint() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("endpoints.threaddump.enabled", false))) {
            assertFalse(ctx.containsBean(ThreadDumpControlPanel.class));
        }
    }

    @Test
    void itListsTheCurrentThreadWithItsStackTrace() {
        var panel = new ThreadDumpControlPanel(CONFIGURATION, new DefaultThreadInfoMapper());

        var body = panel.getBody();

        assertFalse(body.unsupportedMapper());
        assertEquals(body.threads().size(), body.total());
        assertEquals(body.total(), body.stateCounts().stream().mapToInt(ThreadDumpControlPanel.StateCount::count).sum());
        var current = body.threads().stream()
            .filter(thread -> thread.id() == Thread.currentThread().threadId())
            .findFirst()
            .orElseThrow();
        assertEquals(Thread.State.RUNNABLE, current.state());
        assertTrue(current.stackTrace().stream().anyMatch(frame -> frame.contains("itListsTheCurrentThreadWithItsStackTrace")));
    }

    @Test
    void blockedThreadsAreListedFirstWithTheirLockOwner() throws Exception {
        var lock = new Object();
        var started = new CountDownLatch(1);
        var blocked = Thread.ofPlatform().name("cp-blocked-thread").unstarted(() -> {
            started.countDown();
            synchronized (lock) {
                Thread.onSpinWait();
            }
        });
        synchronized (lock) {
            blocked.start();
            started.await();
            while (blocked.getState() != Thread.State.BLOCKED) {
                Thread.onSpinWait();
            }
            var body = new ThreadDumpControlPanel(CONFIGURATION, new DefaultThreadInfoMapper()).getBody();

            assertEquals(Thread.State.BLOCKED, body.stateCounts().getFirst().state());
            var first = body.threads().getFirst();
            assertEquals("cp-blocked-thread", first.name());
            assertEquals("badge-destructive", first.badgeClass());
            assertEquals(Thread.currentThread().getName(), first.lockOwnerName());
        }
        blocked.join();
    }

    @Test
    void aFilteringMapperIsApplied() {
        ThreadInfoMapper<ThreadInfo> onlyCurrentThread = threads -> Flux.from(threads)
            .filter(info -> info.getThreadId() == Thread.currentThread().threadId());

        var body = new ThreadDumpControlPanel(CONFIGURATION, onlyCurrentThread).getBody();

        assertEquals(1, body.total());
        assertEquals(Thread.currentThread().getName(), body.threads().getFirst().name());
    }

    @Test
    void aMapperWithAnotherShapeIsNotRendered() {
        ThreadInfoMapper<String> names = threads -> Flux.from(threads).map(ThreadInfo::getThreadName);

        var body = new ThreadDumpControlPanel(CONFIGURATION, names, () -> ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)).getBody();

        assertTrue(body.unsupportedMapper());
        assertTrue(body.total() > 0);
        assertTrue(body.threads().isEmpty());
        assertTrue(body.stateCounts().isEmpty());
    }
}
