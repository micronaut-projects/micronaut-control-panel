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
package io.micronaut.controlpanel.panels.validation;

import io.micronaut.aop.Intercepted;
import io.micronaut.aop.Introduced;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.validation.validator.Validator;
import io.micronaut.validation.validator.ValidatorConfiguration;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects what Micronaut Validation validates in the application, from bean definitions and introspection metadata.
 *
 * <p>Nothing is validated and no value is read: only the names of the constraints are shown, never their attributes,
 * which can hold patterns or messages that are not meant to be shown. Classes in the {@code io.micronaut} packages are
 * left out.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = Validator.class)
public class ValidationInspector {

    static final int MAX_CLASSES = 200;

    private static final Logger LOG = LoggerFactory.getLogger(ValidationInspector.class);
    private static final String CONSTRAINT = "jakarta.validation.Constraint";
    private static final String VALID = "jakarta.validation.Valid";
    private static final String FRAMEWORK_PACKAGE = "io.micronaut.";
    private static final String LIST_SUFFIX = "$List";
    private static final Map<String, Boolean> CONSTRAINT_TYPES = new ConcurrentHashMap<>();

    private final BeanContext beanContext;
    private final Environment environment;

    /**
     * @param beanContext the bean context
     * @param environment the environment
     */
    public ValidationInspector(BeanContext beanContext, Environment environment) {
        this.beanContext = beanContext;
        this.environment = environment;
    }

    /**
     * @return what is validated
     */
    public ValidationInfo inspect() {
        List<ValidationInfo.ValidatedClass> classes = classes();
        return new ValidationInfo(
            environment.getProperty(ValidatorConfiguration.ENABLED, Boolean.class, true),
            validators(),
            beans(),
            classes.stream().limit(MAX_CLASSES).toList(),
            classes.size()
        );
    }

    private List<ValidationInfo.ConstraintValidator> validators() {
        List<ValidationInfo.ConstraintValidator> validators = new ArrayList<>();
        for (BeanDefinition<ConstraintValidator> definition : beanContext.getBeanDefinitions(ConstraintValidator.class)) {
            String type = definition.getBeanType().getName();
            if (type.startsWith(FRAMEWORK_PACKAGE)) {
                continue;
            }
            List<Argument<?>> arguments = definition.getTypeArguments(ConstraintValidator.class);
            String constraint = arguments.isEmpty() ? "unknown" : "@" + arguments.getFirst().getType().getSimpleName();
            validators.add(new ValidationInfo.ConstraintValidator(constraint, type));
        }
        validators.sort(Comparator.comparing(ValidationInfo.ConstraintValidator::constraint).thenComparing(ValidationInfo.ConstraintValidator::type));
        return validators;
    }

