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

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * What Micronaut Validation validates in the application. Internal to the Validation panel views.
 *
 * @param enabled whether validation is enabled ({@code micronaut.validator.enabled})
 * @param validators the custom constraint validators, sorted by constraint
 * @param beans the beans with validated methods, sorted by type
 * @param classes the classes with constrained properties, sorted by type, capped at
 * {@value ValidationInspector#MAX_CLASSES}
 * @param totalClasses the number of classes with constrained properties before the cap
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record ValidationInfo(
    boolean enabled,
    List<ConstraintValidator> validators,
    List<ValidatedBean> beans,
    List<ValidatedClass> classes,
    int totalClasses
) {

    /**
     * @return whether the class list was capped
     */
    public boolean truncated() {
        return totalClasses > classes.size();
    }

    /**
     * A constraint validator bean.
     *
     * @param constraint the constraint annotation it validates
     * @param type the validator class
     */
    @ReflectiveAccess
    public record ConstraintValidator(String constraint, String type) {
    }

    /**
     * A bean whose method arguments or return values are validated.
     *
     * @param type the bean class
     * @param methods the validated methods, sorted by name
     */
    @ReflectiveAccess
    public record ValidatedBean(String type, List<Element> methods) {
    }

    /**
     * A class whose properties are validated, such as a request body or a {@code @ConfigurationProperties} bean.
     *
     * @param type the class
     * @param configuration whether it is a {@code @ConfigurationProperties} class, validated when the bean is created
     * @param properties the constrained properties, sorted by name
     */
    @ReflectiveAccess
    public record ValidatedClass(String type, boolean configuration, List<Element> properties) {
    }

    /**
     * A validated method or property. Only the constraint names are shown, never their attributes or any value.
     *
     * @param name the method signature or property name
     * @param constraints the constraints, such as {@code @NotBlank}, and {@code @Valid} for cascaded validation
     */
    @ReflectiveAccess
    public record Element(String name, List<String> constraints) {

        /**
         * @return the constraints, separated by spaces
         */
        public String constraintList() {
            return String.join(" ", constraints);
        }
    }
}
