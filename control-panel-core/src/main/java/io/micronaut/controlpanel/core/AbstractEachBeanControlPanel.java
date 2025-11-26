package io.micronaut.controlpanel.core;

import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;

@ReflectiveAccess
public abstract class AbstractEachBeanControlPanel<B> extends AbstractControlPanel<B> {

    protected AbstractEachBeanControlPanel(final String controlPanelName, final ControlPanelConfiguration configuration) {
        super(controlPanelName, configuration);
    }

    protected abstract String getBeanName();

    protected abstract String getPanelName();

    @Override
    public String getTitle() {
        return getBeanName();
    }

    @Override
    public String getName() {
        return getPanelName() + "-" + getBeanName();
    }

    @Override
    public View getBodyView() {
        return new View("/views/" + getPanelName() + "/body");
    }

    @Override
    public View getDetailedView() {
        return new View("/views/" + getPanelName() + "/detail");
    }
}