    private List<ValidationInfo.ValidatedBean> beans() {
        // A bean with validated methods has a generated subclass that intercepts them, so it is listed by the class it
        // intercepts, once
        Map<String, ValidationInfo.ValidatedBean> beans = new TreeMap<>();
        for (BeanDefinition<?> definition : beanContext.getAllBeanDefinitions()) {
            Class<?> type = beanClass(definition);
            if (type.getName().startsWith(FRAMEWORK_PACKAGE)) {
                continue;
            }
            try {
                List<ValidationInfo.Element> methods = new ArrayList<>();
                for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
                    ValidationInfo.Element element = method(method);
                    if (element != null) {
                        methods.add(element);
                    }
                }
                if (!methods.isEmpty()) {
                    beans.merge(type.getName(), new ValidationInfo.ValidatedBean(type.getName(), methods), (a, b) -> {
                        List<ValidationInfo.Element> merged = new ArrayList<>(a.methods());
                        b.methods().stream().filter(method -> !merged.contains(method)).forEach(merged::add);
                        return new ValidationInfo.ValidatedBean(a.type(), merged);
                    });
                }
            } catch (RuntimeException | LinkageError e) {
                LOG.debug("Unable to read the validated methods of {}", type.getName(), e);
            }
        }
        return beans.values().stream()
            .map(bean -> new ValidationInfo.ValidatedBean(bean.type(), bean.methods().stream().sorted(Comparator.comparing(ValidationInfo.Element::name)).toList()))
            .toList();
    }

    private static ValidationInfo.@Nullable Element method(ExecutableMethod<?, ?> method) {
        List<String> constraints = new ArrayList<>();
        List<String> parameters = new ArrayList<>();
        for (Argument<?> argument : method.getArguments()) {
            parameters.add(argument.getName());
            List<String> names = constraints(argument.getAnnotationMetadata());
            if (!names.isEmpty()) {
                constraints.add(argument.getName() + ": " + String.join(" ", names));
            }
        }
        List<String> returned = constraints(method.getAnnotationMetadata());
        if (!returned.isEmpty()) {
            constraints.add("returns: " + String.join(" ", returned));
        }
        if (constraints.isEmpty()) {
            return null;
        }
        return new ValidationInfo.Element(method.getMethodName() + "(" + String.join(", ", parameters) + ")", constraints);
    }

    private static List<ValidationInfo.ValidatedClass> classes() {
        List<ValidationInfo.ValidatedClass> classes = new ArrayList<>();
        try {
            for (BeanIntrospection<Object> introspection : BeanIntrospector.SHARED.findIntrospections(reference -> !reference.getName().startsWith(FRAMEWORK_PACKAGE))) {
                List<ValidationInfo.Element> properties = new ArrayList<>();
                for (BeanProperty<Object, Object> property : introspection.getBeanProperties()) {
                    List<String> names = constraints(property.getAnnotationMetadata());
                    if (!names.isEmpty()) {
                        properties.add(new ValidationInfo.Element(property.getName(), names));
                    }
                }
                if (!properties.isEmpty()) {
                    properties.sort(Comparator.comparing(ValidationInfo.Element::name));
                    classes.add(new ValidationInfo.ValidatedClass(
                        introspection.getBeanType().getName(),
                        introspection.hasStereotype(ConfigurationReader.class),
                        properties
                    ));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("Unable to read the introspected classes", e);
        }
        classes.sort(Comparator.comparing(ValidationInfo.ValidatedClass::type));
        return classes;
    }

    static Class<?> beanClass(BeanDefinition<?> definition) {
        return beanClass(definition.getBeanType());
    }

    static Class<?> beanClass(Class<?> type) {
        if (!Intercepted.class.isAssignableFrom(type)) {
            return type;
        }
        if (Introduced.class.isAssignableFrom(type)) {
            // Introduction advice, such as Micronaut Data repositories and declarative clients: the proxy extends
            // Object and implements the advised interface
            for (Class<?> implemented : type.getInterfaces()) {
                if (!Intercepted.class.isAssignableFrom(implemented)) {
                    return implemented;
                }
            }
        }
        return type.getSuperclass() != null ? type.getSuperclass() : type;
    }

    private static List<String> constraints(AnnotationMetadata metadata) {
        if (!metadata.hasStereotype(CONSTRAINT) && !metadata.hasAnnotation(VALID)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String name : metadata.getAnnotationNames()) {
            // Constraints are stored in their repeatable container, such as NotBlank$List
            String annotation = name.endsWith(LIST_SUFFIX) ? name.substring(0, name.length() - LIST_SUFFIX.length()) : name;
            if (isConstraint(annotation)) {
                names.add("@" + simpleName(annotation));
            }
        }
        // The annotation metadata does not keep the declaration order, so constraints are sorted, with @Valid last
        List<String> sorted = new ArrayList<>(names.stream().distinct().sorted().toList());
        if (metadata.hasAnnotation(VALID)) {
            sorted.add("@Valid");
        }
        return sorted;
    }

    private static boolean isConstraint(String annotation) {
        return CONSTRAINT_TYPES.computeIfAbsent(annotation, name -> {
            Optional<Class<?>> type = ClassUtils.forName(name, ValidationInspector.class.getClassLoader());
            return type.filter(Class::isAnnotation)
                .map(t -> t.isAnnotationPresent(jakarta.validation.Constraint.class))
                .orElse(false);
        });
    }

    private static String simpleName(String name) {
        int dollar = name.lastIndexOf('$');
        int dot = name.lastIndexOf('.');
        return name.substring(Math.max(dollar, dot) + 1);
    }
}
