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
import io.micronaut.management.endpoint.threads.ThreadInfoMapper;
import io.micronaut.management.endpoint.threads.impl.DefaultThreadInfoMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ThreadDumpControlPanelTest {

    private static final String PARKED_THREAD_NAME = "cp-threaddump-parked-thread";

    @Test
    void itIsConfiguredCorrectlyWhenThreadDumpEndpointIsAvailable() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ThreadDumpControlPanel panel = ctx.getBean(ThreadDumpControlPanel.class);

            assertEquals("Thread Dump", panel.getTitle());
            assertEquals("fa-list-check", panel.getIcon());
            assertEquals(50, panel.getOrder());
            assertTrue(panel.getBody().summaryAvailable(), "The framework default mapper must keep the dashboard summary");
            assertFalse(panel.getBody().stateCounts().isEmpty());
            assertFalse(panel.getThreadDump().threads().isEmpty());
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
        ThreadDumpControlPanel.ThreadDump dump = ThreadDumpControlPanel.buildThreadDump(List.of(
            snapshot(4, "waiting-worker", Thread.State.WAITING, false, null, null, -1, 1, -1, 2, -1, "example.Waiting.run(Waiting.java:1)"),
            snapshot(2, "blocked-worker", Thread.State.BLOCKED, true, "java.lang.Object@1", "lock-owner", 9, 3, 40, 5, 60, "example.Blocked.run(Blocked.java:1)"),
            snapshot(3, "timed-worker", Thread.State.TIMED_WAITING, false, null, null, -1, 0, -1, 7, 80, "example.Timed.run(Timed.java:1)"),
            snapshot(1, "runnable-worker", Thread.State.RUNNABLE, true, null, null, -1, 0, 0, 0, 0, "example.Runnable.run(Runnable.java:1)")
        ));

        assertFalse(dump.unsupportedMapper());
        assertEquals(4, dump.totalThreads());
        assertEquals(List.of("BLOCKED", "RUNNABLE", "WAITING", "TIMED_WAITING"), dump.stateCounts().stream().map(ThreadDumpControlPanel.StateCount::state).toList());
        assertEquals(List.of("blocked-worker", "runnable-worker", "waiting-worker", "timed-worker"), dump.threads().stream().map(ThreadDumpControlPanel.ThreadRow::threadName).toList());
        ThreadDumpControlPanel.ThreadRow blocked = dump.threads().get(0);
        assertTrue(blocked.blocked());
        assertTrue(blocked.hasLockOwner());
        assertEquals("3", blocked.blockedCount());
        assertEquals("40", blocked.blockedTime());
        assertTrue(blocked.hasBlockedTime());
        assertEquals(List.of("example.Blocked.run(Blocked.java:1)"), blocked.stackTrace());

        ThreadDumpControlPanel.ThreadRow waiting = dump.threads().get(2);
        assertEquals("-1", waiting.blockedTime());
        assertFalse(waiting.hasBlockedTime());
        assertEquals("-1", waiting.waitedTime());
        assertFalse(waiting.hasWaitedTime());
    }

    @Test
    void itOnlyDisplaysTheThreadsTheConfiguredThreadInfoMapperEmits() throws InterruptedException {
        withParkedThread(() -> {
            ThreadDumpControlPanel unfiltered = panel(passThroughMapper(), ThreadDumpControlPanelTest::dump);
            List<String> allNames = threadNames(unfiltered);
            assertTrue(allNames.contains(PARKED_THREAD_NAME), "The unfiltered panel must see the thread the mapper will later exclude");
            assertTrue(allNames.size() > 1, "The unfiltered panel must see more than the parked thread");

            // A mapper that drops every thread but one stands in for any filtering/redacting ThreadInfoMapper.
            ThreadInfoMapper<ThreadInfo> onlyParkedThread = publisher -> Flux.from(publisher)
                .filter(info -> PARKED_THREAD_NAME.equals(info.getThreadName()));
            ThreadDumpControlPanel filtered = panel(onlyParkedThread, ThreadDumpControlPanelTest::dump);

            ThreadDumpControlPanel.ThreadDump dump = filtered.getThreadDump();
            assertFalse(dump.unsupportedMapper());
            assertEquals(List.of(PARKED_THREAD_NAME), dump.threads().stream().map(ThreadDumpControlPanel.ThreadRow::threadName).toList());
            assertEquals(1, dump.totalThreads());

            // The dashboard never reports counts a custom mapper did not produce, so it reports none at all.
            ThreadDumpControlPanel.Body body = filtered.getBody();
            assertFalse(body.summaryAvailable());
            assertEquals(0, body.totalThreads());
            assertTrue(body.stateCounts().isEmpty());
            assertEquals("", filtered.getBadge());
        });
    }

    @Test
    void aStackFrameFilteringMapperIsNeverGivenATruncatedDump() throws InterruptedException {
        withParkedThread(() -> {
            // Selects the parked thread, then keeps it only if this test class appears in its stack. That decision is
            // meaningless unless the mapper is handed the stack frames, which the cheap dashboard dump does not carry.
            ThreadInfoMapper<ThreadInfo> stackFrameFilter = publisher -> Flux.from(publisher)
                .filter(info -> PARKED_THREAD_NAME.equals(info.getThreadName()))
                .filter(info -> Arrays.stream(info.getStackTrace())
                    .anyMatch(frame -> ThreadDumpControlPanelTest.class.getName().equals(frame.getClassName())));
            ThreadDumpControlPanel panel = panel(stackFrameFilter, ThreadDumpControlPanelTest::dump);

            assertEquals(List.of(PARKED_THREAD_NAME), threadNames(panel), "The detail view must see the mapper's selection");
            assertEquals(List.of(PARKED_THREAD_NAME), threadNames(panel), "A refresh must see the same selection");

            // Before the fix these reported every thread in the JVM, because the truncated dump carried no frames for
            // the mapper to reject on.
            ThreadDumpControlPanel.Body body = panel.getBody();
            assertFalse(body.summaryAvailable());
            assertEquals(0, body.totalThreads());
            assertTrue(body.stateCounts().isEmpty());
            assertEquals("", panel.getBadge());
        });
    }

    @Test
    void aMapperThatDereferencesStackFramesDoesNotBreakTheDashboard() throws InterruptedException {
        withParkedThread(() -> {
            // Indexing into the stack trace throws on a dump collected without frames.
            ThreadInfoMapper<ThreadInfo> topFrameFilter = publisher -> Flux.from(publisher)
                .filter(info -> PARKED_THREAD_NAME.equals(info.getThreadName()))
                .filter(info -> !info.getStackTrace()[0].getClassName().isEmpty());
            ThreadDumpControlPanel panel = panel(topFrameFilter, ThreadDumpControlPanelTest::dump);

            assertDoesNotThrow(panel::getBody, "The dashboard card must not propagate a mapper failure");
            assertDoesNotThrow(panel::getBadge, "The dashboard badge must not propagate a mapper failure");
            assertFalse(panel.getBody().summaryAvailable());
            assertEquals("", panel.getBadge());

            // The very same mapper still works on the detail view, which always supplies a full dump.
            assertEquals(List.of(PARKED_THREAD_NAME), threadNames(panel));
        });
    }

    @Test
    void itDoesNotFallBackToRawThreadsWhenTheMapperEmitsAnUnsupportedShape() {
        ThreadInfoMapper<Map<String, Object>> customShape = publisher -> Flux.from(publisher)
            .map(info -> Map.<String, Object>of("name", info.getThreadName()));
        ThreadDumpControlPanel panel = panel(customShape, ThreadDumpControlPanelTest::dump);

        ThreadDumpControlPanel.ThreadDump dump = panel.getThreadDump();
        assertTrue(dump.unsupportedMapper());
        assertTrue(dump.threads().isEmpty());
        assertTrue(dump.stateCounts().isEmpty());
        assertTrue(dump.totalThreads() > 0);

        ThreadDumpControlPanel.Body body = panel.getBody();
        assertFalse(body.summaryAvailable());
        assertTrue(body.stateCounts().isEmpty());
        assertEquals("", panel.getBadge());
    }

    @Test
    void theDashboardSummaryNeverCollectsStackTracesOrLockDetails() {
        var collections = new ArrayList<Boolean>();
        ThreadDumpControlPanel.ThreadInfoSource recording = withDetails -> {
            collections.add(withDetails);
            return dump(withDetails);
        };
        ThreadDumpControlPanel panel = panel(passThroughMapper(), recording);

        ThreadDumpControlPanel.Body body = panel.getBody();
        assertEquals(List.of(false), collections, "Rendering the dashboard card must not request a full dump");
        assertFalse(body.stateCounts().isEmpty());

        panel.getBadge();
        assertEquals(List.of(false, false), collections, "The dashboard badge must not request a full dump either");

        // The detail view, and every refresh of it, still takes a fresh and complete dump.
        assertTrue(panel.getThreadDump().threads().stream().anyMatch(row -> !row.stackTrace().isEmpty()));
        assertEquals(List.of(false, false, true), collections);
        panel.getThreadDump();
        assertEquals(List.of(false, false, true, true), collections);
    }

    @Test
    void theDashboardCardTemplateOnlyRendersSummaryData() throws IOException {
        var template = readTemplate("/views/threaddump/body.hbs");

        assertTrue(template.contains("controlPanel.body"));
        assertTrue(template.contains("summaryAvailable"));
        assertFalse(template.contains("controlPanel.threadDump"));
        assertFalse(template.contains("{{#each threads"));
        assertFalse(template.contains("stackTrace"));
        assertFalse(template.contains("threadName"));
    }

    @Test
    void threadValuesAreNotInterpolatedIntoInlineJavaScript() throws IOException {
        var template = readTemplate("/views/threaddump/detail.hbs");

        assertTrue(template.contains("controlPanel.threadDump"));
        assertFalse(template.contains("<script>"));
        assertTrue(template.contains("<details>"));
        assertTrue(template.contains("data-filter-table-search"));
        assertTrue(template.contains("No threads match your search."));
    }

    private String readTemplate(String path) throws IOException {
        try (var in = getClass().getResourceAsStream(path)) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ThreadDumpControlPanel panel(ThreadInfoMapper<?> mapper, ThreadDumpControlPanel.ThreadInfoSource source) {
        return new ThreadDumpControlPanel(new ControlPanelConfiguration(ThreadDumpControlPanel.NAME), mapper, source);
    }

    private static ThreadInfoMapper<ThreadInfo> passThroughMapper() {
        // The framework default, not an equivalent lambda: the panel only trusts that exact type with a summary dump.
        return new DefaultThreadInfoMapper();
    }

    private static List<String> threadNames(ThreadDumpControlPanel panel) {
        return panel.getThreadDump().threads().stream().map(ThreadDumpControlPanel.ThreadRow::threadName).toList();
    }

    private static ThreadInfo[] dump(boolean withDetails) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        return withDetails
            ? threadMXBean.dumpAllThreads(true, true)
            : threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 0);
    }

    private static void withParkedThread(Runnable assertions) throws InterruptedException {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var thread = new Thread(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, PARKED_THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertions.run();
        } finally {
            release.countDown();
            thread.join(TimeUnit.SECONDS.toMillis(10));
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
