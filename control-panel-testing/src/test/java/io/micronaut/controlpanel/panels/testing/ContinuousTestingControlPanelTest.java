package io.micronaut.controlpanel.panels.testing;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = "dev")
@Property(name = "micronaut.dev.enabled", value = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContinuousTestingControlPanelTest implements TestPropertyProvider {

    private final Path project = createProject();

    @Inject
    ContinuousTestingControlPanel controlPanel;

    @Inject
    ControlPanelRepository repository;

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
            ContinuousTestingConfiguration.PREFIX + ".project-directory", project.toString(),
            ContinuousTestingConfiguration.PREFIX + ".live-reload-port", String.valueOf(freePort())
        );
    }

    @AfterAll
    void deleteProject() throws IOException {
        try (Stream<Path> files = Files.walk(project)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
        }
    }

    @Test
    void theTestingCategoryHoldsThePanel() {
        assertEquals("testing", controlPanel.getCategory().id());
        assertEquals("Testing", controlPanel.getCategory().name());
        assertEquals("Continuous Testing", controlPanel.getTitle());
        assertTrue(repository.findAllByCategory("testing").contains(controlPanel));
    }

    @Test
    void theCategoryPageTellsHowToStartContinuousTesting() {
        String html = client.toBlocking().retrieve("/control-panel/categories/testing");
        assertTrue(html.contains("Continuous testing is not running"), html);
        assertTrue(html.contains("./gradlew mnTest"), html);
        assertTrue(html.contains("mvn mn:test"), html);
        assertTrue(html.contains("pyronaut test -t"), html);
        assertTrue(html.contains("Not running"), html);
    }

    @Test
    void theDetailPageShowsTheLastReport() throws IOException {
        Path report = project.resolve("build/reports/tests/mnTest");
        Files.createDirectories(report);
        Files.writeString(report.resolve("index.html"), "<html></html>");
        Files.writeString(report.resolve("state.json"), """
            {"version":1,"project":"%s","generatedAt":"2026-10-11T10:00:05Z","live":false,"running":null,"finished":0,
             "current":[],
             "last":{"runId":1,"passed":4,"failed":1,"errored":0,"skipped":0,"durationMillis":321,"cancelled":false,"complete":true,"selection":"all","startedAt":"2026-10-11T10:00:00Z"},
             "history":[{"runId":1,"passed":4,"failed":1,"errored":0,"skipped":0,"durationMillis":321,"cancelled":false,"complete":true,"selection":"all","startedAt":"2026-10-11T10:00:00Z"}],
             "compileFailure":null,
             "classes":[{"name":"example.GreetingTest","tests":[
               {"id":"[a]","name":"greets","displayName":"greets <b>you</b>","status":"failed","durationMillis":3,"message":"expected Hello"}]}],
             "sources":{}}
            """.formatted(project.getFileName()));
        try {
            String html = client.toBlocking().retrieve("/control-panel/continuous-testing");
            assertTrue(html.contains("Continuous testing may be running"), "the report was written without a live server: " + html);
            assertTrue(html.contains("./gradlew mnTest"), html);
            assertTrue(html.contains("example.GreetingTest"), html);
            assertTrue(html.contains("greets &lt;b&gt;you&lt;/b&gt;"), "the names of tests are escaped: " + html);
            assertTrue(html.contains("expected Hello"), html);
            assertTrue(html.contains("Recent runs"), html);
            assertTrue(html.contains(report.resolve("index.html").toString()), html);
            assertFalse(html.contains("Open the live test report"), html);
        } finally {
            Files.delete(report.resolve("state.json"));
            Files.delete(report.resolve("index.html"));
        }
    }

    @Test
    void thePanelIsOnlyThereInDevelopmentMode() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(), "dev")) {
            assertFalse(context.containsBean(ContinuousTestingControlPanel.class));
            assertFalse(context.getBean(ControlPanelRepository.class).findAll().stream()
                .anyMatch(panel -> panel.getName().equals(ContinuousTestingControlPanel.NAME)));
        }
    }

    @Test
    void thePanelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.dev.enabled", true,
            ContinuousTestingControlPanel.ENABLED_PROPERTY, false
        ), "dev")) {
            assertFalse(context.containsBean(ContinuousTestingControlPanel.class));
        }
    }

    private static Path createProject() {
        try {
            Path project = Files.createTempDirectory("continuous-testing");
            Files.writeString(project.resolve("build.gradle.kts"), "");
            Files.writeString(project.resolve("gradlew"), "");
            return project;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
