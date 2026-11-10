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
import io.micronaut.management.endpoint.threads.ThreadInfoMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Control panel that displays a JVM thread dump.
 *
 * <p>Threads are read through the application's {@link ThreadInfoMapper}, the extension point the Micronaut Management
 * {@code /threaddump} endpoint uses, so a mapper that filters threads out applies to this panel as well. The dashboard
 * card only needs the thread states, so it reads a cheap snapshot without stack traces or lock information; the full
 * dump is only taken for the detail view.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(beans = ThreadDumpEndpoint.class)
@Requires(property = ThreadDumpControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ThreadDumpControlPanel extends AbstractControlPanel<ThreadDumpControlPanel.Body> {

    public static final String NAME = "threaddump";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * States in the order they are listed: the ones that usually explain a stuck application come first.
     */
    private static final List<Thread.State> STATE_ORDER = List.of(
        Thread.State.BLOCKED,
        Thread.State.RUNNABLE,
        Thread.State.WAITING,
        Thread.State.TIMED_WAITING,
        Thread.State.NEW,
        Thread.State.TERMINATED
    );
    private static final Comparator<ThreadRow> BY_STATE_THEN_NAME = Comparator
        .comparingInt((ThreadRow row) -> STATE_ORDER.indexOf(row.state()))
        .thenComparing(ThreadRow::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(ThreadRow::id);

    private final ThreadInfoMapper<?> threadInfoMapper;
    private final Supplier<ThreadInfo[]> snapshot;
    private final Supplier<ThreadInfo[]> threadDumper;

    @Inject
    public ThreadDumpControlPanel(@Named(NAME) ControlPanelConfiguration configuration, ThreadInfoMapper<?> threadInfoMapper) {
        this(configuration, threadInfoMapper, ThreadDumpControlPanel::snapshot, ThreadDumpControlPanel::fullDump);
    }

    ThreadDumpControlPanel(ControlPanelConfiguration configuration, ThreadInfoMapper<?> threadInfoMapper, Supplier<ThreadInfo[]> snapshot, Supplier<ThreadInfo[]> threadDumper) {
        super(NAME, configuration);
        this.threadInfoMapper = threadInfoMapper;
        this.snapshot = snapshot;
        this.threadDumper = threadDumper;
    }

    /**
     * @return the threads and their states, without stack traces or lock information, for the dashboard card
     */
    @Override
    public Body getBody() {
        return body(snapshot.get());
    }

    /**
     * @return the full thread dump, with stack traces and lock information, for the detail view
     */
    @ReflectiveAccess
    public Body getDetailedBody() {
        return body(threadDumper.get());
    }

    private static ThreadInfo[] snapshot() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        // A depth of 0 reads no stack frames, and threads that ended meanwhile are reported as null
        return Arrays.stream(bean.getThreadInfo(bean.getAllThreadIds(), 0))
            .filter(Objects::nonNull)
            .toArray(ThreadInfo[]::new);
    }

    private static ThreadInfo[] fullDump() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        // Asking for lock information that the runtime cannot provide, as in a GraalVM native image, throws
        return bean.dumpAllThreads(bean.isObjectMonitorUsageSupported(), bean.isSynchronizerUsageSupported());
    }

    private Body body(ThreadInfo[] dump) {
        List<?> mapped = Flux.from(threadInfoMapper.mapThreadInfo(Flux.fromArray(dump)))
            .filter(Objects::nonNull)
            .collectList()
            .blockOptional()
            .orElse(List.of());
        if (!mapped.stream().allMatch(ThreadInfo.class::isInstance)) {
            // A custom mapper produced a different shape. Never fall back to the unmapped data it chose not to expose.
            return new Body(mapped.size(), List.of(), List.of(), true);
        }
        List<ThreadRow> threads = mapped.stream()
            .map(ThreadInfo.class::cast)
            .map(ThreadRow::of)
            .sorted(BY_STATE_THEN_NAME)
            .toList();
        return new Body(threads.size(), stateCounts(threads), threads, false);
    }

    @Override
    public String getBadge() {
        // The card already shows the counts, and a badge would take a second snapshot on every dashboard render
        return StringUtils.EMPTY_STRING;
    }

    private static List<StateCount> stateCounts(List<ThreadRow> threads) {
        Map<Thread.State, Integer> counts = new EnumMap<>(Thread.State.class);
        threads.forEach(thread -> counts.merge(thread.state(), 1, Integer::sum));
        return STATE_ORDER.stream()
            .filter(counts::containsKey)
            .map(state -> new StateCount(state, counts.get(state)))
            .toList();
    }

    private static String badgeClass(Thread.State state) {
        return switch (state) {
            case BLOCKED -> "badge-destructive";
            case RUNNABLE -> "badge-primary";
            default -> "badge-secondary";
        };
    }

    /**
     * The thread dump. Internal to the Thread Dump panel views.
     *
     * @param total the number of threads the configured {@link ThreadInfoMapper} returned
     * @param stateCounts the number of threads per state, blocked threads first
     * @param threads the threads, blocked threads first
     * @param unsupportedMapper whether the configured mapper returned something other than {@link ThreadInfo}
     */
    @ReflectiveAccess
    public record Body(int total, List<StateCount> stateCounts, List<ThreadRow> threads, boolean unsupportedMapper) {
    }

    /**
     * Number of threads in a state. Internal to the Thread Dump panel views.
     *
     * @param state the thread state
     * @param count the number of threads in that state
     */
    @ReflectiveAccess
    public record StateCount(Thread.State state, int count) {

        /**
         * @return the CSS class of the state badge
         */
        public String badgeClass() {
            return ThreadDumpControlPanel.badgeClass(state);
        }
    }

    /**
     * A thread of the dump. Internal to the Thread Dump panel views.
     *
     * @param id the thread id
     * @param name the thread name
     * @param state the thread state
     * @param daemon whether it is a daemon thread
     * @param lockName the lock the thread is blocked on or waiting for, if any
     * @param lockOwnerName the name of the thread that owns that lock, if any
     * @param blockedCount how many times the thread has blocked
     * @param waitedCount how many times the thread has waited
     * @param stackTrace the stack frames, each followed by the monitors locked in it, as in a {@code jstack} dump
     * @param lockedSynchronizers the ownable synchronizers, such as {@code ReentrantLock}s, the thread holds
     */
    @ReflectiveAccess
    public record ThreadRow(
        long id,
        String name,
        Thread.State state,
        boolean daemon,
        @Nullable String lockName,
        @Nullable String lockOwnerName,
        long blockedCount,
        long waitedCount,
        List<String> stackTrace,
        List<String> lockedSynchronizers
    ) {

        static ThreadRow of(ThreadInfo info) {
            return new ThreadRow(
                info.getThreadId(),
                info.getThreadName(),
                info.getThreadState(),
                info.isDaemon(),
                info.getLockName(),
                info.getLockOwnerName(),
                info.getBlockedCount(),
                info.getWaitedCount(),
                stackTrace(info),
                Arrays.stream(info.getLockedSynchronizers()).map(LockInfo::toString).toList()
            );
        }

        private static List<String> stackTrace(ThreadInfo info) {
            StackTraceElement[] frames = info.getStackTrace();
            MonitorInfo[] monitors = info.getLockedMonitors();
            List<String> lines = new ArrayList<>(frames.length + monitors.length);
            for (int depth = 0; depth < frames.length; depth++) {
                lines.add(frames[depth].toString());
                for (MonitorInfo monitor : monitors) {
                    if (monitor.getLockedStackDepth() == depth) {
                        lines.add("- locked " + monitor);
                    }
                }
            }
            return lines;
        }

        /**
         * @return the CSS class of the state badge
         */
        public String badgeClass() {
            return ThreadDumpControlPanel.badgeClass(state);
        }
    }
}
