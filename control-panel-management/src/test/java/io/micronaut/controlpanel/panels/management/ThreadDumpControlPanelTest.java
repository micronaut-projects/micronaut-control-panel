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
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.endpoint.threads.ThreadDumpEndpoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ThreadDumpControlPanelTest {

    @Test
    void itIsConfiguredCorrectlyWhenThreadDumpEndpointIsAvailable() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ThreadDumpControlPanel panel = ctx.getBean(ThreadDumpControlPanel.class);

            assertEquals("Thread Dump", panel.getTitle());
            assertEquals("fa-list-check", panel.getIcon());
            assertEquals(50, panel.getOrder());
            assertFalse(panel.getBody().threads().isEmpty());
            assertTrue(Integer.parseInt(panel.getBadge()) > 0);
        }
    }

    @Test
    void itCanBeDisabledIndependently() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(ThreadDumpControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ThreadDumpControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(ThreadDumpControlPanel.class));
            assertTrue(ctx.containsBean(ThreadDumpEndpoint.class));
        }
    }

    @Test
    void itIsNotAvailableWhenThreadDumpEndpointIsDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("endpoints.threaddump.enabled", false))) {
            assertFalse(ctx.containsBean(ThreadDumpEndpoint.class));
            assertFalse(ctx.containsBean(ThreadDumpControlPanel.class));
        }
    }

    @Test
    void itBuildsThreadSummaryInDiagnosticStateOrder() {
        ThreadDumpControlPanel.Body body = ThreadDumpControlPanel.buildBody(List.of(
            snapshot(4, "waiting-worker", Thread.State.WAITING, false, null, null, -1, 1, -1, 2, -1, "example.Waiting.run(Waiting.java:1)"),
            snapshot(2, "blocked-worker", Thread.State.BLOCKED, true, "java.lang.Object@1", "lock-owner", 9, 3, 40, 5, 60, "example.Blocked.run(Blocked.java:1)"),
            snapshot(3, "timed-worker", Thread.State.TIMED_WAITING, false, null, null, -1, 0, -1, 7, 80, "example.Timed.run(Timed.java:1)"),
            snapshot(1, "runnable-worker", Thread.State.RUNNABLE, true, null, null, -1, 0, 0, 0, 0, "example.Runnable.run(Runnable.java:1)")
        ));

        assertEquals(4, body.totalThreads());
        assertEquals(List.of("BLOCKED", "RUNNABLE", "WAITING", "TIMED_WAITING"), body.stateCounts().stream().map(ThreadDumpControlPanel.StateCount::state).toList());
        assertEquals(List.of("blocked-worker", "runnable-worker", "waiting-worker", "timed-worker"), body.threads().stream().map(ThreadDumpControlPanel.ThreadRow::threadName).toList());
        ThreadDumpControlPanel.ThreadRow blocked = body.threads().get(0);
        assertTrue(blocked.blocked());
        assertTrue(blocked.hasLockOwner());
        assertEquals("3", blocked.blockedCount());
        assertEquals("40", blocked.blockedTime());
        assertEquals(List.of("example.Blocked.run(Blocked.java:1)"), blocked.stackTrace());
    }

    @Test
    void threadValuesAreNotInterpolatedIntoInlineJavaScript() throws IOException {
        try (var in = getClass().getResourceAsStream("/views/threaddump/detail.hbs")) {
            assertNotNull(in);
            var template = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(template.contains("<script>"));
            assertTrue(template.contains("<details>"));
            assertTrue(template.contains("data-filter-table-search"));
            assertTrue(template.contains("No threads match your search."));
        }
    }

    private static ThreadDumpControlPanel.ThreadSnapshot snapshot(
        long threadId,
        String threadName,
        Thread.State state,
        boolean daemon,
        String lockName,
        String lockOwnerName,
        long lockOwnerId,
        long blockedCount,
        long blockedTime,
        long waitedCount,
        long waitedTime,
        String stackTrace
    ) {
        return new ThreadDumpControlPanel.ThreadSnapshot(
            threadId,
            threadName,
            state,
            daemon,
            lockName,
            lockOwnerName,
            lockOwnerId,
            blockedCount,
            blockedTime,
            waitedCount,
            waitedTime,
            List.of(stackTrace)
        );
    }
}
