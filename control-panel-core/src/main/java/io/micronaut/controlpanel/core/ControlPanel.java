package io.micronaut.controlpanel.core;

import io.micronaut.core.naming.Described;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanel extends Named, Described, Ordered, Toggleable {
}
