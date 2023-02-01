package io.micronaut.controlpanel.core;

import io.micronaut.core.naming.Named;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;

import java.util.Map;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanel extends Named, Ordered, Toggleable {

    String getTitle();

    String getBody();

    Map<String, Object> getModel();

    View getView();

    default View getDetailedView() {
        return new View("/views/" + getName());
    }

    Category getCategory();

    record Category (String id, String name, String iconClass) {
        public static final Category MAIN = new Category("main", "Dashboard", "fa-tachometer-alt");
    }

    record View (String file) {
        public static final View CARD = new View("/views/card");
    }

}
