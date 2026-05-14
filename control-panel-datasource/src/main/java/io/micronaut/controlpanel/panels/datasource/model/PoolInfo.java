/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.datasource.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Connection pool metadata displayed in the datasource panel.
 *
 * @param provider      The pool provider name
 * @param poolName      The configured pool name
 * @param implementation The concrete datasource implementation class
 * @param stats         The current connection pool statistics
 * @param optionGroups  The connection pool option groups
 */
@ReflectiveAccess
public record PoolInfo(String provider,
                       String poolName,
                       String implementation,
                       PoolStats stats,
                       List<PoolOptionGroup> optionGroups) {

    /**
     * Connection pool status metrics.
     *
     * @param active         Active borrowed connections
     * @param idle           Idle available connections
     * @param total          Total open connections
     * @param max            Maximum configured connections
     * @param min            Minimum configured connections
     * @param awaiting       Threads or requests waiting for a connection
     * @param activeWidth    Active segment width for the status bar
     * @param idleWidth      Idle segment width for the status bar
     * @param remainingWidth Remaining capacity segment width for the status bar
     * @param usageLabel     Human-readable usage label
     */
    @ReflectiveAccess
    public record PoolStats(int active,
                            int idle,
                            int total,
                            int max,
                            int min,
                            int awaiting,
                            String activeWidth,
                            String idleWidth,
                            String remainingWidth,
                            String usageLabel) {

        public static PoolStats of(int active, int idle, int total, int max, int min, int awaiting) {
            int denominator = max > 0 ? max : Math.max(total, active + idle);
            int activePercent = percentage(active, denominator);
            int idlePercent = percentage(idle, denominator);
            int usedPercent = Math.min(100, activePercent + idlePercent);
            int remainingPercent = Math.max(0, 100 - usedPercent);
            String usageLabel = max > 0 ? active + " / " + max : String.valueOf(active);
            return new PoolStats(
                active,
                idle,
                total,
                max,
                min,
                awaiting,
                activePercent + "%",
                idlePercent + "%",
                remainingPercent + "%",
                usageLabel
            );
        }

        public boolean hasCapacity() {
            return max > 0;
        }

        private static int percentage(int value, int total) {
            if (value <= 0 || total <= 0) {
                return 0;
            }
            return Math.min(100, Math.round((value * 100f) / total));
        }
    }

    /**
     * A grouped set of pool configuration options.
     *
     * @param title   The group title
     * @param options The group options
     */
    @ReflectiveAccess
    public record PoolOptionGroup(String title, List<PoolOption> options) {
    }

    /**
     * A displayable pool configuration option.
     *
     * @param label The option label
     * @param value The option value
     */
    @ReflectiveAccess
    public record PoolOption(String label, String value) {
    }
}
