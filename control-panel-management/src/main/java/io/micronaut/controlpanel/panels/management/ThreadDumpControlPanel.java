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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.threads.ThreadDumpEndpoint;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Control panel that displays the current JVM thread dump.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.1.0
 */
@Singleton
@Requires(beans = ThreadDumpEndpoint.class)
@Requires(property = ThreadDumpControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ThreadDumpControlPanel extends AbstractControlPanel<ThreadDumpControlPanel.Body> {

    public static final String NAME = "threaddump";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private static final List<Thread.State> STATE_ORDER = List.of(
        Thread.State.BLOCKED,
        Thread.State.RUNNABLE,
        Thread.State.WAITING,
        Thread.State.TIMED_WAITING,
        Thread.State.NEW,
        Thread.State.TERMINATED
    );
    private static final Comparator<ThreadRow> THREAD_ROW_COMPARATOR = Comparator
        .comparingInt((ThreadRow row) -> STATE_ORDER.indexOf(row.state()))
        .thenComparing(ThreadRow::threadName, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(ThreadRow::threadId);

    public ThreadDumpControlPanel(@Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
    }

    @Override
    public Body getBody() {
        ThreadInfo[] threadInfos = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        return buildBody(List.of(threadInfos).stream()
            .map(ThreadSnapshot::from)
            .toList());
    }

    @Override
    public String getBadge() {
        return String.valueOf(ManagementFactory.getThreadMXBean().getThreadCount());
    }

    static Body buildBody(List<ThreadSnapshot> snapshots) {
        EnumMap<Thread.State, Integer> counts = new EnumMap<>(Thread.State.class);
        snapshots.forEach(snapshot -> counts.merge(snapshot.state(), 1, Integer::sum));

        List<StateCount> stateCounts = new ArrayList<>();
        for (Thread.State state : STATE_ORDER) {
            Integer count = counts.remove(state);
            if (count != null) {
                stateCounts.add(new StateCount(state.name(), count, stateBadgeClass(state)));
            }
        }
        counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new StateCount(entry.getKey().name(), entry.getValue(), stateBadgeClass(entry.getKey())))
            .forEach(stateCounts::add);

        List<ThreadRow> threads = snapshots.stream()
            .map(ThreadRow::from)
            .sorted(THREAD_ROW_COMPARATOR)
            .toList();

        return new Body(threads.size(), stateCounts, threads);
    }

    private static String stateBadgeClass(Thread.State state) {
        return switch (state) {
            case BLOCKED -> "badge-danger";
            case RUNNABLE -> "badge-primary";
            case WAITING, TIMED_WAITING -> "badge-secondary";
            default -> "badge";
        };
    }

    @ReflectiveAccess
    public record Body(int totalThreads, List<StateCount> stateCounts, List<ThreadRow> threads) {
    }

    @ReflectiveAccess
    public record StateCount(String state, int count, String badgeClass) {
    }

    @ReflectiveAccess
    public record ThreadRow(
        long threadId,
        String threadName,
        Thread.State state,
        boolean daemon,
        String lockName,
        String lockOwnerName,
        long lockOwnerId,
        String blockedCount,
        String blockedTime,
        boolean hasBlockedTime,
        String waitedCount,
        String waitedTime,
        boolean hasWaitedTime,
        List<String> stackTrace,
        String badgeClass,
        boolean blocked,
        boolean hasLockOwner
    ) {
        static ThreadRow from(ThreadSnapshot snapshot) {
            return new ThreadRow(
                snapshot.threadId(),
                snapshot.threadName(),
                snapshot.state(),
                snapshot.daemon(),
                snapshot.lockName(),
                snapshot.lockOwnerName(),
                snapshot.lockOwnerId(),
                Long.toString(snapshot.blockedCount()),
                Long.toString(snapshot.blockedTime()),
                snapshot.blockedTime() >= 0,
                Long.toString(snapshot.waitedCount()),
                Long.toString(snapshot.waitedTime()),
                snapshot.waitedTime() >= 0,
                snapshot.stackTrace(),
                stateBadgeClass(snapshot.state()),
                snapshot.state() == Thread.State.BLOCKED,
                snapshot.lockOwnerName() != null || snapshot.lockOwnerId() >= 0
            );
        }
    }

    record ThreadSnapshot(
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
        List<String> stackTrace
    ) {
        static ThreadSnapshot from(ThreadInfo threadInfo) {
            return new ThreadSnapshot(
                threadInfo.getThreadId(),
                threadInfo.getThreadName(),
                threadInfo.getThreadState(),
                threadInfo.isDaemon(),
                threadInfo.getLockName(),
                threadInfo.getLockOwnerName(),
                threadInfo.getLockOwnerId(),
                threadInfo.getBlockedCount(),
                threadInfo.getBlockedTime(),
                threadInfo.getWaitedCount(),
                threadInfo.getWaitedTime(),
                List.of(threadInfo.getStackTrace()).stream()
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList())
            );
        }
    }
}
