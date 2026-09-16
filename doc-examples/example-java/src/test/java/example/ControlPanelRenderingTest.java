package example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ControlPanelRenderingTest {

    private static final boolean NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

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
        if (NATIVE_IMAGE) {
            // In a native image ThreadMXBean.dumpAllThreads() always returns an empty array, so the panel renders its
            // documented empty state. See the "GraalVM Native Image Limitation" section of the management guide.
            assertTrue(body.contains("Thread dump data is not available."));
        } else {
            assertTrue(body.contains("Search threads, states, locks, or stack frames"));
            assertTrue(body.contains("Show stack trace"));
        }
    }
}
