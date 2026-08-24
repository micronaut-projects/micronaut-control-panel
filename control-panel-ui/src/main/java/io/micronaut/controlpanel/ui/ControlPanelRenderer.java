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
package io.micronaut.controlpanel.ui;

import com.github.jknack.handlebars.Handlebars;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Renders the control panel's bundled Handlebars templates without using the
 * host application's Micronaut Views renderer.
 */
@Internal
@Singleton
public final class ControlPanelRenderer {
    private static final String LAYOUT_TEMPLATE = "controlpanelviews/layout";

    private final Handlebars handlebars;

    public ControlPanelRenderer(@Named("controlPanel") Handlebars handlebars) {
        this.handlebars = handlebars;
    }

    String render(Model model) {
        return render(LAYOUT_TEMPLATE, model);
    }

    /**
     * Renders a bundled Control Panel Handlebars template.
     *
     * @param template The classpath-relative template name without the {@code .hbs} suffix
     * @param model The data model supplied to the template
     * @return The rendered HTML
     */
    public String render(String template, Object model) {
        try {
            return handlebars.compile(template).apply(model);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to render the control panel", e);
        }
    }
}
