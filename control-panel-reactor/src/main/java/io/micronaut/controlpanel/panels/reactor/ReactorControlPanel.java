/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.reactor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Control panel that displays Reactor route, client, and readiness diagnostics.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
@Requires(classes = {Mono.class, Flux.class})
@Requires(property = ReactorControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ReactorControlPanel extends AbstractControlPanel<ReactorDiagnosticsBody> {

    /**
     * Reactor control panel name.
     */
    public static final String NAME = "reactor";

    /**
     * Configuration property used to enable or disable this panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    private static final ControlPanel.Category CATEGORY = new ControlPanel.Category("reactor", "Reactor", "fa-wave-square", 25);

    private final ReactorRouteAnalyzer routeAnalyzer;
    private final ReactorClientInspector clientInspector;
    private final ReactorReadinessInspector readinessInspector;

    /**
     * Creates a Reactor control panel.
     *
     * @param routeAnalyzer reactive route analyzer
     * @param clientInspector Reactor HTTP client inspector
     * @param readinessInspector Reactor readiness inspector
     * @param configuration control panel configuration
     */
    public ReactorControlPanel(
            ReactorRouteAnalyzer routeAnalyzer,
            ReactorClientInspector clientInspector,
            ReactorReadinessInspector readinessInspector,
            @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.routeAnalyzer = routeAnalyzer;
        this.clientInspector = clientInspector;
        this.readinessInspector = readinessInspector;
    }

    @Override
    public ReactorDiagnosticsBody getBody() {
        List<ReactorDiagnosticsBody.SectionError> errors = new ArrayList<>();
        List<ReactorDiagnosticsBody.ReactiveRoute> routes = safeRoutes(errors);
        List<ReactorDiagnosticsBody.ReactorClient> clients = safeClients(errors);
        List<ReactorDiagnosticsBody.ReadinessCheck> readiness = safeReadiness(errors);
        return new ReactorDiagnosticsBody(routes, clients, readiness, List.copyOf(errors));
    }

    @Override
    public String getBadge() {
        try {
            return String.valueOf(routeAnalyzer.routes().size());
        } catch (RuntimeException e) {
            return "0";
        }
    }

    @Override
    public Category getCategory() {
        return CATEGORY;
    }

    private List<ReactorDiagnosticsBody.ReactiveRoute> safeRoutes(List<ReactorDiagnosticsBody.SectionError> errors) {
        try {
            return routeAnalyzer.routes();
        } catch (RuntimeException e) {
            errors.add(new ReactorDiagnosticsBody.SectionError("Routes", diagnosticMessage("Reactor route diagnostics are unavailable.", e)));
            return List.of();
        }
    }

    private List<ReactorDiagnosticsBody.ReactorClient> safeClients(List<ReactorDiagnosticsBody.SectionError> errors) {
        try {
            return clientInspector.clients();
        } catch (RuntimeException e) {
            errors.add(new ReactorDiagnosticsBody.SectionError("HTTP clients", diagnosticMessage("Reactor HTTP client diagnostics are unavailable.", e)));
            return List.of();
        }
    }

    private List<ReactorDiagnosticsBody.ReadinessCheck> safeReadiness(List<ReactorDiagnosticsBody.SectionError> errors) {
        try {
            return readinessInspector.readiness();
        } catch (RuntimeException e) {
            errors.add(new ReactorDiagnosticsBody.SectionError("Readiness", diagnosticMessage("Reactor readiness diagnostics are unavailable.", e)));
            return List.of();
        }
    }

    private static String diagnosticMessage(String message, RuntimeException e) {
        return e.getMessage() == null ? message : message + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
