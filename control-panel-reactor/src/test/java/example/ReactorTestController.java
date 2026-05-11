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
package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Controller("/reactor")
@Requires(property = "spec.name", value = "ReactorControlPanelTest")
class ReactorTestController {

    @Get("/plain")
    String plain() {
        return "plain";
    }

    @Get("/completion-stage")
    CompletionStage<String> completionStage() {
        return CompletableFuture.completedFuture("completion-stage");
    }

    @Get("/mono")
    Mono<String> mono() {
        return Mono.just("mono");
    }

    @Get("/flux")
    Flux<String> flux() {
        return Flux.just("one", "two");
    }

    @Get("/sse")
    @Produces(MediaType.TEXT_EVENT_STREAM)
    Flux<String> sse() {
        return Flux.just("one", "two");
    }

    @Get("/stream")
    @Produces(MediaType.APPLICATION_JSON_STREAM)
    Flux<String> stream() {
        return Flux.just("one", "two");
    }
}
