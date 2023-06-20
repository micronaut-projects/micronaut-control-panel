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

import java.util.Map;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * A control panel is a UI component that displays some information about the application.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanel extends Named, Ordered, Toggleable {


    /**
     * The title is displayed in the header of the card UI element.
     *
     * @return the title of the control panel.
     */
    String getTitle();


    /**
     * Used to render the body of the control panel.
     *
     * @return the model of the control panel.
     */
    Map<String, Object> getBody();

    /**
     * {@link View} that will be used to render the body of the control panel.
     *
     * @return the view of the control panel.
     */
    default View getBodyView() {
        return new View("/views/" + getName() + "/body");
    }

    /**
     * {@link View} that will be used to render the body of the control panel when it has been
     * selected.
     *
     * @return the view of the control panel.
     */
    default View getDetailedView() {
        return new View("/views/" + getName() + "/detail");
    }

    /**
     * Badge text to be displayed in the header of the card UI element.
     *
     * @return the badge text of the control panel.
     */
    default String getBadge() {
        return EMPTY_STRING;
    }

    /**
     * Icon class of the card UI element.
     *
     * @return the icon class of the control panel.
     */
    default String getIcon() {
        return "fa-cog";
    }

    /**
     * The category of the control panel.
     *
     * @return the category of the control panel.
     */
    Category getCategory();

    /**
     * Control panels are grouped by category. Categories are used to render the menu items of the
     * control panel UI.
     *
     * @param id category id, used in links.
     * @param name category name, displayed in the menu.
     * @param iconClass category icon class, displayed in the menu.
     * @param order category order, used to sort the menu items.
     */
    record Category (String id, String name, String iconClass, Integer order) {
        public static final Category MAIN = new Category("main", "Dashboard", "fa-tachometer-alt", Integer.MIN_VALUE);

        public Category(String id, String name, String iconClass) {
            this(id, name, iconClass, 0);
        }
    }

    /**
     * View to be rendered by the control panel.
     * @param file view path in the classpath.
     */
    record View (String file) {
        public static final View CARD = new View("/views/card");
    }

}
