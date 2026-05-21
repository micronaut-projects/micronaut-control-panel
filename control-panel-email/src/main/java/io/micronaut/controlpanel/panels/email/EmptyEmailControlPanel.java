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

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;

/**
 * Empty-state panel shown when Micronaut Email is present but no senders are configured.
 */
final class EmptyEmailControlPanel extends AbstractControlPanel<EmailDiagnosticModel> {

    private final EmailDiagnosticsAnalyzer analyzer;

    EmptyEmailControlPanel(EmailDiagnosticsAnalyzer analyzer, ControlPanelConfiguration configuration) {
        super(EmailControlPanel.NAME, configuration);
        this.analyzer = analyzer;
    }

    @Override
    public String getTitle() {
        return "Email";
    }

    @Override
    public EmailDiagnosticModel getBody() {
        return analyzer.empty();
    }

    @Override
    public View getBodyView() {
        return new View("/views/email/body");
    }

    @Override
    public View getDetailedView() {
        return new View("/views/email/detail");
    }

    @Override
    public ControlPanel.Category getCategory() {
        return EmailControlPanel.CATEGORY;
    }

    @Override
    public String getIcon() {
        return EmailControlPanel.DEFAULT_ICON_CLASS;
    }
}
