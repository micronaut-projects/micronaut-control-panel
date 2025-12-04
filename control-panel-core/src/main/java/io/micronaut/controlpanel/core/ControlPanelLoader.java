package io.micronaut.controlpanel.core;

import java.util.List;

public interface ControlPanelLoader {

    <CP extends ControlPanel> List<CP> loadControlPanels();
}
