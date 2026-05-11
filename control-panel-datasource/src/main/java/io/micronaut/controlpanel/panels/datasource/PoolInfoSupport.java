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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import io.micronaut.core.annotation.Internal;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Shared helpers for pool option display.
 */
@Internal
final class PoolInfoSupport {

    static final String UNKNOWN = "Unknown";

    private PoolInfoSupport() {
    }

    static PoolInfo.PoolOptionGroup group(String title, PoolInfo.PoolOption... options) {
        return new PoolInfo.PoolOptionGroup(title, List.of(options));
    }

    static PoolInfo.PoolOption option(String label, Object value) {
        return new PoolInfo.PoolOption(label, display(value));
    }

    static PoolInfo.PoolOption durationMillis(String label, long value) {
        return new PoolInfo.PoolOption(label, displayDuration(Duration.ofMillis(value)));
    }

    static PoolInfo.PoolOption durationSeconds(String label, long value) {
        return new PoolInfo.PoolOption(label, displayDuration(Duration.ofSeconds(value)));
    }

    static String display(Object value) {
        if (value == null) {
            return UNKNOWN;
        }
        if (value instanceof String stringValue) {
            return stringValue.isBlank() ? UNKNOWN : stringValue;
        }
        return String.valueOf(value);
    }

    static String displayProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return UNKNOWN;
        }
        return String.join(", ", properties.stringPropertyNames().stream().sorted().toList());
    }

    static String displayKeys(Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return UNKNOWN;
        }
        return String.join(", ", values.keySet().stream().map(String::valueOf).sorted().toList());
    }

    static String displayType(Object value) {
        if (value == null) {
            return UNKNOWN;
        }
        return value.getClass().getName();
    }

    static String displayDuration(Duration duration) {
        if (duration.isNegative()) {
            return UNKNOWN;
        }
        if (duration.isZero()) {
            return "Disabled";
        }
        long millis = duration.toMillis();
        if (millis % 60_000 == 0) {
            return (millis / 60_000) + "m";
        }
        if (millis % 1_000 == 0) {
            return (millis / 1_000) + "s";
        }
        return millis + "ms";
    }

    static int safeInt(SqlIntSupplier supplier) {
        try {
            return Math.max(0, supplier.getAsInt());
        } catch (SQLException e) {
            return 0;
        }
    }

    static long safeLong(SqlLongSupplier supplier) {
        try {
            return Math.max(0, supplier.getAsLong());
        } catch (SQLException e) {
            return 0;
        }
    }

    static Duration safeDuration(SqlDurationSupplier supplier) {
        try {
            Duration duration = supplier.get();
            return duration == null ? Duration.ZERO : duration;
        } catch (SQLException e) {
            return Duration.ZERO;
        }
    }

    static boolean safeBoolean(SqlBooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (SQLException e) {
            return false;
        }
    }

    static String safeString(SqlStringSupplier supplier) {
        try {
            return display(supplier.get());
        } catch (SQLException e) {
            return UNKNOWN;
        }
    }

    @FunctionalInterface
    interface SqlIntSupplier {
        int getAsInt() throws SQLException;
    }

    @FunctionalInterface
    interface SqlLongSupplier {
        long getAsLong() throws SQLException;
    }

    @FunctionalInterface
    interface SqlDurationSupplier {
        Duration get() throws SQLException;
    }

    @FunctionalInterface
    interface SqlBooleanSupplier {
        boolean getAsBoolean() throws SQLException;
    }

    @FunctionalInterface
    interface SqlStringSupplier {
        String get() throws SQLException;
    }
}
