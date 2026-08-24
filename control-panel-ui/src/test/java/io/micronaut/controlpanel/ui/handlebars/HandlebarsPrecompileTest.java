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
package io.micronaut.controlpanel.ui.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordingHandlebars extends Handlebars {
    final java.util.List<String> compiled = new java.util.ArrayList<>(); // retain FQNs in test to avoid extra imports

    RecordingHandlebars(TemplateLoader loader) { super(loader); }
    @Override public Template compile(String location) throws java.io.IOException {
        compiled.add(location);
        return super.compile(location);
    }
}

class HandlebarsPrecompileTest {

    @Test
    void precompilesTemplatesUnderControlPanelViewsPrefix() {
        HandlebarsHelperRegistrar registrar = new HandlebarsHelperRegistrar();

        ClassPathTemplateLoader loader = new ClassPathTemplateLoader("controlpanelviews", ".hbs");
        RecordingHandlebars handlebars = new RecordingHandlebars(loader);

        registrar.configure(handlebars);

        assertTrue(handlebars.compiled.contains("controlpanelviews/index"));
        assertTrue(handlebars.compiled.contains("controlpanelviews/layout"));
        assertTrue(handlebars.compiled.size() >= 2);
    }
}
