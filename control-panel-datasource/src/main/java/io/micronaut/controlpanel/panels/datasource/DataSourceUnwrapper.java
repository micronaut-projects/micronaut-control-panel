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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Strips Micronaut Data's {@link DelegatingDataSource} connection-management advice so the panel can
 * reach the underlying pool and open connections outside a Micronaut connection scope.
 *
 * <p>Registered only when {@code micronaut-data-connection-jdbc} is on the classpath — i.e. the
 * application uses Micronaut Data, whose {@code ContextualAwareDataSource} wraps every datasource.
 * Otherwise no such advice exists and {@link DataSourceService} uses the datasource directly. The
 * {@code @Requires(classes = ...)} guard keeps the dependency optional ({@code compileOnly}): when
 * the class is absent this bean definition is skipped without a {@code NoClassDefFoundError}.
 */
@Internal
@Singleton
@Requires(classes = DelegatingDataSource.class)
final class DataSourceUnwrapper {

    DataSource unwrap(DataSource dataSource) {
        return DelegatingDataSource.unwrapDataSource(dataSource);
    }
}
