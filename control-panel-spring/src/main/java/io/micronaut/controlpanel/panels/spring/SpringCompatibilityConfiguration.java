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
package io.micronaut.controlpanel.panels.spring;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration for the Spring Compatibility Control Panel.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@ConfigurationProperties(SpringCompatibilityControlPanel.ENABLED_PROPERTY_PREFIX)
public class SpringCompatibilityConfiguration {

    private boolean includeClasspathSummary = true;
    private boolean includeUnsupportedFeatureWarnings = true;
    private boolean showAnnotationValues;

    /**
     * @return whether to show detected Micronaut Spring modules
     */
    public boolean isIncludeClasspathSummary() {
        return includeClasspathSummary;
    }

    /**
     * @param includeClasspathSummary whether to show detected Micronaut Spring modules
     */
    public void setIncludeClasspathSummary(boolean includeClasspathSummary) {
        this.includeClasspathSummary = includeClasspathSummary;
    }

    /**
     * @return whether unsupported-feature warnings are included
     */
    public boolean isIncludeUnsupportedFeatureWarnings() {
        return includeUnsupportedFeatureWarnings;
    }

    /**
     * @param includeUnsupportedFeatureWarnings whether unsupported-feature warnings are included
     */
    public void setIncludeUnsupportedFeatureWarnings(boolean includeUnsupportedFeatureWarnings) {
        this.includeUnsupportedFeatureWarnings = includeUnsupportedFeatureWarnings;
    }

    /**
     * @return whether annotation values are shown
     */
    public boolean isShowAnnotationValues() {
        return showAnnotationValues;
    }

    /**
     * @param showAnnotationValues whether annotation values are shown
     */
    public void setShowAnnotationValues(boolean showAnnotationValues) {
        this.showAnnotationValues = showAnnotationValues;
    }
}
