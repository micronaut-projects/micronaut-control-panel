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
     */
    String getTitle();


    /**
     * The model is used to render the body of the control panel.
     */
    Map<String, Object> getModel();

    /**
     * {@link View} that will be used to render the body of the control panel.
     */
    default View getBody() {
        return new View("/views/" + getName() + "/body");
    }


    /**
     * {@link View} that will be used to render the body of the control panel when it has been
     * selected.
     */
    default View getDetailedView() {
        return new View("/views/" + getName() + "/detail");
    }

    /**
     * Badge text to be displayed in the header of the card UI element.
     */
    default String getBadge() {
        return EMPTY_STRING;
    }

    /**
     * Icon class of the card UI element.
     */
    default String getIcon() {
        return "fa-cog";
    }

    /**
     * The category of the control panel.
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
