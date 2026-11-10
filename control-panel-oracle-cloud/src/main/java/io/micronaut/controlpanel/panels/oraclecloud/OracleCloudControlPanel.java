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
package io.micronaut.controlpanel.panels.oraclecloud;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.oraclecloud.core.OracleCloudCoreFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of how the application connects to Oracle Cloud: the authentication provider, the region, the OCI
 * SDK clients and their configuration. It never creates an authentication provider or a client, and it does not send
 * requests to Oracle Cloud.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = OracleCloudCoreFactory.class)
@Requires(property = OracleCloudControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class OracleCloudControlPanel extends AbstractControlPanel<OracleCloudInfo> {

    public static final String NAME = "oracle-cloud";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final OracleCloudInspector inspector;

    /**
     * @param inspector the Oracle Cloud inspector
     * @param configuration the panel configuration
     */
    public OracleCloudControlPanel(OracleCloudInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public OracleCloudInfo getBody() {
        return inspector.inspect();
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }
}
