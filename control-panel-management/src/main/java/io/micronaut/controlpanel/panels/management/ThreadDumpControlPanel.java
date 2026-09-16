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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Control panel that displays the current JVM thread dump.
 *
 * <p>Thread data is always read through the application's {@link ThreadInfoMapper}, the same extension point
 * {@link ThreadDumpEndpoint} uses, so a mapper that filters or redacts threads applies to this panel too. Mappers that
 * emit something other than {@link ThreadInfo} cannot be rendered as thread rows; in that case the panel reports the
 * number of entries the mapper emitted and displays no thread details.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.2.0
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

    private final ThreadInfoMapper<?> threadInfoMapper;
    private final ThreadInfoSource threadInfoSource;

    @Inject
    public ThreadDumpControlPanel(@Named(NAME) ControlPanelConfiguration configuration, ThreadInfoMapper<?> threadInfoMapper) {
        this(configuration, threadInfoMapper, ThreadDumpControlPanel::dumpThreads);
    }

    ThreadDumpControlPanel(ControlPanelConfiguration configuration, ThreadInfoMapper<?> threadInfoMapper, ThreadInfoSource threadInfoSource) {
        super(NAME, configuration);
        this.threadInfoMapper = threadInfoMapper;
        this.threadInfoSource = threadInfoSource;
    }

    /**
     * Summary shown on the dashboard card. It never collects stack traces, monitors or synchronizers.
     *
     * @return the thread state summary.
     */
    @Override
    public Body getBody() {
        ThreadDump summary = collect(false);
        return new Body(summary.totalThreads(), summary.stateCounts(), summary.unsupportedMapper());
    }

    /**
     * Full thread dump used by the detail view only. Unlike {@link #getBody()} this collects stack traces, locked
     * monitors and locked synchronizers.
     *
     * @return the current thread dump.
     */
    @ReflectiveAccess
    public ThreadDump getThreadDump() {
        return collect(true);
    }

    @Override
    public String getBadge() {
        return String.valueOf(collect(false).totalThreads());
    }

    private ThreadDump collect(boolean withDetails) {
        List<?> mapped = mapThreadInfo(threadInfoSource.dump(withDetails));
        List<ThreadSnapshot> snapshots = new ArrayList<>(mapped.size());
        for (Object item : mapped) {
            if (item instanceof ThreadInfo threadInfo) {
                snapshots.add(ThreadSnapshot.from(threadInfo));
            } else {
                // A custom ThreadInfoMapper produced a shape this panel cannot interpret. Report how many entries it
                // emitted, but never fall back to the unmapped thread data it chose not to expose.
                return ThreadDump.unsupportedMapper(mapped.size());
            }
        }
        return buildThreadDump(snapshots);
    }

    private List<?> mapThreadInfo(ThreadInfo[] threadInfos) {
        Publisher<?> mapped = threadInfoMapper.mapThreadInfo(Flux.fromArray(threadInfos));
        List<?> result = Flux.from(mapped).collectList().block();
        return result == null ? List.of() : result;
    }

    private static ThreadInfo[] dumpThreads(boolean withDetails) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = withDetails
            ? threadMXBean.dumpAllThreads(true, true)
            : threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 0);
        return Arrays.stream(threadInfos)
            .filter(Objects::nonNull)
            .toArray(ThreadInfo[]::new);
    }

    static ThreadDump buildThreadDump(List<ThreadSnapshot> snapshots) {
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

        return new ThreadDump(threads.size(), stateCounts, threads, false);
    }

    private static String stateBadgeClass(Thread.State state) {
        return switch (state) {
            case BLOCKED -> "badge-danger";
            case RUNNABLE -> "badge-primary";
            case WAITING, TIMED_WAITING -> "badge-secondary";
            default -> "badge";
        };
    }

    /**
     * Reads the raw thread data the configured {@link ThreadInfoMapper} is applied to. Exists so that the cheap
     * dashboard path and the full detail path can be told apart.
     */
    @FunctionalInterface
    interface ThreadInfoSource {

        /**
         * @param withDetails whether stack traces, locked monitors and locked synchronizers must be collected.
         * @return the current thread information, without null entries.
         */
        ThreadInfo[] dump(boolean withDetails);
    }

    /**
     * Rendering model of the dashboard card. Internal to the Thread Dump panel views.
     *
     * @param totalThreads number of entries the configured {@link ThreadInfoMapper} emitted.
     * @param stateCounts counts per thread state, in diagnostic order; empty when the mapper output is unsupported.
     * @param unsupportedMapper whether the configured mapper emitted a shape this panel cannot render.
     * @since 2.2.0
     */
    @ReflectiveAccess
    public record Body(int totalThreads, List<StateCount> stateCounts, boolean unsupportedMapper) {
    }

    /**
     * Rendering model of the detail view. Internal to the Thread Dump panel views.
     *
     * @param totalThreads number of entries the configured {@link ThreadInfoMapper} emitted.
     * @param stateCounts counts per thread state, in diagnostic order; empty when the mapper output is unsupported.
     * @param threads the thread rows; empty when the mapper output is unsupported.
     * @param unsupportedMapper whether the configured mapper emitted a shape this panel cannot render.
     * @since 2.2.0
     */
    @ReflectiveAccess
    public record ThreadDump(int totalThreads, List<StateCount> stateCounts, List<ThreadRow> threads, boolean unsupportedMapper) {

        static ThreadDump unsupportedMapper(int totalThreads) {
            return new ThreadDump(totalThreads, List.of(), List.of(), true);
        }
    }

    /**
     * Number of threads in a given state. Internal to the Thread Dump panel views.
     *
     * @param state the {@link Thread.State} name.
     * @param count how many threads are in that state.
     * @param badgeClass CSS class used to render the state badge.
     * @since 2.2.0
     */
    @ReflectiveAccess
    public record StateCount(String state, int count, String badgeClass) {
    }

    /**
     * A single thread of the dump. Internal to the Thread Dump panel views.
     *
     * @param threadId the thread id.
     * @param threadName the thread name.
     * @param state the thread state.
     * @param daemon whether the thread is a daemon thread.
     * @param lockName the lock the thread is blocked on or waiting for, if any.
     * @param lockOwnerName the name of the thread owning that lock, if known.
     * @param lockOwnerId the id of the thread owning that lock, or {@code -1}.
     * @param blockedCount total number of times the thread has been blocked, as text.
     * @param blockedTime total blocked time in milliseconds, as text; meaningless unless {@code hasBlockedTime}.
     * @param hasBlockedTime whether the JVM measures blocked time.
     * @param waitedCount total number of times the thread has waited, as text.
     * @param waitedTime total waited time in milliseconds, as text; meaningless unless {@code hasWaitedTime}.
     * @param hasWaitedTime whether the JVM measures waited time.
     * @param stackTrace the stack frames; always empty on the dashboard summary path.
     * @param badgeClass CSS class used to render the state badge.
     * @param blocked whether the thread is {@link Thread.State#BLOCKED}.
     * @param hasLockOwner whether lock ownership information is available.
     * @since 2.2.0
     */
    @ReflectiveAccess
    public record ThreadRow(
        long threadId,
        String threadName,
        Thread.State state,
        boolean daemon,
        @Nullable String lockName,
        @Nullable String lockOwnerName,
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
        @Nullable String lockName,
        @Nullable String lockOwnerName,
        long lockOwnerId,
        long blockedCount,
        long blockedTime,
        long waitedCount,
        long waitedTime,
        List<String> stackTrace
    ) {
        static ThreadSnapshot from(@NonNull ThreadInfo threadInfo) {
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
                Arrays.stream(threadInfo.getStackTrace())
                    .map(StackTraceElement::toString)
                    .toList()
            );
        }
    }
}
