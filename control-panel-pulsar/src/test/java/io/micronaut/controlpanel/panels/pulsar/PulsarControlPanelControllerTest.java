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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PulsarControlPanelControllerTest {

    @Mock BeanLocator beanLocator;

    @Test
    void deniesPauseWhenActionsAreDisabled() {
        var registry = mock(PulsarConsumerRegistry.class);
        var controller = new PulsarControlPanelController(beanLocator, configuration(false));

        HttpResponse<PulsarControlPanelController.ActionResponse> response = controller.pause(new PulsarControlPanelController.ConsumerActionRequest("orders"));

        assertEquals(403, response.getStatus().getCode());
        verify(registry, never()).pause("orders");
    }

    @Test
    void pausesConsumerWhenActionsAreEnabled() {
        var registry = mock(PulsarConsumerRegistry.class);
        when(registry.consumerExists("orders")).thenReturn(true);
        doReturn(Optional.of(registry)).when(beanLocator).findBean(Argument.of(PulsarConsumerRegistry.class));
        var controller = new PulsarControlPanelController(beanLocator, configuration(true));

        HttpResponse<PulsarControlPanelController.ActionResponse> response = controller.pause(new PulsarControlPanelController.ConsumerActionRequest("orders"));

        assertEquals(200, response.getStatus().getCode());
        verify(registry).pause("orders");
    }

    @Test
    void resumesConsumerWhenActionsAreEnabled() {
        var registry = mock(PulsarConsumerRegistry.class);
        when(registry.consumerExists("orders")).thenReturn(true);
        doReturn(Optional.of(registry)).when(beanLocator).findBean(Argument.of(PulsarConsumerRegistry.class));
        var controller = new PulsarControlPanelController(beanLocator, configuration(true));

        HttpResponse<PulsarControlPanelController.ActionResponse> response = controller.resume(new PulsarControlPanelController.ConsumerActionRequest("orders"));

        assertEquals(200, response.getStatus().getCode());
        verify(registry).resume("orders");
    }

    @Test
    void returnsNotFoundForMissingConsumerRegistry() {
        doReturn(Optional.empty()).when(beanLocator).findBean(Argument.of(PulsarConsumerRegistry.class));
        var controller = new PulsarControlPanelController(beanLocator, configuration(true));

        HttpResponse<PulsarControlPanelController.ActionResponse> response = controller.resume(new PulsarControlPanelController.ConsumerActionRequest("orders"));

        assertEquals(404, response.getStatus().getCode());
    }

    private static PulsarControlPanelConfiguration configuration(boolean allowConsumerActions) {
        return new PulsarControlPanelConfiguration() {
            @Override
            public boolean isAllowConsumerActions() {
                return allowConsumerActions;
            }

            @Override
            public boolean isIncludeFailureEvents() {
                return true;
            }

            @Override
            public int getMaxFailureEvents() {
                return 25;
            }
        };
    }
}
