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
package example;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

/**
 * Registers a small tagged metric set so the example app always exposes meaningful metrics in the control panel.
 */
@Singleton
@Requires(beans = MeterRegistry.class)
class ExampleMetricsPublisher {

    private final MeterRegistry meterRegistry;

    ExampleMetricsPublisher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "web")
            .tag("status", "success")
            .register(meterRegistry)
            .increment(4);

        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "batch")
            .tag("status", "failure")
            .register(meterRegistry)
            .increment();
    }
}
