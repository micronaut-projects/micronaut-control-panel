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

import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel for a Micronaut Email sender.
 */
public class EmailControlPanel extends AbstractEachBeanControlPanel<EmailDiagnosticModel> {

    public static final String NAME = "email";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final String DEFAULT_ICON_CLASS = "fas fa-envelope";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "Email", DEFAULT_ICON_CLASS);

    private final EmailSenderDescriptor descriptor;
    private final EmailDiagnosticsAnalyzer analyzer;

    EmailControlPanel(EmailSenderDescriptor descriptor,
                      EmailDiagnosticsAnalyzer analyzer,
                      ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.descriptor = descriptor;
        this.analyzer = analyzer;
    }

    @Override
    protected String getBeanName() {
        return descriptor.name();
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public String getTitle() {
        return "Email: " + descriptor.name();
    }

    @Override
    public EmailDiagnosticModel getBody() {
        return analyzer.analyze(descriptor);
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getIcon() {
        return DEFAULT_ICON_CLASS;
    }
}
