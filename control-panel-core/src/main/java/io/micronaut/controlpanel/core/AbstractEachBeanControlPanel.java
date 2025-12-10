/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.core;

import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * Base class for control panels that are created for each bean of a specific type.
 * This class provides common functionality for control panels that need to display
 * information about individual beans.
 *
 * @param <B> the body type
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@ReflectiveAccess
public abstract class AbstractEachBeanControlPanel<B> extends AbstractControlPanel<B> {

    /**
     * Constructor.
     *
     * @param controlPanelName the control panel name
     * @param configuration the control panel configuration
     */
    protected AbstractEachBeanControlPanel(final String controlPanelName, final ControlPanelConfiguration configuration) {
        super(controlPanelName, configuration);
    }

    /**
     * Returns the name of the bean this control panel represents.
     *
     * @return the bean name
     */
    protected abstract String getBeanName();

    /**
     * Returns the name of the panel category.
     *
     * @return the panel name
     */
    protected abstract String getPanelName();

    /**
     * Returns the title for this control panel, which is the bean name.
     *
     * @return the title
     */
    @Override
    public String getTitle() {
        return getBeanName();
    }

    /**
     * Returns the unique name for this control panel, combining the panel name and bean name.
     *
     * @return the control panel name
     */
    @Override
    public String getName() {
        return getPanelName() + "-" + getBeanName();
    }

    /**
     * Returns the view for the control panel body.
     *
     * @return the body view
     */
    @Override
    public View getBodyView() {
        return new View("/views/" + getPanelName() + "/body");
    }

    /**
     * Returns the view for the control panel detailed view.
     *
     * @return the detailed view
     */
    @Override
    public View getDetailedView() {
        return new View("/views/" + getPanelName() + "/detail");
    }
}
