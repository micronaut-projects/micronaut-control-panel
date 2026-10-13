package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "demo")
@Controller("/demo")
public class DemoController {

    @Operation(summary = "test1")
    @Get("/test1")
    public String test1() {
        return "test1";
    }

    @Operation(summary = "test2")
    @Get("/test2")
    public String test2() {
        return "test2";
    }

    @Operation(summary = "mono")
    @Get("/reactor/mono")
    public Mono<String> mono() {
        return Mono.just("mono");
    }

    @Operation(summary = "sse")
    @Get("/reactor/sse")
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> sse() {
        return Flux.just("one", "two");
    }
}
