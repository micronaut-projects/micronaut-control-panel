package example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ControlPanelRenderingTest {

    private static final boolean NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

    /** One rendered {@link StackTraceElement#toString()}, for example {@code java.base@25/java.lang.Thread.run(Thread.java:1474)}. */
    private static final Pattern STACK_FRAME = Pattern.compile("^[\\w.$@/-]+\\([^)]*\\)$", Pattern.MULTILINE);

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
            // The disclosure must contain the mapped stack frames. A nested {{#each}} without its own block
            // parameter resolves {{this}} to the enclosing ThreadRow, which renders the record's toString()
            // once per frame instead of the frames themselves.
            assertFalse(body.contains("ThreadRow["), "the stack trace must not render the ThreadRow record");
            assertTrue(STACK_FRAME.matcher(stackTraceOf(body)).find(),
                "the stack trace must render real frames");
        }
    }

    /**
     * @param body the rendered thread dump page
     * @return the text of the first rendered stack trace disclosure
     */
    private static String stackTraceOf(String body) {
        String open = "<pre class=\"cp-thread-stack\"><code class=\"nohighlight\">";
        int start = body.indexOf(open);
        assertTrue(start >= 0, "expected a rendered stack trace");
        return body.substring(start + open.length(), body.indexOf("</code>", start));
    }

    @Test
    void rendersThreadDumpDashboardCard(@Client("/") HttpClient client) {
        var body = client.toBlocking().retrieve(HttpRequest.GET("/control-panel"));

        // This application keeps the default ThreadInfoMapper, so the card carries the cheap summary. An application
        // that replaces the mapper gets a count-free card instead; see ThreadDumpControlPanelTest.
        assertTrue(body.contains("threads in the current JVM snapshot."));
    }
}
