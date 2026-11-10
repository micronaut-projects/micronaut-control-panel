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
import io.micronaut.core.annotation.Internal;
import io.micronaut.flyway.FlywayConfigurationProperties;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the migration history of each Flyway configuration. Micronaut Flyway registers a {@link Flyway} bean named
 * after each configuration when it runs, which is the same bean the {@code /flyway} management endpoint reads.
 *
 * <p>Only {@link Flyway#info()} is called. Nothing is migrated, cleaned or repaired.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = FlywayConfigurationProperties.class)
public class FlywayInspector {

    private static final Logger LOG = LoggerFactory.getLogger(FlywayInspector.class);
    private static final DateTimeFormatter INSTALLED_ON = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public FlywayInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return one entry per Flyway configuration, sorted by name
     */
    public List<FlywaySchema> inspect() {
        return beanContext.getBeansOfType(FlywayConfigurationProperties.class).stream()
            .sorted(Comparator.comparing(FlywayConfigurationProperties::getNameQualifier))
            .map(this::inspect)
            .toList();
    }

    private FlywaySchema inspect(FlywayConfigurationProperties configuration) {
        String name = configuration.getNameQualifier();
        if (!configuration.isEnabled()) {
            return FlywaySchema.withoutMigrations(name, FlywaySchema.Status.DISABLED);
        }
        Optional<Flyway> flyway = beanContext.findBean(Flyway.class, Qualifiers.byName(name));
        if (flyway.isEmpty()) {
            return FlywaySchema.withoutMigrations(name, FlywaySchema.Status.NOT_RUN);
        }
        try {
            return read(name, flyway.get().info());
        } catch (RuntimeException e) {
            // The exception can carry the JDBC URL, so it is only logged
            LOG.debug("Unable to read the Flyway migration history of '{}'", name, e);
            return FlywaySchema.withoutMigrations(name, FlywaySchema.Status.UNREADABLE);
        }
    }

    static FlywaySchema read(String name, MigrationInfoService info) {
        List<MigrationInfo> all = Arrays.stream(info.all()).filter(Objects::nonNull).toList();
        MigrationInfo current = info.current();
        return new FlywaySchema(
            name,
            FlywaySchema.Status.READ,
            current == null || current.getVersion() == null ? null : current.getVersion().getVersion(),
            all.stream().map(FlywayInspector::migration).toList(),
            all.stream().filter(migration -> isApplied(migration.getState())).count(),
            all.stream().filter(migration -> migration.getState() == MigrationState.PENDING).count(),
            all.stream().filter(migration -> migration.getState() != null && migration.getState().isFailed()).count()
        );
    }

    private static boolean isApplied(@Nullable MigrationState state) {
        return state != null && state.isApplied() && !state.isFailed();
    }

    private static FlywayMigration migration(MigrationInfo info) {
        MigrationState state = info.getState();
        return new FlywayMigration(
            info.getVersion() == null ? null : info.getVersion().getVersion(),
            Objects.toString(info.getDescription(), ""),
            info.getType() == null ? "" : info.getType().name(),
            Objects.toString(info.getScript(), ""),
            state == null ? "Unknown" : state.getDisplayName(),
            badgeClass(state),
            info.getInstalledOn() == null ? null : INSTALLED_ON.format(Instant.ofEpochMilli(info.getInstalledOn().getTime())),
            info.getInstalledBy(),
            info.getExecutionTime()
        );
    }

    static String badgeClass(@Nullable MigrationState state) {
        if (state == null) {
            return "badge-secondary";
        }
        if (state.isFailed()) {
            return "badge-destructive";
        }
        if (state == MigrationState.PENDING) {
            return "badge-primary";
        }
        if (state.isApplied() && !state.isResolved()) {
            // Applied, but the migration is no longer on the classpath, or is newer than the application knows about
            return "badge-outline";
        }
        return "badge-secondary";
    }
}
