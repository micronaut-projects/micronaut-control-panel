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
package io.micronaut.controlpanel.panels.email;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelLoader;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads one email panel per stable sender name.
 */
@Context
@Requires(beans = ControlPanelConfiguration.class)
public class EmailControlPanelLoader implements ControlPanelLoader {

    private final EmailSenderRegistry registry;
    private final EmailDiagnosticsAnalyzer analyzer;
    private final ControlPanelConfiguration configuration;

    EmailControlPanelLoader(EmailSenderRegistry registry,
                            EmailDiagnosticsAnalyzer analyzer,
                            @Named(EmailControlPanel.NAME) ControlPanelConfiguration configuration) {
        this.registry = registry;
        this.analyzer = analyzer;
        this.configuration = configuration;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <CP extends ControlPanel<?>> List<CP> loadControlPanels() {
        List<EmailSenderDescriptor> descriptors = registry.descriptors();
        if (descriptors.isEmpty()) {
            return (List<CP>) List.of(new EmptyEmailControlPanel(analyzer, configuration));
        }
        List<EmailControlPanel> panels = new ArrayList<>();
        for (EmailSenderDescriptor descriptor : descriptors) {
            panels.add(new EmailControlPanel(descriptor, analyzer, configuration));
        }
        return (List<CP>) panels;
    }
}
