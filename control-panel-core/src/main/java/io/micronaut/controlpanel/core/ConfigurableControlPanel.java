/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.naming.Named;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;

/**
 * Common configuration properties for control panels.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ConfigurableControlPanel extends Named, Ordered, Toggleable {


    /**
     * @return whether this control panel is enabled or not.
     */
    @Override
    boolean isEnabled();

    /**
     * The title is displayed in the header of the card UI element.
     *
     * @return the title of the control panel.
     */
    String getTitle();

    /**
     * Icon CSS class of the card UI element.
     *
     * @return the icon class of the control panel.
     */
    String getIcon();
}
