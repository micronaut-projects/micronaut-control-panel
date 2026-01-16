package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Demo", description = "Demo operations")
@Controller("/demo")
public class DemoController {
    
    @Operation(summary = "Test endpoint 1", description = "Returns test1 string")
    @ApiResponse(responseCode = "200", description = "Successful operation")
    @Get("/test1")
    public String test1() {
        return "test1";
    }
    
    @Operation(summary = "Test endpoint 2", description = "Returns test2 string")
    @ApiResponse(responseCode = "200", description = "Successful operation")
    @Get("/test2")
    public String test2() {
        return "test2";
    }
}