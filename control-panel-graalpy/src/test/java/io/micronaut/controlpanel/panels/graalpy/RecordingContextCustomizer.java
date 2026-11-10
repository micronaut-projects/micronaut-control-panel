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

import io.micronaut.context.python.GraalPyContextCustomizer;
import org.graalvm.polyglot.Context;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registered through {@code META-INF/services}. It counts construction and customization so tests can prove the
 * panel lists customizer types without ever loading or running them.
 */
public class RecordingContextCustomizer implements GraalPyContextCustomizer {

    private static final AtomicInteger INSTANTIATIONS = new AtomicInteger();
    private static final AtomicInteger CUSTOMIZATIONS = new AtomicInteger();

    public RecordingContextCustomizer() {
        INSTANTIATIONS.incrementAndGet();
    }

    static int instantiations() {
        return INSTANTIATIONS.get();
    }

    static int customizations() {
        return CUSTOMIZATIONS.get();
    }

    @Override
    public void customize(Context.Builder builder) {
        CUSTOMIZATIONS.incrementAndGet();
    }
}
