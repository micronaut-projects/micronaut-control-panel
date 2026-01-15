/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.openapi;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelLoader;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.micronaut.controlpanel.panels.openapi.OpenAPIViewerControlPanel.NAME;

/**
 * Loader for OpenAPI viewer control panels.
 * Dynamically discovers enabled OpenAPI viewers from openapi.properties and creates control panels for each.
 * The loader reads the micronaut.openapi.views.spec property to determine which viewers are enabled,
 * then matches them with the corresponding static resource mappings to construct viewer URLs.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Context
@Requires(beans = ControlPanelConfiguration.class)
public class OpenAPIControlPanelLoader implements ControlPanelLoader {

    private static final String OPENAPI_PROPERTIES = "META-INF/swagger/openapi.properties";
    private static final String VIEWS_SPEC_PROPERTY = "micronaut.openapi.views.spec";
    private static final String STATIC_RESOURCES_PREFIX = "micronaut.router.static-resources.";
    private static final String MAPPING_SUFFIX = ".mapping";

    private static final Map<String, String> VIEWER_NAMES = Map.of(
        "swagger-ui", "Swagger UI",
        "redoc", "Redoc",
        "rapidoc", "RapiDoc",
        "scalar", "Scalar",
        "openapi-explorer", "OpenAPI Explorer"
    );

    private final ControlPanelConfiguration configuration;
    private final ApplicationContext applicationContext;
    private final ResourceLoader resourceLoader;

    /**
     * Constructor.
     *
     * @param configuration the control panel configuration
     * @param applicationContext the application context
     * @param resourceLoader the resource loader for reading configuration files
     */
    public OpenAPIControlPanelLoader(@Named(NAME) ControlPanelConfiguration configuration,
                                     ApplicationContext applicationContext,
                                     ResourceLoader resourceLoader) {
        this.configuration = configuration;
        this.applicationContext = applicationContext;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public List<OpenAPIViewerControlPanel> loadControlPanels() {
        var enabledViewers = getEnabledViewers();
        var staticMappings = getStaticResourceMappings();

        var controlPanels = new ArrayList<OpenAPIViewerControlPanel>();
        for (String viewer : enabledViewers) {
            var url = findViewerUrl(viewer, staticMappings);
            if (url != null) {
                var title = VIEWER_NAMES.getOrDefault(viewer, capitalizeViewer(viewer));
                var panelConfig = createConfiguration(title);
                controlPanels.add(new OpenAPIViewerControlPanel(viewer, url, panelConfig));
            }
        }

        return controlPanels;
    }

    /**
     * Reads the openapi.properties file to determine which viewers are enabled.
     *
     * @return list of enabled viewer names
     */
    @NonNull
    private List<String> getEnabledViewers() {
        var enabledViewers = new ArrayList<String>();

        // First check system properties
        var systemViewsSpec = System.getProperty(VIEWS_SPEC_PROPERTY);
        if (systemViewsSpec != null) {
            enabledViewers.addAll(parseViewsSpec(systemViewsSpec));
        }

        // Then check openapi.properties file
        var openapiPropsUrl = resourceLoader.getResource(OPENAPI_PROPERTIES);
        if (openapiPropsUrl.isPresent()) {
            var properties = new Properties();
            try (var input = openapiPropsUrl.get().openStream()) {
                properties.load(input);
                var viewsSpec = properties.getProperty(VIEWS_SPEC_PROPERTY);
                if (viewsSpec != null) {
                    enabledViewers.addAll(parseViewsSpec(viewsSpec));
                }
            } catch (IOException e) {
                // Ignore, no OpenAPI viewers configured
            }
        }

        var uniqueViewers = new ArrayList<String>();
        for (String viewer : enabledViewers) {
            if (!uniqueViewers.contains(viewer)) {
                uniqueViewers.add(viewer);
            }
        }
        return uniqueViewers;
    }

    /**
     * Parses the micronaut.openapi.views.spec property value to extract enabled viewers.
     * Format: "swagger-ui.enabled=true,redoc.enabled=true,..."
     *
     * @param viewsSpec the views specification string
     * @return list of enabled viewer names
     */
    @NonNull
    private List<String> parseViewsSpec(@NonNull String viewsSpec) {
        var enabledViewers = new ArrayList<String>();
        var pairs = viewsSpec.split(",");

        for (String pair : pairs) {
            var trimmed = pair.trim();
            if (trimmed.endsWith(".enabled=true")) {
                var viewerName = trimmed.substring(0, trimmed.indexOf(".enabled=true"));
                enabledViewers.add(viewerName);
            }
        }

        return enabledViewers;
    }

    /**
     * Retrieves static resource mappings from application configuration.
     * Format: micronaut.router.static-resources.{name}.mapping
     *
     * @return map of resource names to their URL mappings
     */
    @NonNull
    private Map<String, String> getStaticResourceMappings() {
        var mappings = new HashMap<String, String>();
        var environment = applicationContext.getEnvironment();

        // Get all properties starting with micronaut.router.static-resources
        for (String key : environment.getPropertyEntries(STATIC_RESOURCES_PREFIX)) {
            if (key.endsWith(MAPPING_SUFFIX)) {
                var value = environment.getProperty(STATIC_RESOURCES_PREFIX + key, String.class).orElse(null);
                if (value != null) {
                    // Extract the resource name from the key
                    // e.g., "swagger.mapping" -> "swagger"
                    var endIdx = key.lastIndexOf(MAPPING_SUFFIX);
                    if (endIdx > 0) {
                        var resourceName = key.substring(0, endIdx);
                        var mapping = value;
                        // Remove /** suffix if present
                        if (mapping.endsWith("/**")) {
                            mapping = mapping.substring(0, mapping.length() - 3);
                        }
                        mappings.put(resourceName, mapping);
                    }
                }
            }
        }

        return mappings;
    }

    /**
     * Finds the URL for a specific viewer by matching it with static resource mappings.
     * Uses heuristics to match viewer names with resource mapping names.
     *
     * @param viewer the viewer name (e.g., "swagger-ui", "redoc")
     * @param staticMappings map of resource names to URL mappings
     * @return the viewer URL, or null if not found
     */
    private String findViewerUrl(@NonNull String viewer, @NonNull Map<String, String> staticMappings) {
        // Direct match
        if (staticMappings.containsKey(viewer)) {
            return staticMappings.get(viewer) + "/";
        }

        // Try without hyphens
        var viewerNoHyphen = viewer.replace("-", "");
        for (var entry : staticMappings.entrySet()) {
            var mappingName = entry.getKey().replace("-", "").toLowerCase();
            if (mappingName.equals(viewerNoHyphen.toLowerCase())) {
                return entry.getValue() + "/";
            }
        }

        // Try partial match (contains)
        for (var entry : staticMappings.entrySet()) {
            var mappingName = entry.getKey().toLowerCase();
            if (mappingName.contains(viewer.toLowerCase()) || viewer.toLowerCase().contains(mappingName)) {
                return entry.getValue() + "/";
            }
        }

        return null;
    }

    /**
     * Creates a control panel configuration for a viewer.
     *
     * @param title the viewer title
     * @return the control panel configuration
     */
    @NonNull
    private ControlPanelConfiguration createConfiguration(@NonNull String title) {
        var config = new ControlPanelConfiguration(NAME);
        config.setTitle(title);
        config.setIcon(configuration.getIcon());
        config.setOrder(configuration.getOrder());
        config.setEnabled(configuration.isEnabled());
        return config;
    }

    /**
     * Capitalizes a viewer name for display.
     *
     * @param viewer the viewer name
     * @return the capitalized name
     */
    @NonNull
    private String capitalizeViewer(@NonNull String viewer) {
        return viewer.substring(0, 1).toUpperCase() + viewer.substring(1).replace("-", " ");
    }
}
