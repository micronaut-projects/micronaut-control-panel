package example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/demo")
@Tag(name = "demo")
public class DemoController {

    @Get("/test1")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "First demo endpoint", description = "Returns a plain text demo response.")
    public String test1() {
        return "test1";
    }

    @Get("/test2")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Second demo endpoint", description = "Returns a plain text demo response.")
    public String test2() {
        return "test2";
    }
}
