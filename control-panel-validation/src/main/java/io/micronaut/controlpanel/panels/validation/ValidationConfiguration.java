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
package io.micronaut.controlpanel.panels.validation;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the validation diagnostics panel.
 *
 * @since 2.0.0
 */
@ConfigurationProperties(ValidationConfiguration.PREFIX)
@ReflectiveAccess
public class ValidationConfiguration {

    public static final String PREFIX = "micronaut.control-panel.validation";

    private List<String> includePackages = new ArrayList<>();
    private List<String> excludePackages = new ArrayList<>();
    private AttributeMode showConstraintAttributes = AttributeMode.SAFE;

    /**
     * Package prefixes included in diagnostics. Empty means application packages after framework defaults are excluded.
     */
    public List<String> getIncludePackages() {
        return includePackages;
    }

    public void setIncludePackages(List<String> includePackages) {
        this.includePackages = includePackages == null ? new ArrayList<>() : includePackages;
    }

    /**
     * Package prefixes excluded from diagnostics.
     */
    public List<String> getExcludePackages() {
        return excludePackages;
    }

    public void setExcludePackages(List<String> excludePackages) {
        this.excludePackages = excludePackages == null ? new ArrayList<>() : excludePackages;
    }

    /**
     * Controls how constraint annotation attributes are displayed.
     */
    public AttributeMode getShowConstraintAttributes() {
        return showConstraintAttributes;
    }

    public void setShowConstraintAttributes(AttributeMode showConstraintAttributes) {
        this.showConstraintAttributes = showConstraintAttributes == null ? AttributeMode.SAFE : showConstraintAttributes;
    }

    public enum AttributeMode {
        NONE,
        SAFE,
        ALL
    }
}
