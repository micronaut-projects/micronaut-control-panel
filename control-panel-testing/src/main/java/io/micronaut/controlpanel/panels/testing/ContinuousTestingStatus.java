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
package io.micronaut.controlpanel.panels.testing;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the Continuous Testing panel knows of the continuous testing of the project.
 *
 * @param running Whether a continuous testing process of the project is known to run
 * @param mayBeRunning Whether the process may run without the panel being able to tell: its last report was written
 *                     without a live server, as when the application's LiveReload server holds the port, and does not
 *                     name its process
 * @param reportUrl The address of the live report, when a running process serves it
 * @param reportFile The report page in the file system, when there is one
 * @param project The project the report belongs to
 * @param updatedAt When the report was last written, as an ISO-8601 instant
 * @param runInProgress Whether a test run is in progress
 * @param finishedInRun How many tests of the run in progress have finished
 * @param lastRun The last finished run, when there is one
 * @param history The latest finished runs, most recent first
 * @param failures The tests whose latest result is a failure or an error, at most the configured number
 * @param failureCount How many tests failed or errored in all
 * @param compileFailure The diagnostics of a change that does not compile, when there is one
 * @param commands The commands that start continuous testing, the one of the project's build tool first
 * @since 2.3.0
 */
@ReflectiveAccess
public record ContinuousTestingStatus(
    boolean running,
    boolean mayBeRunning,
    @Nullable String reportUrl,
    @Nullable String reportFile,
    @Nullable String project,
    @Nullable String updatedAt,
    boolean runInProgress,
    int finishedInRun,
    @Nullable Run lastRun,
    List<Run> history,
    List<Failure> failures,
    int failureCount,
    @Nullable List<String> compileFailure,
    List<Command> commands
) {

    /**
     * @return Whether a report of the project was found
     */
    public boolean reportFound() {
        return reportFile != null || reportUrl != null;
    }

    /**
     * @return Whether more tests failed than are listed
     */
    public boolean moreFailures() {
        return failureCount > failures.size();
    }

    /**
     * The text of the panel's badge.
     *
     * @return The badge
     */
    public String badge() {
        if (compileFailure != null) {
            return "Does not compile";
        }
        if (runInProgress) {
            return "Running tests";
        }
        if (lastRun != null) {
            if (lastRun.problems() > 0) {
                return lastRun.problems() + " failing";
            }
            return lastRun.cancelled() ? "Cancelled" : "Passing";
        }
        if (running) {
            return "Running";
        }
        return mayBeRunning ? "Unknown" : "Not running";
    }

    /**
     * A finished test run.
     *
     * @param passed How many tests passed
     * @param failed How many tests failed
     * @param errored How many tests errored
     * @param skipped How many tests were skipped
     * @param durationMillis How long the run took
     * @param selection Which tests the run selected
     * @param startedAt When the run started, as an ISO-8601 instant
     * @param cancelled Whether the run was cancelled
     */
    @ReflectiveAccess
    public record Run(int passed, int failed, int errored, int skipped, long durationMillis,
                      @Nullable String selection, @Nullable String startedAt, boolean cancelled) {

        /**
         * @return How many tests ran
         */
        public int total() {
            return passed + failed + errored + skipped;
        }

        /**
         * @return How many tests failed or errored
         */
        public int problems() {
            return failed + errored;
        }

        /**
         * @return Whether every test that ran passed, or was skipped
         */
        public boolean successful() {
            return problems() == 0 && !cancelled;
        }
    }

    /**
     * A test whose latest result is a failure or an error.
     *
     * @param className The test class
     * @param displayName The test
     * @param status {@code failed} or {@code errored}
     * @param message The message of the failure
     */
    @ReflectiveAccess
    public record Failure(String className, String displayName, String status, @Nullable String message) {
    }

    /**
     * A command that starts continuous testing.
     *
     * @param tool The build tool
     * @param command The command to run in a terminal of its own, in the project directory
     * @param detected Whether the project uses this build tool
     */
    @ReflectiveAccess
    public record Command(String tool, String command, boolean detected) {
    }
}
