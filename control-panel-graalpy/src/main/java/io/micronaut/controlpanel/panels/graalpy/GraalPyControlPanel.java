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

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.type.Argument;
import io.micronaut.graal.graalpy.GraalPyContextBuilderFactory;
import io.micronaut.graal.graalpy.annotations.GraalPyModule;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only diagnostics for Micronaut GraalPy module definitions.
 */
@Singleton
@Requires(classes = GraalPyModule.class)
public class GraalPyControlPanel extends AbstractControlPanel<GraalPyControlPanel.Body> {

    /**
     * Panel name.
     */
    public static final String NAME = "graalpy";
    /**
     * Property that enables or disables the GraalPy panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    /**
     * GraalPy category used by the sidebar.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "GraalPy", "fa-brands fa-python");

    private static final String GRAALPY_CONTEXT_CLASS_NAME = "io.micronaut.graal.graalpy.GraalPyContext";

    private final BeanContext beanContext;
    private final GraalPyVfsMetadataReader vfsMetadataReader;

    /**
     * Constructor.
     *
     * @param beanContext the bean context to inspect
     * @param vfsMetadataReader the VFS metadata reader
     * @param configuration panel configuration
     */
    public GraalPyControlPanel(BeanContext beanContext,
                               GraalPyVfsMetadataReader vfsMetadataReader,
                               @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanContext = beanContext;
        this.vfsMetadataReader = vfsMetadataReader;
    }

    @Override
    public Body getBody() {
        List<ModuleInterface> modules = discoverModules();
        RuntimeMetadata runtime = discoverRuntimeMetadata();
        GraalPyVfsMetadataReader.GraalPyVfsMetadata vfs = vfsMetadataReader.read();
        return new Body(modules, runtime, vfs);
    }

