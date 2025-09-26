package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/demo")
public class DemoController {
    
    @Get("/test1")
    public String test1() {
        return "test1";
    }
    
    @Get("/test2")
    public String test2() {
        return "test2";
    }
}