package io.micronaut.controlpanel.test.thymleaf;

import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = Environment.DEVELOPMENT)
class ControlPlanWorksWithThymeleafTest {

    @Test
    void controlPlanWorksWithThymeleaf(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpRequest<?> request = HttpRequest.GET("/control-panel");
        HttpResponse<String> response = client.exchange(request, String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        String body = response.body();
        assertNotNull(body);
        assertTrue(body.contains("Micronaut Control Panel"));
    }
}
