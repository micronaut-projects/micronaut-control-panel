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
package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.panels.flyway.model.FlywayBody;
import io.micronaut.controlpanel.panels.flyway.model.FlywayConfigurationStatus;
import io.micronaut.controlpanel.panels.flyway.model.FlywayMigration;
import io.micronaut.controlpanel.panels.flyway.model.FlywayStateSummary;
import jakarta.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds read-only Flyway status models from public Flyway APIs.
 */
@Singleton
@Requires(classes = Flyway.class)
public class FlywayStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(FlywayStatusService.class);
    private static final DateTimeFormatter INSTALLED_ON_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);
    private static final List<StateGroup> STATE_GROUPS = List.of(
        StateGroup.SUCCESS,
        StateGroup.PENDING,
        StateGroup.FAILED,
        StateGroup.FUTURE,
        StateGroup.MISSING,
        StateGroup.OUT_OF_ORDER,
        StateGroup.IGNORED,
        StateGroup.UNKNOWN
    );

    private final BeanContext beanContext;
    private final FlywayPanelConfiguration configuration;

    /**
     * Creates a Flyway status service.
     *
     * @param beanContext bean context used to discover Flyway beans lazily
     * @param configuration display configuration
     */
    public FlywayStatusService(BeanContext beanContext, FlywayPanelConfiguration configuration) {
        this.beanContext = beanContext;
        this.configuration = configuration;
    }

    /**
     * Builds the current Flyway status body.
     *
     * @return current Flyway status body
     */
    public FlywayBody getBody() {
        List<FlywayConfigurationStatus> configurations = beanContext.mapOfType(Flyway.class)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> readConfiguration(entry.getKey(), entry.getValue()))
            .toList();

        List<FlywayMigration> migrations = configurations.stream()
            .flatMap(status -> status.migrations().stream())
            .toList();
        List<FlywayStateSummary> stateSummaries = summarize(migrations);
        int totalMigrations = migrations.size();
        int availableConfigurations = (int) configurations.stream().filter(FlywayConfigurationStatus::available).count();

        return new FlywayBody(
            configurations,
            stateSummaries,
            configurations.size(),
            totalMigrations,
            availableConfigurations,
            configurations.size() - availableConfigurations,
            configuration.isShowChecksums(),
            configuration.isShowInstalledBy()
        );
    }

    private FlywayConfigurationStatus readConfiguration(String name, Flyway flyway) {
        try {
            MigrationInfoService info = flyway.info();
            List<FlywayMigration> migrations = Arrays.stream(info.all())
                .filter(Objects::nonNull)
                .sorted()
                .map(FlywayStatusService::migration)
                .toList();
            MigrationInfo current = info.current();
            List<FlywayStateSummary> stateSummaries = summarize(migrations);
            return new FlywayConfigurationStatus(
                name,
                true,
                statusLabel(migrations),
                statusBadgeClass(migrations),
                displayVersion(current),
                latestApplied(migrations),
                migrations.size(),
                migrations.isEmpty() ? "Flyway is configured, but it has not reported any migrations for this datasource." : "",
                migrations,
                stateSummaries
            );
        } catch (RuntimeException e) {
            LOG.debug("Unable to read Flyway migration info for bean '{}'", name, e);
            return new FlywayConfigurationStatus(
                name,
                false,
                "Unavailable",
                "badge-destructive",
                "",
                "",
                0,
                "Flyway is configured, but migration metadata could not be read for this datasource.",
                List.of(),
                emptySummaries()
            );
        }
    }

    private static FlywayMigration migration(MigrationInfo info) {
        StateGroup group = stateGroup(info.getState());
        return new FlywayMigration(
            displayVersion(info),
            string(info.getDescription()),
            info.getType() == null ? "" : info.getType().name(),
            string(info.getScript()),
            info.getState() == null ? "UNKNOWN" : info.getState().name(),
            stateLabel(info.getState()),
            group.id(),
            group.badgeClass(),
            string(info.getInstalledRank()),
            info.getInstalledOn() == null ? "" : INSTALLED_ON_FORMAT.format(info.getInstalledOn().toInstant()),
            string(info.getExecutionTime()),
            string(info.getChecksum()),
            string(info.getInstalledBy())
        );
    }

    static StateGroup stateGroup(MigrationState state) {
        if (state == null) {
            return StateGroup.UNKNOWN;
        }
        String name = state.name();
        if (state.isFailed() || name.contains("FAILED")) {
            return StateGroup.FAILED;
        }
        if (name.startsWith("FUTURE")) {
            return StateGroup.FUTURE;
        }
        if (name.startsWith("MISSING")) {
            return StateGroup.MISSING;
        }
        if (name.equals("OUT_OF_ORDER")) {
            return StateGroup.OUT_OF_ORDER;
        }
        if (name.contains("IGNORED") || name.equals("BELOW_BASELINE") || name.equals("ABOVE_TARGET")) {
            return StateGroup.IGNORED;
        }
        if (name.equals("PENDING") || name.equals("AVAILABLE")) {
            return StateGroup.PENDING;
        }
        if (name.equals("SUCCESS") || name.equals("BASELINE") || name.equals("UNDONE") || name.equals("SUPERSEDED") || name.equals("OUTDATED") || name.equals("DELETED")) {
            return StateGroup.SUCCESS;
        }
        return StateGroup.UNKNOWN;
    }

    private static List<FlywayStateSummary> summarize(List<FlywayMigration> migrations) {
        Map<String, Long> counts = migrations.stream()
            .collect(Collectors.groupingBy(FlywayMigration::stateGroup, LinkedHashMap::new, Collectors.counting()));
        return STATE_GROUPS.stream()
            .map(group -> new FlywayStateSummary(group.id(), group.label(), group.badgeClass(), counts.getOrDefault(group.id(), 0L).intValue()))
            .toList();
    }

    private static List<FlywayStateSummary> emptySummaries() {
        return summarize(List.of());
    }

    private static String statusLabel(List<FlywayMigration> migrations) {
        if (migrations.isEmpty()) {
            return "No migrations";
        }
        Map<String, Integer> counts = countByGroup(migrations);
        if (counts.getOrDefault(StateGroup.FAILED.id(), 0) > 0) {
            return "Failed";
        }
        if (counts.getOrDefault(StateGroup.PENDING.id(), 0) > 0) {
            return "Pending";
        }
        if (counts.getOrDefault(StateGroup.FUTURE.id(), 0) > 0
            || counts.getOrDefault(StateGroup.MISSING.id(), 0) > 0
            || counts.getOrDefault(StateGroup.OUT_OF_ORDER.id(), 0) > 0) {
            return "Attention";
        }
        if (counts.getOrDefault(StateGroup.UNKNOWN.id(), 0) > 0) {
            return "Unknown";
        }
        return "Current";
    }

    private static String statusBadgeClass(List<FlywayMigration> migrations) {
        return switch (statusLabel(migrations)) {
            case "Failed" -> "badge-destructive";
            case "Pending", "Attention" -> "badge-light";
            case "Unknown", "No migrations" -> "badge-secondary";
            default -> "badge-primary";
        };
    }

    private static Map<String, Integer> countByGroup(List<FlywayMigration> migrations) {
        return migrations.stream()
            .collect(Collectors.groupingBy(FlywayMigration::stateGroup, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private static String latestApplied(List<FlywayMigration> migrations) {
        return migrations.stream()
            .filter(migration -> !migration.installedRank().isBlank())
            .max(Comparator.comparingInt(migration -> Integer.parseInt(migration.installedRank())))
            .map(migration -> migration.version().isBlank() ? migration.description() : migration.version())
            .orElse("");
    }

    private static String displayVersion(MigrationInfo info) {
        if (info == null) {
            return "";
        }
        return Optional.ofNullable(info.getVersion())
            .map(Object::toString)
            .orElse("repeatable");
    }

    private static String stateLabel(MigrationState state) {
        if (state == null) {
            return "Unknown";
        }
        String displayName = state.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return Arrays.stream(state.name().split("_"))
            .map(part -> part.substring(0, 1).toUpperCase(Locale.ENGLISH) + part.substring(1).toLowerCase(Locale.ENGLISH))
            .collect(Collectors.joining(" "));
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    enum StateGroup {
        SUCCESS("success", "Success", "badge-primary"),
        PENDING("pending", "Pending", "badge-light"),
        FAILED("failed", "Failed", "badge-destructive"),
        FUTURE("future", "Future", "badge-light"),
        MISSING("missing", "Missing", "badge-light"),
        OUT_OF_ORDER("out-of-order", "Out of order", "badge-light"),
        IGNORED("ignored", "Ignored", "badge-secondary"),
        UNKNOWN("unknown", "Unknown", "badge-secondary");

        private final String id;
        private final String label;
        private final String badgeClass;

        StateGroup(String id, String label, String badgeClass) {
            this.id = id;
            this.label = label;
            this.badgeClass = badgeClass;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        String badgeClass() {
            return badgeClass;
        }
    }
}
