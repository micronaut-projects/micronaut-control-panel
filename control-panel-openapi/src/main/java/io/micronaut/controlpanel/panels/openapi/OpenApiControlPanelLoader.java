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
package io.micronaut.controlpanel.panels.openapi;

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelLoader;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loader that discovers enabled OpenAPI viewers and creates a control panel per viewer.
 */
@Singleton
public class OpenApiControlPanelLoader implements ControlPanelLoader {

    // Precompile once; scanning property keys will use this repeatedly.
    private static final Pattern STATIC_RES_MAPPING =
        Pattern.compile("^micronaut\\.router\\.static-resources\\.([^.]+)\\.mapping$");

    private final BeanContext beanContext;
    private final Environment environment;

    public OpenApiControlPanelLoader(BeanContext beanContext, Environment environment) {
        this.beanContext = beanContext;
        this.environment = environment;
    }

    @Override
    public <CP extends ControlPanel<?>> List<CP> loadControlPanels() {
        String spec = resolveViewsSpec();
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        var enabledViewers = OpenApiSupport.parseEnabledViewers(spec);
        if (enabledViewers.isEmpty()) {
            return List.of();
        }
        Map<String, String> viewerToBase = inferViewerBaseMappings();
        List<ControlPanel<?>> panels = new ArrayList<>();
        for (String viewer : enabledViewers) {
            String base = viewerToBase.get(viewer);
            if (base == null) {
                // Fallback to conventional base if not discoverable
                base = OpenApiSupport.defaultBaseFor(viewer);
            }
            String href = ensureTrailingSlash(base);
            String panelName = "openapi-" + viewer;
            ControlPanelConfiguration cfg = resolveConfiguration(panelName);
            panels.add(new OpenApiViewerControlPanel(panelName, viewer, href, cfg));
        }
        @SuppressWarnings("unchecked")
        List<CP> cast = (List<CP>) panels;
        return cast;
    }

    private String resolveViewsSpec() {
        // Priority: system property, environment, then classpath openapi.properties
        String sys = System.getProperty(OpenApiSupport.OPENAPI_VIEWS_SPEC);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        var envOpt = environment.getProperty(OpenApiSupport.OPENAPI_VIEWS_SPEC, String.class);
        if (envOpt.isPresent() && !envOpt.get().isBlank()) {
            return envOpt.get();
        }
        // Try to load from classpath resource "openapi.properties"
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream("openapi.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                Object v = props.get(OpenApiSupport.OPENAPI_VIEWS_SPEC);
                if (v != null) {
                    return String.valueOf(v);
                }
            }
        } catch (IOException e) {
            // Do not suppress: surface issues reading configuration to aid debugging.
            throw new UncheckedIOException(e);
        }
        return null;
    }

    private static String ensureTrailingSlash(String path) {
        return path != null && path.endsWith("/") ? path : path + "/";
    }

    private ControlPanelConfiguration resolveConfiguration(String name) {
        // Obtain the per-panel configuration bean for this panel name
        // Users can override via micronaut.control-panel.panels."<name>".*
        return beanContext.getBean(ControlPanelConfiguration.class,
            Qualifiers.byName(name));
    }

    /**
     * Heuristically infer base mappings for static resources that correspond to viewers.
     * We look at micronaut.router.static-resources.*.mapping and try to match segment names.
     */
    private Map<String, String> inferViewerBaseMappings() {
        Map<String, String> viewerToBase = new LinkedHashMap<>();
        // Properties are flattened in Environment; pattern: micronaut.router.static-resources.<name>.mapping
        // We'll scan property sources by regex.
        for (var ps : environment.getPropertySources()) {
            for (String key : ps) {
                Matcher m = STATIC_RES_MAPPING.matcher(key);
                if (m.matches()) {
                    String resName = m.group(1);
                    Optional<String> mappingOpt = environment.getProperty(key, String.class);
                    mappingOpt.ifPresent(mapping -> {
                        String base = normalizeMappingBase(mapping);
                        String viewer = OpenApiSupport.inferViewerFromResourceName(resName);
                        if (viewer != null) {
                            viewerToBase.putIfAbsent(viewer, base);
                        }
                    });
                }
            }
        }
        return viewerToBase;
    }

    private static String normalizeMappingBase(String mapping) {
        // mapping can be like "/rapidoc/**" -> base "/rapidoc"
        String base = mapping;
        if (base.endsWith("/**")) {
            base = base.substring(0, base.length() - 3);
        }
        if (base.endsWith("/*")) {
            base = base.substring(0, base.length() - 2);
        }
        return base;
    }
}
