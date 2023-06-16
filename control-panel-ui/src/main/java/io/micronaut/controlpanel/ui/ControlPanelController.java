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
package io.micronaut.controlpanel.ui;

import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.views.View;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Control panel web controller to render the UI.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Controller("/control-panel")
public class ControlPanelController {

    private final ControlPanelRepository repository;

    private final ApplicationConfiguration applicationConfiguration;
    private final Environment environment;

    public ControlPanelController(ControlPanelRepository repository, ApplicationConfiguration applicationConfiguration, Environment environment) {
        this.repository = repository;
        this.applicationConfiguration = applicationConfiguration;
        this.environment = environment;
    }

    /**
     * Renders the index view.
     *
     * @return the model
     */
    @View("layout")
    @Get
    public Map<String, Object> index() {
        return byCategory(ControlPanel.Category.MAIN.id());
    }

    /**
     * Renders the category view.
     *
     * @param categoryId the category id.
     *
     * @return the model
     */
    @View("layout")
    @Get("/categories/{categoryId}")
    public Map<String, Object> byCategory(String categoryId) {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        Optional<ControlPanel.Category> optionalCategory = repository.findCategoryById(categoryId);
        List<ControlPanel> controlPanels = repository.findAllByCategory(categoryId);
        return Map.of(
            "micronautVersion", VersionUtils.getMicronautVersion(),
            "applicationName", applicationConfiguration.getName().orElse("(unnamed)"),
            "activeEnvironments", environment.getActiveNames(),
            "controlPanels", controlPanels,
            "categories", categories,
            "currentCategory", optionalCategory.get(),
            "contentView", "index"
        );

    }

    /**
     * Renders the control panel detailed view.
     *
     * @param controlPanelName the control panel name.
     *
     * @return the model
     */
    @View("layout")
    @Get("/{controlPanelName}")
    public Map<String, Object> detail(String controlPanelName) {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        Map<String, Object> response = new HashMap<>();
        response.put("micronautVersion", VersionUtils.getMicronautVersion());
        response.put("applicationName", applicationConfiguration.getName().orElse("(unnamed)"));
        response.put("activeEnvironments", environment.getActiveNames());
        response.put("categories", categories);
        response.put("contentView", "detail");

        Optional<ControlPanel> optionalControlPanel = repository.findByName(controlPanelName);
        if (optionalControlPanel.isPresent()) {
            Optional<ControlPanel.Category> optionalCategory = repository.findCategoryById(optionalControlPanel.get().getCategory().id());
            response.putAll(Map.of(
                "controlPanel", optionalControlPanel.get(),
                "currentCategory", optionalCategory.get()
            ));
        }
        return response;
    }
}
