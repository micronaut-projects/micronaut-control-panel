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
package io.micronaut.controlpanel.panels.graalpy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.python.PythonContextExecutor;
import io.micronaut.context.python.PythonPoolStatistics;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Stand-in for Core's pooled executor. Borrowing a context always fails so the read-only guarantee is provable
 * without a GraalPy runtime on the test classpath.
 */
@Singleton
@Requires(property = "spec.name", pattern = "GraalPyControlPanel(Empty)?Test")
class TestPythonContextExecutor implements PythonContextExecutor {

    private final AtomicInteger withContextCalls = new AtomicInteger();

    private volatile PythonPoolStatistics statistics =
        new PythonPoolStatistics(true, 4, 3, 2, 1, 10, 2, 120, 90, false);
    private volatile RuntimeException failure;

    @Override
    public <T> T withContext(Function<Context, T> function) {
        withContextCalls.incrementAndGet();
        throw new AssertionError("Panel rendering must not borrow a GraalPy context");
    }

    @Override
    public PythonPoolStatistics statistics() {
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        return statistics;
    }

    int withContextCalls() {
        return withContextCalls.get();
    }

    void statistics(PythonPoolStatistics statistics) {
        this.statistics = statistics;
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }
}
