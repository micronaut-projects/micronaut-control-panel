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
package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Preview-only R2DBC connection factory with unsupported metadata for screenshots.
 */
@Factory
@Requires(env = "r2dbc-preview")
final class R2dbcPreviewConnectionFactory {

    @Named("default")
    @Singleton
    ConnectionFactoryOptions defaultConnectionFactoryOptions() {
        return ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "h2")
            .option(ConnectionFactoryOptions.PROTOCOL, "mem")
            .option(ConnectionFactoryOptions.DATABASE, "preview")
            .option(ConnectionFactoryOptions.USER, "sa")
            .option(ConnectionFactoryOptions.PASSWORD, "screenshot-secret")
            .build();
    }

    @Named("unsupported")
    @Singleton
    ConnectionFactory unsupportedConnectionFactory() {
        return new UnsupportedConnectionFactory();
    }

    private static final class UnsupportedConnectionFactory implements ConnectionFactory {

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.error(new IllegalStateException("Preview connection factory should not be probed without a validation query"));
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "Unsupported";
        }
    }
}
