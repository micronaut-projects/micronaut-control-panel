package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/demo")
@Tag(name = "Demo", description = "Demo API endpoints")
public class DemoController {
    
    @Get("/test1")
    @Operation(summary = "Test endpoint 1", description = "Returns a simple test message")
    public String test1() {
        return "test1";
    }
    
    @Get("/test2")
    @Operation(summary = "Test endpoint 2", description = "Returns another test message")
    public String test2() {
        return "test2";
    }
}