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

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.context.python.annotation.PythonModule;
import io.micronaut.context.python.scope.ContextPooled;
import jakarta.inject.Singleton;

/**
 * Hand-written stand-ins for the Java bridge stubs that {@code micronaut-inject-python} generates.
 *
 * <p>These carry the real Core annotations on purpose: if Core renames or reshapes them, this module's build
 * fails here instead of silently emptying the panel in user applications.</p>
 */
final class TestPythonBeans {

    private TestPythonBeans() {
    }

    @Singleton
    @PythonModule(moduleName = "dealer", packageName = "cards")
    @Requires(property = "spec.name", value = "GraalPyControlPanelTest")
    static class DealerModule {

        @Executable
        public String deal(String name, int count) {
            throw new AssertionError("Panel rendering must not call a Python bridge method");
        }
    }

    @ContextPooled
    @PythonClass(packageName = "pricing", rootName = "PricingRules", nestedMemberNames = {"quote"}, displayName = "Pricing rules", cacheKey = "pricing.PricingRules")
    @Requires(property = "spec.name", value = "GraalPyControlPanelTest")
    static class PricingRules {

        public String quote(String order) {
            throw new AssertionError("Panel rendering must not call a Python bridge method");
        }
    }
}
