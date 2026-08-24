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
import io.micronaut.controlpanel.core.security.ControlPanelWriteAccessEvaluator;
import io.micronaut.controlpanel.ui.util.EndpointUtils;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.management.endpoint.refresh.RefreshEndpoint;
import io.micronaut.management.endpoint.stop.ServerStopEndpoint;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final @Nullable RefreshEndpoint refreshEndpoint;
    private final @Nullable ServerStopEndpoint stopEndpoint;
    private final String appPath;
    private final String controlPanelPath;
    private final ControlPanelWriteAccessEvaluator writeAccessEvaluator;
    private final ControlPanelRenderer renderer;

    /**
     *
     * @param repository Control Panel Repository
     * @param beanContext Bean Context
     * @param refreshEndpoint Refresh endpoint
     * @param stopEndpoint Stop Endpoint
     * @param configuration Control Panel Module Configuration
     * @param writeAccessEvaluator WriteAccess Evaluator
     * @param renderer Control Panel Renderer
     */
    @Inject
    public ControlPanelController(ControlPanelRepository repository,
                                  BeanContext beanContext,
                                  @Nullable RefreshEndpoint refreshEndpoint,
                                  @Nullable ServerStopEndpoint stopEndpoint,
                                  ControlPanelModuleConfiguration configuration,
                                  ControlPanelWriteAccessEvaluator writeAccessEvaluator,
                                  ControlPanelRenderer renderer) {
        ApplicationConfiguration applicationConfiguration = beanContext.getBean(ApplicationConfiguration.class);
        HttpServerConfiguration  serverConfiguration = beanContext.getBean(HttpServerConfiguration.class);
        Environment environment = beanContext.getBean(Environment.class);
        this.repository = repository;
        this.applicationName = applicationConfiguration.getName().orElse("(unnamed)");
        this.activeEnvironments = environment.getActiveNames();
        this.canRefresh = EndpointUtils.canRefresh(refreshEndpoint);
        this.canStop = stopEndpoint != null;
        this.refreshEndpoint = refreshEndpoint;
        this.stopEndpoint = stopEndpoint;
        this.appPath = Optional.ofNullable(serverConfiguration.getContextPath()).orElse("");
        this.controlPanelPath = computeControlPanelPath(appPath, configuration.getPath());
        this.writeAccessEvaluator = writeAccessEvaluator;
        this.renderer = renderer;
    }

    @Override
    public HttpResponse<String> index(HttpRequest<?> request) {
        return byCategoryModel(ControlPanel.Category.MAIN.id(), request)
            .map(this::htmlResponse)
            .orElseGet(HttpResponse::notFound);
    }

    @Override
    public HttpResponse<String> byCategory(String categoryId, HttpRequest<?> request) {
        return byCategoryModel(categoryId, request)
            .map(this::htmlResponse)
            .orElseGet(HttpResponse::notFound);
    }

    final Optional<Model> byCategoryModel(String categoryId, HttpRequest<?> request) {
        var common = buildCommonData(request);
        var controlPanels = repository.findAllByCategory(categoryId);
        var optionalCategory = repository.findCategoryById(categoryId);

        if (optionalCategory.isPresent()) {
            var extraProperties = new HashMap<>(common.baseExtra());
            extraProperties.put("controlPanels", controlPanels);
            extraProperties.put("currentCategory", optionalCategory.get());
            var model = new Model(common.categories(), applicationName, activeEnvironments, Model.ContentView.INDEX,
                canRefresh, canStop, extraProperties);
            return Optional.of(model);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public HttpResponse<String> detail(String controlPanelName, HttpRequest<?> request) {
        return detailModel(controlPanelName, request)
            .map(this::htmlResponse)
            .orElseGet(HttpResponse::notFound);
    }

    final Optional<Model> detailModel(String controlPanelName, HttpRequest<?> request) {
        var common = buildCommonData(request);
        var optionalControlPanel = repository.findByName(controlPanelName);
        if (optionalControlPanel.isPresent()) {
            var extraProperties = new HashMap<>(common.baseExtra());
            extraProperties.put("controlPanel", optionalControlPanel.get());
            var optionalCategory = repository.findCategoryById(optionalControlPanel.get().getCategory().id());
            optionalCategory.ifPresent(category -> extraProperties.put("currentCategory", category));
            var model = new Model(common.categories(), applicationName, activeEnvironments, Model.ContentView.DETAIL,
                canRefresh, canStop, extraProperties);
            return Optional.of(model);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public HttpResponse<Object> refresh(@Nullable RefreshRequest request) {
        if (refreshEndpoint == null || !canRefresh) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(refreshEndpoint.refresh(request != null && request.force()));
    }

    @Override
    public HttpResponse<Object> stop() {
        if (stopEndpoint == null || !canStop) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(stopEndpoint.stop());
    }

    private HttpResponse<String> htmlResponse(Model model) {
        return HttpResponse.ok(renderer.render(model)).contentType(MediaType.TEXT_HTML_TYPE);
    }

    private CommonData buildCommonData(HttpRequest<?> request) {
        var categories = repository.findAllCategories();
        var categoryCount = categories
            .stream()
            .collect(Collectors.toMap(
                ControlPanel.Category::id,
                category -> repository.countByCategoryId(category.id()),
                (a, b) -> a,
                LinkedHashMap::new
            ));
        Map<String, Object> baseExtra = new LinkedHashMap<>();
        baseExtra.put("controlPanelPath", controlPanelPath);
        baseExtra.put("appPath", appPath);
        baseExtra.put("categoryCount", categoryCount);
        baseExtra.put("activeEnvironments", activeEnvironments);
        baseExtra.put("hasActiveEnvironments", !activeEnvironments.isEmpty());
        baseExtra.put("writeAccess", writeAccessEvaluator.evaluate(request));
        repository.findByName("health")
            .map(ControlPanel::getBody)
            .ifPresent(body -> baseExtra.put("applicationHealth", body));
        return new CommonData(categories, baseExtra);
    }

    private record CommonData(List<ControlPanel.Category> categories, Map<String, Object> baseExtra) { }
}
