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
package io.micronaut.controlpanel.panels.graalpy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.python.PythonContextExecutor;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Read-only diagnostics for the Micronaut Core Python (GraalPy) integration.
 *
 * <p>The panel renders bean-definition metadata, the context pool snapshot, context configuration and
 * packaged virtual-filesystem metadata. It never borrows a pooled context, evaluates Python, calls a generated
 * bridge method, or creates a polyglot engine or context.</p>
 */
@Singleton
@Requires(classes = PythonContextExecutor.class)
@Requires(bean = PythonContextExecutor.class)
public class GraalPyControlPanel extends AbstractControlPanel<GraalPyControlPanel.Body> {

    /**
     * Panel name.
     */
    public static final String NAME = "graalpy";
    /**
     * Configuration prefix for this panel.
     */
    public static final String PANEL_PREFIX = ControlPanelConfiguration.PREFIX + "." + NAME;
    /**
     * Property that enables or disables the GraalPy panel.
     */
    public static final String ENABLED_PROPERTY = PANEL_PREFIX + ".enabled";
    /**
     * GraalPy category used by the sidebar.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "GraalPy", "fa-brands fa-python");

    private final PythonBeanScanner beanScanner;
    private final PythonRuntimeInspector runtimeInspector;
    private final GraalPyVfsMetadataReader vfsMetadataReader;

    /**
     * Constructor.
     *
     * @param beanScanner the Python bean definition scanner
     * @param runtimeInspector the Python runtime inspector
     * @param vfsMetadataReader the VFS metadata reader
     * @param configuration panel configuration
     */
    public GraalPyControlPanel(PythonBeanScanner beanScanner,
                               PythonRuntimeInspector runtimeInspector,
                               GraalPyVfsMetadataReader vfsMetadataReader,
                               @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanScanner = beanScanner;
        this.runtimeInspector = runtimeInspector;
        this.vfsMetadataReader = vfsMetadataReader;
    }

    @Override
    public Body getBody() {
        return new Body(
            beanScanner.metadataAvailable(),
            beanScanner.scan(),
            runtimeInspector.contextPool(),
            runtimeInspector.contextConfiguration(),
            runtimeInspector.runtimeModules(),
            vfsMetadataReader.read()
        );
    }

    @Override
    public String getBadge() {
        return String.valueOf(beanScanner.count());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    /**
     * Detail body for the GraalPy panel.
     *
     * @param beanMetadataAvailable whether the Core Python bean annotations are resolvable
     * @param pythonBeans discovered Python-backed bean definitions
     * @param pool context pool snapshot and configuration
     * @param configuration GraalPy context configuration and customizers
     * @param modules optional Python runtime modules detected on the classpath
     * @param vfs packaged VFS metadata
     */
    @ReflectiveAccess
    public record Body(
        boolean beanMetadataAvailable,
        List<PythonBeanScanner.PythonBean> pythonBeans,
        PythonRuntimeInspector.ContextPool pool,
        PythonRuntimeInspector.ContextConfiguration configuration,
        PythonRuntimeInspector.RuntimeModules modules,
        GraalPyVfsMetadataReader.GraalPyVfsMetadata vfs) {

        /**
         * @return whether at least one Python-backed bean definition was detected
         */
        public boolean hasPythonBeans() {
            return !pythonBeans.isEmpty();
        }

        /**
         * @return number of detected Python-backed bean definitions
         */
        public int pythonBeanCount() {
            return pythonBeans.size();
        }
    }
}