    @Override
    public String getBadge() {
        return String.valueOf(countModules());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    private List<ModuleInterface> discoverModules() {
        return beanContext.getAllBeanDefinitions()
            .stream()
            .filter(GraalPyControlPanel::isGraalPyModule)
            .map(this::moduleInterface)
            .sorted(Comparator.comparing(ModuleInterface::javaType).thenComparing(ModuleInterface::beanName))
            .toList();
    }

    private long countModules() {
        return beanContext.getAllBeanDefinitions()
            .stream()
            .filter(GraalPyControlPanel::isGraalPyModule)
            .count();
    }

    private static boolean isGraalPyModule(BeanDefinition<?> definition) {
        return definition.hasStereotype(GraalPyModule.class) || definition.hasDeclaredStereotype(GraalPyModule.class);
    }

    private ModuleInterface moduleInterface(BeanDefinition<?> definition) {
        String moduleName = definition.stringValue(GraalPyModule.class).orElse("");
        List<MethodSignature> methods = methodSignatures(definition);
        return new ModuleInterface(
            definition.getName(),
            definition.getBeanType().getName(),
            moduleName,
            definition.getScopeName().orElse("Unknown"),
            qualifierName(definition),
            "Unknown",
            methods
        );
    }

    private static String qualifierName(BeanDefinition<?> definition) {
        Qualifier<?> qualifier = definition.getDeclaredQualifier();
        return qualifier == null ? "Default" : qualifier.toString();
    }

    private static List<MethodSignature> methodSignatures(BeanDefinition<?> definition) {
        Collection<ExecutableMethod<?, ?>> executableMethods = new ArrayList<>(definition.getExecutableMethods());
        if (!executableMethods.isEmpty()) {
            return executableMethods.stream()
                .filter(GraalPyControlPanel::isUserMethod)
                .map(GraalPyControlPanel::methodSignature)
                .sorted(Comparator.comparing(MethodSignature::name).thenComparing(MethodSignature::signature))
                .toList();
        }
        return Arrays.stream(definition.getBeanType().getMethods())
            .filter(GraalPyControlPanel::isUserMethod)
            .map(GraalPyControlPanel::methodSignature)
            .sorted(Comparator.comparing(MethodSignature::name).thenComparing(MethodSignature::signature))
            .toList();
    }

    private static boolean isUserMethod(ExecutableMethod<?, ?> method) {
        Method targetMethod = method.getTargetMethod();
        return isUserMethod(targetMethod);
    }

    private static boolean isUserMethod(Method method) {
        return method.getDeclaringClass() != Object.class
            && !method.isSynthetic()
            && !method.isBridge()
            && !Modifier.isStatic(method.getModifiers());
    }

    private static MethodSignature methodSignature(ExecutableMethod<?, ?> method) {
        String name = method.getMethodName();
        String returnType = displayType(method.getReturnType().asArgument());
        String parameters = Arrays.stream(method.getArguments())
            .map(GraalPyControlPanel::displayParameter)
            .collect(Collectors.joining(", "));
        return new MethodSignature(name, returnType, parameters, returnType + " " + name + "(" + parameters + ")");
    }

    private static MethodSignature methodSignature(Method method) {
        String name = method.getName();
        String returnType = displayType(method.getGenericReturnType());
        String parameters = Arrays.stream(method.getGenericParameterTypes())
            .map(GraalPyControlPanel::displayType)
            .collect(Collectors.joining(", "));
        return new MethodSignature(name, returnType, parameters, returnType + " " + name + "(" + parameters + ")");
    }

    private static String displayParameter(Argument<?> argument) {
        return displayType(argument) + " " + argument.getName();
    }

    private static String displayType(Argument<?> argument) {
        return argument.getTypeString(false);
    }

    private static String displayType(Type type) {
        return type.getTypeName()
            .replace("java.lang.", "");
    }

    private RuntimeMetadata discoverRuntimeMetadata() {
        List<String> factoryCandidates = beanContext.getBeanDefinitions(GraalPyContextBuilderFactory.class)
            .stream()
            .map(definition -> definition.getBeanType().getName())
            .sorted()
            .toList();
        String factoryStatus = factoryStatus(factoryCandidates);
        String activeFactoryClass = factoryCandidates.size() == 1 ? factoryCandidates.get(0) : "";
        boolean sharedContextPresent = beanContext.getAllBeanDefinitions()
            .stream()
            .map(BeanDefinition::getBeanType)
            .map(Class::getName)
            .anyMatch(GRAALPY_CONTEXT_CLASS_NAME::equals);
        return new RuntimeMetadata(factoryStatus, activeFactoryClass, factoryCandidates, sharedContextPresent);
    }

    private static String factoryStatus(List<String> factoryCandidates) {
        if (factoryCandidates.isEmpty()) {
            return "Unavailable";
        }
        if (factoryCandidates.size() == 1) {
            return "Available";
        }
        return "Ambiguous";
    }

    /**
     * Detail body for the GraalPy panel.
     *
     * @param modules discovered module interfaces
     * @param runtime runtime metadata
     * @param vfs VFS metadata
     */
    @ReflectiveAccess
    public record Body(
        List<ModuleInterface> modules,
        RuntimeMetadata runtime,
        GraalPyVfsMetadataReader.GraalPyVfsMetadata vfs) {

        /**
         * @return whether at least one GraalPy module interface was detected
         */
        public boolean hasModules() {
            return !modules.isEmpty();
        }

        /**
         * @return number of detected GraalPy module interfaces
         */
        public int moduleCount() {
            return modules.size();
        }
    }

    /**
     * Description of a bean definition annotated with {@link GraalPyModule}.
     *
     * @param beanName bean name
     * @param javaType Java interface type
     * @param pythonModule Python module name
     * @param scope scope metadata
     * @param qualifier qualifier metadata
     * @param instantiated safe instantiation state
     * @param methods Java method signatures
     */
    @ReflectiveAccess
    public record ModuleInterface(
        String beanName,
        String javaType,
        String pythonModule,
        String scope,
        String qualifier,
        String instantiated,
        List<MethodSignature> methods) {

        /**
         * @return whether method signatures are available
         */
        public boolean hasMethods() {
            return !methods.isEmpty();
        }

        /**
         * @return method signature count
         */
        public int methodCount() {
            return methods.size();
        }
    }

    /**
     * Java method signature expected to map to a Python function.
     *
     * @param name method name
     * @param returnType return type
     * @param parameters parameter list
     * @param signature display signature
     */
    @ReflectiveAccess
    public record MethodSignature(String name, String returnType, String parameters, String signature) {
    }

    /**
     * Runtime metadata that can be discovered without creating GraalPy contexts.
     *
     * @param factoryStatus factory candidate state
     * @param activeFactoryClass single factory class when unambiguous
     * @param factoryCandidates factory candidate classes
     * @param sharedContextPresent whether the shared context bean definition is present
     */
    @ReflectiveAccess
    public record RuntimeMetadata(
        String factoryStatus,
        String activeFactoryClass,
        List<String> factoryCandidates,
        boolean sharedContextPresent) {

        /**
         * @return whether a single factory candidate was found
         */
        public boolean hasActiveFactory() {
            return !activeFactoryClass.isBlank();
        }

        /**
         * @return whether any factory candidates were found
         */
        public boolean hasFactoryCandidates() {
            return !factoryCandidates.isEmpty();
        }
    }
}
