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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.email.TransactionalEmailSender;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of the Micronaut Email senders and their configuration.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = TransactionalEmailSender.class)
@Requires(property = EmailControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class EmailControlPanel extends AbstractControlPanel<EmailInfo> {

    public static final String NAME = "email";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final EmailInspector inspector;

    /**
     * @param inspector the email inspector
     * @param configuration the panel configuration
     */
    public EmailControlPanel(EmailInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public EmailInfo getBody() {
        return inspector.inspect();
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }
}
