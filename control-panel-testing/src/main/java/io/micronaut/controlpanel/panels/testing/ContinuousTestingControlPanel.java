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
package io.micronaut.controlpanel.panels.testing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * The Continuous Testing panel of the Testing category: whether continuous testing runs for the project, a link to its
 * live report, the latest results, and how to start it in a terminal of its own.
 *
 * <p>Continuous testing runs in a process of its own, so the panel only shows in development mode, where the
 * application runs beside it: {@code micronaut.dev.enabled}.</p>
 *
 * @since 2.3.0
 */
@Singleton
@Requires(property = ContinuousTestingControlPanel.DEVELOPMENT_MODE_PROPERTY, value = StringUtils.TRUE)
@Requires(property = ContinuousTestingControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ContinuousTestingControlPanel extends AbstractControlPanel<ContinuousTestingStatus> {

    /**
     * The name of the panel.
     */
    public static final String NAME = "continuous-testing";

    /**
     * The category of the panel.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("testing", "Testing", "fas fa-vial-circle-check");

    /**
     * The property that enables the panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The property a development launcher sets to switch on development mode.
     */
    public static final String DEVELOPMENT_MODE_PROPERTY = "micronaut.dev.enabled";

    private final ContinuousTestingStatusResolver resolver;

    /**
     * @param configuration The panel's configuration
     * @param resolver Finds the status of continuous testing
     */
    public ContinuousTestingControlPanel(@Named(NAME) ControlPanelConfiguration configuration,
                                         ContinuousTestingStatusResolver resolver) {
        super(NAME, configuration);
        this.resolver = resolver;
    }

    @Override
    public ContinuousTestingStatus getBody() {
        return resolver.resolve();
    }

    @Override
    public String getBadge() {
        // a page renders the badge and the body together: the resolver reuses its probe of the live report
        return resolver.resolve().badge();
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getDetailLinkName() {
        return "Status and results";
    }
}
