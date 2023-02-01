package io.micronaut.controlpanel.core;

import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;

import java.util.Map;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanel extends Ordered, Toggleable {

    String getTitle();

    String getBody();

    Map<String, Object> getModel();

    View getView();

    Category getCategory();

    record Category (String name, String iconClass) {
        public static final Category MAIN = new Category("Main", "fa-tachometer-alt");
    }

    record View (String file) {
        public static final View CARD = new View("/views/card");
    }

}
