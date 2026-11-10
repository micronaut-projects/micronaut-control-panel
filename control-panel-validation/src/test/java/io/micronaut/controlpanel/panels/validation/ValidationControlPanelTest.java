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

import com.example.orders.OrderFixtures;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationControlPanelTest {

    @Test
    void listsValidatedMethodsClassesAndValidators() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", OrderFixtures.SPEC, "orders.prefix", "ORD"))) {
            ValidationControlPanel panel = context.getBean(ValidationControlPanel.class);
            assertEquals("Validation", panel.getTitle());

            ValidationInfo body = panel.getBody();
            assertTrue(body.enabled());

            ValidationInfo.ValidatedBean service = body.beans().stream()
                .filter(bean -> bean.type().equals(OrderFixtures.OrderService.class.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(body.beans().toString()));
            ValidationInfo.Element place = service.methods().getFirst();
            assertEquals("place(order, quantity)", place.name());
            assertEquals(List.of("order: @NotNull @Valid", "quantity: @Min", "returns: @NotBlank"), place.constraints());

            // Introduction advice is reported under the advised interface, not under java.lang.Object
            ValidationInfo.ValidatedBean repository = body.beans().stream()
                .filter(bean -> bean.type().equals(OrderFixtures.OrderRepository.class.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(body.beans().toString()));
            assertEquals("findBySku(sku)", repository.methods().getFirst().name());
            assertTrue(body.beans().stream().noneMatch(bean -> bean.type().equals(Object.class.getName())), body.beans().toString());

            ValidationInfo.ValidatedClass order = body.classes().stream()
                .filter(c -> c.type().equals(OrderFixtures.Order.class.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(body.classes().toString()));
            assertFalse(order.configuration());
            assertEquals(List.of("@NotBlank", "@Sku"), order.properties().getFirst().constraints());

            ValidationInfo.ValidatedClass configuration = body.classes().stream()
                .filter(c -> c.type().equals(OrderFixtures.OrdersConfiguration.class.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(body.classes().toString()));
            assertTrue(configuration.configuration());
            assertFalse(body.toString().contains("[A-Z]{3}"), "constraint attributes are not shown");

            assertEquals(List.of(new ValidationInfo.ConstraintValidator("@Sku", OrderFixtures.SkuValidator.class.getName())), body.validators());
            assertTrue(body.classes().stream().noneMatch(c -> c.type().startsWith("io.micronaut.")));

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(ValidationControlPanel.NAME).isPresent());
        }
    }

    @Test
    void reportsDisabledValidation() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.validator.enabled", false))) {
            assertFalse(context.getBean(ValidationControlPanel.class).getBody().enabled());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(ValidationControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(ValidationControlPanel.class));
        }
    }
}
