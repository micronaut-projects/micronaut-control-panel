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
package com.example.orders;

import io.micronaut.aop.Introduction;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;
import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Application classes for the Validation panel tests, outside the {@code io.micronaut} packages the panel leaves out.
 */
public final class OrderFixtures {

    public static final String SPEC = "ValidationControlPanelTest";

    private OrderFixtures() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface Sku {
        String message() default "invalid SKU";
    }

    @Introspected
    public record Order(@NotBlank @Sku String sku) {
    }

    @Requires(property = "spec.name", value = OrderFixtures.SPEC)
    @Singleton
    public static class SkuValidator implements ConstraintValidator<Sku, String> {
        @Override
        public boolean isValid(String value, AnnotationValue<Sku> annotationMetadata, ConstraintValidatorContext context) {
            return value == null || value.length() == 8;
        }
    }

    @Requires(property = "spec.name", value = OrderFixtures.SPEC)
    @Singleton
    public static class OrderService {
        @NotBlank
        public String place(@NotNull @Valid Order order, @Min(1) int quantity) {
            return order.sku();
        }
    }

    @Requires(property = "spec.name", value = OrderFixtures.SPEC)
    @ConfigurationProperties("orders")
    public static class OrdersConfiguration {
        @Pattern(regexp = "[A-Z]{3}")
        private String prefix;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    /**
     * Introduction advice, as used by Micronaut Data repositories and declarative clients.
     */
    @Introduction
    @Type(StubIntroducer.class)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Stub {
    }

    @Requires(property = "spec.name", value = OrderFixtures.SPEC)
    @Singleton
    public static class StubIntroducer implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return null;
        }
    }

    @Requires(property = "spec.name", value = OrderFixtures.SPEC)
    @Stub
    public interface OrderRepository {
        Order findBySku(@NotBlank String sku);
    }
}
