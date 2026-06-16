package example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ControlPanelRenderingTest {

    @Test
    void rendersObjectStorageCategory(@Client("/") HttpClient client) {
        var body = client.toBlocking().retrieve(HttpRequest.GET("/control-panel/categories/object-storage"));

        assertTrue(body.contains("my-local"));
        assertTrue(body.contains("files stored."));
    }

    @Test
    void rendersThreadDumpPanel(@Client("/") HttpClient client) {
        var body = client.toBlocking().retrieve(HttpRequest.GET("/control-panel/threaddump"));

        assertTrue(body.contains("Thread Dump"));
        assertTrue(body.contains("Refresh dump"));
        assertTrue(body.contains("Search threads, states, locks, or stack frames")
            || body.contains("Thread dump data is not available."));
    }
}
