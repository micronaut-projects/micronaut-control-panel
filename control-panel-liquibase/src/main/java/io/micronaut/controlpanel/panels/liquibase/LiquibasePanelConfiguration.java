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
package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Liquibase control panel rendering options.
 */
@ConfigurationProperties(LiquibaseControlPanel.CONFIGURATION_PREFIX)
public class LiquibasePanelConfiguration {

    public static final boolean DEFAULT_SHOW_CHECKSUMS = true;
    public static final boolean DEFAULT_SHOW_DEPLOYMENT_IDS = true;

    private boolean showChecksums = DEFAULT_SHOW_CHECKSUMS;
    private boolean showDeploymentIds = DEFAULT_SHOW_DEPLOYMENT_IDS;

    /**
     * @return whether change-set checksums should be displayed
     */
    public boolean isShowChecksums() {
        return showChecksums;
    }

    /**
     * @param showChecksums whether change-set checksums should be displayed
     */
    public void setShowChecksums(boolean showChecksums) {
        this.showChecksums = showChecksums;
    }

    /**
     * @return whether Liquibase deployment IDs should be displayed
     */
    public boolean isShowDeploymentIds() {
        return showDeploymentIds;
    }

    /**
     * @param showDeploymentIds whether Liquibase deployment IDs should be displayed
     */
    public void setShowDeploymentIds(boolean showDeploymentIds) {
        this.showDeploymentIds = showDeploymentIds;
    }
}
