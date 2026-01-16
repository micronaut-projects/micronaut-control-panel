package example;
 
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
 
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
}