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
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;

/**
 * REST controller for explicitly enabled Pulsar consumer actions.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Controller(ControlPanelSecurityPaths.PULSAR)
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
@Requires(classes = PulsarConsumerRegistry.class)
public final class PulsarControlPanelController {

    private static final Argument<PulsarConsumerRegistry> CONSUMER_REGISTRY = Argument.of(PulsarConsumerRegistry.class);

    private final BeanLocator beanLocator;
    private final PulsarControlPanelConfiguration configuration;

    PulsarControlPanelController(BeanLocator beanLocator, PulsarControlPanelConfiguration configuration) {
        this.beanLocator = beanLocator;
        this.configuration = configuration;
    }

    /**
     * Pauses a registered Pulsar consumer when consumer actions are enabled.
     *
     * @param request pause request
     * @return action response
     */
    @Post(value = "/consumers/pause", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<ActionResponse> pause(@Body ConsumerActionRequest request) {
        return consumerAction(request, Action.PAUSE);
    }

    /**
     * Resumes a registered Pulsar consumer when consumer actions are enabled.
     *
     * @param request resume request
     * @return action response
     */
    @Post(value = "/consumers/resume", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<ActionResponse> resume(@Body ConsumerActionRequest request) {
        return consumerAction(request, Action.RESUME);
    }

    private HttpResponse<ActionResponse> consumerAction(ConsumerActionRequest request, Action action) {
        if (!configuration.isAllowConsumerActions()) {
            return HttpResponse.<ActionResponse>status(HttpStatus.FORBIDDEN)
                .body(new ActionResponse(false, "Pulsar consumer actions are disabled"));
        }
        if (request == null || request.id() == null || request.id().isBlank()) {
            return HttpResponse.badRequest(new ActionResponse(false, "Consumer id must not be blank"));
        }
        var registry = beanLocator.findBean(CONSUMER_REGISTRY);
        if (registry.isEmpty() || !registry.get().consumerExists(request.id())) {
            return HttpResponse.notFound(new ActionResponse(false, "Pulsar consumer was not found"));
        }
        if (action == Action.PAUSE) {
            registry.get().pause(request.id());
        } else {
            registry.get().resume(request.id());
        }
        return HttpResponse.ok(new ActionResponse(true, "Pulsar consumer " + action.label + "d"));
    }

    private enum Action {
        PAUSE("pause"),
        RESUME("resume");

        private final String label;

        Action(String label) {
            this.label = label;
        }
    }

    /**
     * Consumer action request.
     *
     * @param id registry consumer id
     */
    @Serdeable
    @Introspected
    public record ConsumerActionRequest(String id) {
    }

    /**
     * Consumer action response.
     *
     * @param success whether the action succeeded
     * @param message human-readable message
     */
    @Serdeable
    @Introspected
    public record ActionResponse(boolean success, String message) {
    }
}
