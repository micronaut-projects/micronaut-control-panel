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
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.views.View;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Control panel web controller to render the UI.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Controller(ControlPanelApi.PATH)
@Refreshable
public class ControlPanelController implements ControlPanelApi {

    private final ControlPanelRepository repository;
    private final String applicationName;
    private final Set<String> activeEnvironments;

    public ControlPanelController(ControlPanelRepository repository, ApplicationConfiguration applicationConfiguration, Environment environment) {
        this.repository = repository;
        this.applicationName = applicationConfiguration.getName().orElse("(unnamed)");
        this.activeEnvironments = environment.getActiveNames();
    }

    @View("layout")
    @Override
    public Model index() {
        return byCategory(ControlPanel.Category.MAIN.id());
    }

    @View("layout")
    @Override
    public Model byCategory(String categoryId) {
        Map<String, Object> extraProperties = new HashMap<>();
        var categories = repository.findAllCategories();

        var controlPanels = repository.findAllByCategory(categoryId);
        extraProperties.put("controlPanels", controlPanels);

        var optionalCategory = repository.findCategoryById(categoryId);
        optionalCategory.ifPresent(category -> extraProperties.put("currentCategory", category));

        return new Model(categories, applicationName, activeEnvironments, Model.ContentView.INDEX, extraProperties);
    }

    @View("layout")
    @Override
    public Model detail(String controlPanelName) {
        var categories = repository.findAllCategories();
        Map<String, Object> extraProperties = new HashMap<>();

        var optionalControlPanel = repository.findByName(controlPanelName);
        if (optionalControlPanel.isPresent()) {
            extraProperties.put("controlPanel", optionalControlPanel.get());
            var optionalCategory = repository.findCategoryById(optionalControlPanel.get().getCategory().id());
            optionalCategory.ifPresent(category -> extraProperties.put("currentCategory", category));
        }
        return new Model(categories, applicationName, activeEnvironments, Model.ContentView.DETAIL, extraProperties);
    }
}
