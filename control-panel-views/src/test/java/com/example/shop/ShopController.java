package com.example.shop;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;

import java.util.Map;

@Requires(property = "spec.name", value = ShopController.SPEC)
@Controller("/shop")
public class ShopController {

    public static final String SPEC = "ViewsControlPanelTest";

    @View("home")
    @Get
    public Map<String, Object> home() {
        return Map.of("title", "Shop");
    }

    @View("checkout")
    @Get("/checkout")
    public Map<String, Object> checkout() {
        return Map.of();
    }

    @Get("/product/{id}")
    public HttpResponse<ModelAndView<Map<String, Object>>> product(String id) {
        return HttpResponse.ok(new ModelAndView<>("home", Map.of("id", id)));
    }

    @Get("/api/products")
    public Map<String, Object> products() {
        return Map.of();
    }
}
