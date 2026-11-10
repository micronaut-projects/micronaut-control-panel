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
package io.micronaut.controlpanel.panels.jmx;

import io.micronaut.configuration.jmx.JmxConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of the MBean server and the management endpoint MBeans that Micronaut JMX registers.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = JmxConfiguration.class)
@Requires(property = JmxControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class JmxControlPanel extends AbstractControlPanel<JmxInfo> {

    public static final String NAME = "jmx";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final JmxInspector inspector;

    /**
     * @param inspector the JMX inspector
     * @param configuration the panel configuration
     */
    public JmxControlPanel(JmxInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public JmxInfo getBody() {
        return inspector.inspect();
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }
}
