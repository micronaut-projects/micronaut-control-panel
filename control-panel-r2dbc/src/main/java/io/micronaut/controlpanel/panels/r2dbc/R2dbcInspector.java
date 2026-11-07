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
package io.micronaut.controlpanel.panels.r2dbc;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Collects the R2DBC connection factories of the application from the {@link ConnectionFactoryOptions} beans that
 * Micronaut R2DBC creates for each {@code r2dbc.datasources} entry.
 *
 * <p>Only an allow-list of options is read, so the password and driver-specific options, which can carry secrets, are
 * never shown. No connection is opened.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = ConnectionFactoryOptions.class)
public class R2dbcInspector {

    private static final Logger LOG = LoggerFactory.getLogger(R2dbcInspector.class);
    private static final boolean POOL_PRESENT = ClassUtils.isPresent("io.r2dbc.pool.ConnectionPool", R2dbcInspector.class.getClassLoader());

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public R2dbcInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return one entry per connection factory, sorted by name
     */
    public List<R2dbcFactoryInfo> inspect() {
        return beanContext.getBeanRegistrations(ConnectionFactoryOptions.class).stream()
            .map(this::inspect)
            .sorted(Comparator.comparing(R2dbcFactoryInfo::name))
            .toList();
    }

    private R2dbcFactoryInfo inspect(BeanRegistration<ConnectionFactoryOptions> registration) {
        String name = registration.getIdentifier().getName();
        ConnectionFactoryOptions options = registration.getBean();
        Optional<ConnectionFactory> factory = beanContext.findBean(ConnectionFactory.class, Qualifiers.byName(name));
        String product = null;
        R2dbcFactoryInfo.Pool pool = null;
        if (factory.isPresent()) {
            try {
                product = factory.get().getMetadata().getName();
                pool = POOL_PRESENT ? R2dbcPoolMetrics.read(factory.get()) : null;
            } catch (RuntimeException e) {
                LOG.debug("Unable to read the metadata of R2DBC connection factory '{}'", name, e);
            }
        }
        return new R2dbcFactoryInfo(
            name,
            product,
            value(options, ConnectionFactoryOptions.DRIVER),
            value(options, ConnectionFactoryOptions.PROTOCOL),
            value(options, ConnectionFactoryOptions.HOST),
            value(options, ConnectionFactoryOptions.PORT),
            value(options, ConnectionFactoryOptions.DATABASE),
            value(options, ConnectionFactoryOptions.USER),
            value(options, ConnectionFactoryOptions.SSL),
            value(options, ConnectionFactoryOptions.CONNECT_TIMEOUT),
            pool
        );
    }

    private static @Nullable String value(ConnectionFactoryOptions options, Option<?> option) {
        Object value = options.getValue(option);
        return value == null ? null : value.toString();
    }
}
