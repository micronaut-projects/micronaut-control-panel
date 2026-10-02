package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Controller("/validation-demo")
class ValidationDemoController {

    @Post("/orders/{tenant}")
    ValidationResponse create(@TenantCode String tenant, @Body @Valid ValidationRequest request) {
        return new ValidationResponse(tenant, request.email());
    }

    @Introspected
    @Serdeable
    record ValidationRequest(
        @NotBlank
        @Size(max = 80)
        String name,

        @Email
        String email,

        @Valid
        Address address
    ) {
    }

    @Introspected
    @Serdeable
    record Address(
        @NotBlank
        String city
    ) {
    }

    @Serdeable
    record ValidationResponse(String tenant, String email) {
    }
}
