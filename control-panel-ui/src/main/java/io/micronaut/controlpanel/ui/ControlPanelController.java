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

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.controlpanel.ui.util.EndpointUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.management.endpoint.refresh.RefreshEndpoint;
import io.micronaut.management.endpoint.stop.ServerStopEndpoint;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.controlpanel.util.ControlPanelUtils.computeControlPanelPath;

/**
 * Control panel web controller to render the UI.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Controller(ControlPanelApi.PATH)
@ExecuteOn(TaskExecutors.BLOCKING)
public class ControlPanelController implements ControlPanelApi {

    private final ControlPanelRepository repository;
    private final String applicationName;
    private final Set<String> activeEnvironments;
    private final boolean canRefresh;
    private final boolean canStop;
    private final String appPath;
    private final String controlPanelPath;

    @Deprecated
    public ControlPanelController(ControlPanelRepository repository, BeanContext beanContext,
                                  @Nullable RefreshEndpoint refreshEndpoint,
                                  @Nullable ServerStopEndpoint stopEndpoint) {
        ApplicationConfiguration applicationConfiguration = beanContext.getBean(ApplicationConfiguration.class);
        Environment environment = beanContext.getBean(Environment.class);
        this.repository = repository;
        this.applicationName = applicationConfiguration.getName().orElse("(unnamed)");
        this.activeEnvironments = environment.getActiveNames();
        this.canRefresh = EndpointUtils.canRefresh(refreshEndpoint, beanContext);
        this.canStop = stopEndpoint != null;
        this.appPath = "/";
        this.controlPanelPath = ControlPanelModuleConfiguration.DEFAULT_PATH;
    }

    @Inject
    public ControlPanelController(ControlPanelRepository repository, BeanContext beanContext,
                                  @Nullable RefreshEndpoint refreshEndpoint,
                                  @Nullable ServerStopEndpoint stopEndpoint,
                                  ControlPanelModuleConfiguration configuration) {
        ApplicationConfiguration applicationConfiguration = beanContext.getBean(ApplicationConfiguration.class);
        HttpServerConfiguration  serverConfiguration = beanContext.getBean(HttpServerConfiguration.class);
        Environment environment = beanContext.getBean(Environment.class);
        this.repository = repository;
        this.applicationName = applicationConfiguration.getName().orElse("(unnamed)");
        this.activeEnvironments = environment.getActiveNames();
        this.canRefresh = EndpointUtils.canRefresh(refreshEndpoint, beanContext);
        this.canStop = stopEndpoint != null;
        this.appPath = Optional.ofNullable(serverConfiguration.getContextPath()).orElse("");
        this.controlPanelPath = computeControlPanelPath(appPath, configuration.getPath());
    }

    @Override
    public HttpResponse<ModelAndView<?>> index() {
        return byCategory(ControlPanel.Category.MAIN.id());
    }

    @Override
    public HttpResponse<ModelAndView<?>> byCategory(String categoryId) {
        Map<String, Object> extraProperties = new HashMap<>();
        var categories = repository.findAllCategories();

        var controlPanels = repository.findAllByCategory(categoryId);
        extraProperties.put("controlPanels", controlPanels);
        extraProperties.put("controlPanelPath", controlPanelPath);
        extraProperties.put("appPath", appPath);

        var optionalCategory = repository.findCategoryById(categoryId);

        if (optionalCategory.isPresent()) {
            extraProperties.put("currentCategory", optionalCategory.get());
            var model = new Model(categories, applicationName, activeEnvironments, Model.ContentView.INDEX,
                canRefresh, canStop, extraProperties);
            return HttpResponse.ok(new ModelAndView<>("layout", model));
        } else {
            return HttpResponse.notFound();
        }
    }

    @Override
    public HttpResponse<ModelAndView<?>> detail(String controlPanelName) {
        var categories = repository.findAllCategories();
        Map<String, Object> extraProperties = new HashMap<>();
        extraProperties.put("controlPanelPath", controlPanelPath);
        extraProperties.put("appPath", appPath);

        var optionalControlPanel = repository.findByName(controlPanelName);
        if (optionalControlPanel.isPresent()) {
            extraProperties.put("controlPanel", optionalControlPanel.get());
            var optionalCategory = repository.findCategoryById(optionalControlPanel.get().getCategory().id());
            optionalCategory.ifPresent(category -> extraProperties.put("currentCategory", category));
            var model = new Model(categories, applicationName, activeEnvironments, Model.ContentView.DETAIL,
                canRefresh, canStop, extraProperties);
            return HttpResponse.ok(new ModelAndView<>("layout", model));
        } else {
            return HttpResponse.notFound();
        }
    }
}
