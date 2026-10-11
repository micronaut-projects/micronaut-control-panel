package io.micronaut.controlpanel.panels.testing;

import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousTestingStatusResolverTest {

    @TempDir
    Path project;

    private ApplicationContext context;
    private HttpServer server;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void notRunningWithoutAReportListsTheCommandsOfTheProjectFirst() throws IOException {
        Files.writeString(project.resolve("pom.xml"), "<project/>");

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertFalse(status.running());
        assertFalse(status.reportFound());
        assertNull(status.lastRun());
        assertEquals("Not running", status.badge());
        assertEquals(List.of("Maven", "Gradle", "Pyronaut"), status.commands().stream().map(ContinuousTestingStatus.Command::tool).toList());
        assertEquals("mvn mn:test", status.commands().get(0).command());
        assertTrue(status.commands().get(0).detected());
        assertEquals("./gradlew mnTest", status.commands().get(1).command());
        assertEquals("pyronaut test -t", status.commands().get(2).command());
    }

    @Test
    void commandsUseTheWrappersOfTheProject() throws IOException {
        Files.writeString(project.resolve("build.gradle.kts"), "");
        Files.writeString(project.resolve("gradlew"), "");

        List<ContinuousTestingStatus.Command> commands = ContinuousTestingStatusResolver.commands(project);

        assertEquals("Gradle", commands.get(0).tool());
        assertEquals("./gradlew mnTest", commands.get(0).command());
        assertTrue(commands.get(0).detected());

        Files.delete(project.resolve("gradlew"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("mvnw"), "");
        commands = ContinuousTestingStatusResolver.commands(project);
        assertEquals("gradle mnTest", commands.get(0).command());
        assertEquals("./mvnw mn:test", commands.get(1).command());
        assertTrue(commands.get(1).detected());
    }

    @Test
    void readsTheLastReportOfAStoppedProcess() throws IOException {
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState(""));

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertFalse(status.running(), "nothing serves the report");
        assertFalse(status.mayBeRunning(), "the process that served it stopped");
        assertTrue(status.reportFound());
        assertNull(status.reportUrl());
        assertEquals(project.resolve("build/reports/tests/mnTest/index.html").toString(), status.reportFile());
        assertEquals("2026-10-11T10:00:05Z", status.updatedAt());
        assertEquals(3, status.lastRun().passed());
        assertEquals(1, status.lastRun().failed());
        assertEquals(1, status.lastRun().errored());
        assertEquals(6, status.lastRun().total());
        assertFalse(status.lastRun().successful());
        assertEquals("2 failing", status.badge());
        assertEquals(2, status.history().size());
        assertEquals(2, status.failureCount());
        assertEquals(List.of("shouldAdd()", "shouldDivide()"), status.failures().stream().map(ContinuousTestingStatus.Failure::displayName).toList());
        assertEquals("expected: <4> but was: <5>", status.failures().get(0).message(), "the first line of the message");
        assertFalse(status.runInProgress(), "a stopped process runs nothing");
        assertNull(status.compileFailure());
    }

    @Test
    void aReportWrittenWithoutALiveServerMayBeOfARunningProcess() throws IOException {
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState("").replace("\"live\":true", "\"live\":false"));

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertFalse(status.running());
        assertTrue(status.mayBeRunning());
        assertNull(status.reportUrl());
        assertEquals(2, status.failureCount());
    }

    @Test
    void listsAtMostTheConfiguredNumberOfFailures() throws IOException {
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState(""));

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".max-failures", 1)).resolve();

        assertEquals(1, status.failures().size());
        assertEquals(2, status.failureCount());
        assertTrue(status.moreFailures());
    }

    @Test
    void readsACompileFailure() throws IOException {
        String state = """
            {"version":1,"project":"%s","generatedAt":"2026-10-11T10:00:05Z","live":false,"running":null,"finished":0,
             "current":[],"last":null,"history":[],
             "compileFailure":{"kind":"java","at":"2026-10-11T10:00:04Z","diagnostics":[
               {"severity":"error","message":"cannot find symbol\\n  symbol: foo","file":"/p/src/main/java/A.java","line":12,"column":5}]},
             "classes":[],"sources":{}}
            """.formatted(project.getFileName());
        writeReport(project.resolve("target/micronaut-dev/test-report"), state);

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertEquals(List.of("/p/src/main/java/A.java:12: cannot find symbol"), status.compileFailure());
        assertEquals("Does not compile", status.badge());
    }

    @Test
    void theReportOfALiveProcessThatNamesItsPidAndUrl() throws IOException {
        ProcessHandle current = ProcessHandle.current();
        writeReport(project.resolve("__pyronaut__/reports/tests"), failingState("\"pid\":" + current.pid()
            + ",\"pidStartedAt\":\"" + current.info().startInstant().orElseThrow() + "\",\"url\":\"http://localhost:35729/reports/tests/\","));

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertTrue(status.running());
        assertEquals("http://localhost:35729/reports/tests/", status.reportUrl());
    }

    @Test
    void theReportOfAProcessThatExitedDoesNotRun() throws IOException, InterruptedException {
        Process exited = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java"), "-version")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        exited.waitFor();
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState("\"pid\":" + exited.pid() + ",\"pidStartedAt\":\"" + Instant.now() + "\","));

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertFalse(status.running());
        assertNull(status.reportUrl());
        assertTrue(status.reportFound());
    }

    @Test
    void theLiveProcessOfAnotherProjectsReportIsNotThisProjects() throws IOException {
        ProcessHandle current = ProcessHandle.current();
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState("\"pid\":" + current.pid()
            + ",\"pidStartedAt\":\"" + current.info().startInstant().orElseThrow() + "\",")
            .replace("\"project\":\"" + project.getFileName() + "\"", "\"project\":\"other\""));

        assertFalse(resolver(Map.of()).resolve().running());
    }

    @Test
    void theConfiguredReportUrlWinsOverTheOneTheReportNames() throws IOException {
        ProcessHandle current = ProcessHandle.current();
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState("\"pid\":" + current.pid()
            + ",\"pidStartedAt\":\"" + current.info().startInstant().orElseThrow() + "\",\"url\":\"http://localhost:35729/tests/\","));

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".report-url", "http://localhost:35730/reports/")).resolve();

        assertTrue(status.running());
        assertEquals("http://localhost:35730/reports/", status.reportUrl());
    }

    @Test
    void aLiveProcessWhoseReportNamesNoUrlLinksToTheDefaultOneWhenItServesTheReport() throws IOException {
        ProcessHandle current = ProcessHandle.current();
        Path report = project.resolve("build/reports/tests/mnTest");
        writeReport(report, failingState("\"pid\":" + current.pid() + ",\"pidStartedAt\":\"" + current.info().startInstant().orElseThrow() + "\","));
        int port = serve("/tests/state.json", Files.readString(report.resolve("state.json")));

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port)).resolve();

        assertTrue(status.running());
        assertEquals("http://localhost:" + port + "/tests/", status.reportUrl());
    }

    @Test
    void aProcessThatReusedTheIdOfTheReportIsNotItsProcess() throws IOException {
        ProcessHandle current = ProcessHandle.current();
        Instant started = current.info().startInstant().orElseThrow();
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState("\"pid\":" + current.pid()
            + ",\"pidStartedAt\":\"" + started.minus(Duration.ofHours(1)) + "\",\"url\":\"http://localhost:35729/tests/\","));

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertFalse(status.running());
        assertNull(status.reportUrl());
    }

    @Test
    void theLiveReportOfAnotherProjectIsNotThisOneEvenWhenTheLocalReportIsOfIt() throws IOException {
        // a stale report of another project, in this project's report directory
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState("").replace("\"project\":\"" + project.getFileName() + "\"", "\"project\":\"other\""));
        int port = serve("/tests/state.json", "{\"version\":1,\"project\":\"other\",\"live\":true}");

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port)).resolve();

        assertFalse(status.running());
    }

    @Test
    void aReplacedReportIsReadAgain() throws IOException {
        Path report = project.resolve("build/reports/tests/mnTest");
        writeReport(report, failingState(""));
        ContinuousTestingStatusResolver resolver = resolver(Map.of());
        assertEquals(2, resolver.resolve().failureCount());

        // replaced as the report replaces it, with a move, keeping its time and size
        Path state = report.resolve("state.json");
        String passing = failingState("").replace("\"status\":\"failed\"", "\"status\":\"passed\"").replace("\"status\":\"errored\"", "\"status\":\"passed\" ");
        FileTime modified = Files.getLastModifiedTime(state);
        Path replacement = report.resolve("state.json.tmp");
        Files.writeString(replacement, passing);
        Files.setLastModifiedTime(replacement, modified);
        Files.move(replacement, state, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        assertEquals(0, resolver.resolve().failureCount());
    }

    @Test
    void aLiveReloadServerServingTheReportOfTheProjectRuns() throws IOException {
        Path report = project.resolve("build/reports/tests/mnTest");
        writeReport(report, failingState(""));
        int port = serve("/tests/state.json", Files.readString(report.resolve("state.json")));

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port)).resolve();

        assertTrue(status.running());
        assertEquals("http://localhost:" + port + "/tests/", status.reportUrl());
        assertTrue(status.runInProgress(), "the report names a run in progress");
        assertEquals(1, status.finishedInRun());
        assertEquals("Running tests", status.badge());
    }

    @Test
    void theReportOfAnotherProjectOnThePortIsNotThisProjects() throws IOException {
        writeReport(project.resolve("build/reports/tests/mnTest"), failingState(""));
        int port = serve("/tests/state.json", "{\"version\":1,\"project\":\"another-project\",\"live\":true}");

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port)).resolve();

        assertFalse(status.running());
        assertNull(status.reportUrl());
    }

    @Test
    void jsonThatIsNotAReportIsNotALiveReport() throws IOException {
        int port = serve("/tests/state.json", "{}");

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port)).resolve();

        assertFalse(status.running());
        assertNull(status.reportUrl());
    }

    @Test
    void theProjectNameOfALiveReportIsDecoded() throws IOException {
        Path escaped = Files.createDirectories(project.resolve("R&D \"app\""));
        int port = serve("/tests/state.json", "{\"version\":1,\"project\":\"R\\u0026D \\\"app\\\"\",\"live\":true}");

        ContinuousTestingStatus status = resolver(Map.of(
            ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port,
            ContinuousTestingConfiguration.PREFIX + ".project-directory", escaped.toString()
        )).resolve();

        assertTrue(status.running());
        assertEquals("R&D \"app\"", status.project());
    }

    @Test
    void aCancelledRunIsNotFailing() {
        ContinuousTestingStatus.Run cancelled = new ContinuousTestingStatus.Run(2, 0, 0, 0, 10, "all", null, true);
        ContinuousTestingStatus status = new ContinuousTestingStatus(true, false, null, null, null, null, false, 0, cancelled,
            List.of(), List.of(), 0, null, List.of());

        assertFalse(cancelled.successful());
        assertEquals("Cancelled", status.badge());
    }

    @Test
    void theStateOfTheLiveReportWhenNoReportDirectoryIsKnown() throws IOException {
        int port = serve("/reports/tests/state.json", failingState(""));

        ContinuousTestingStatus status = resolver(Map.of(
            ContinuousTestingConfiguration.PREFIX + ".live-reload-port", port,
            ContinuousTestingStatusResolver.HTML_REPORT_PATH_PROPERTY, "/reports/tests/"
        )).resolve();

        assertTrue(status.running());
        assertEquals("http://localhost:" + port + "/reports/tests/", status.reportUrl());
        assertNull(status.reportFile());
        assertEquals(2, status.failureCount());
    }

    @Test
    void theReportDirectoryOfTheDevelopmentProperty() throws IOException {
        writeReport(project.resolve("custom/report"), failingState(""));
        writeReport(project.resolve("build/reports/tests/mnTest"), "{}");

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingStatusResolver.HTML_REPORT_PROPERTY, "custom/report")).resolve();

        assertEquals(project.resolve("custom/report/index.html").toString(), status.reportFile());
        assertEquals(2, status.failureCount());
    }

    @Test
    void aMalformedReportIsFoundButHasNoResults() throws IOException {
        writeReport(project.resolve("build/reports/tests/mnTest"), "{\"version\":1,");

        ContinuousTestingStatus status = resolver(Map.of()).resolve();

        assertTrue(status.reportFound());
        assertNull(status.lastRun());
        assertEquals(0, status.failureCount());
    }

    @Test
    void onlyLoopbackHttpAddresses() {
        assertEquals("http://localhost:35729/tests/", ContinuousTestingStatusResolver.loopbackUrl("http://localhost:35729/tests"));
        assertEquals("http://127.0.0.1:1234/t/", ContinuousTestingStatusResolver.loopbackUrl("http://127.0.0.1:1234/t/"));
        assertEquals("http://[::1]:1234/t/", ContinuousTestingStatusResolver.loopbackUrl("http://[::1]:1234/t/"));
        assertNull(ContinuousTestingStatusResolver.loopbackUrl("http://example.com/tests/"));
        assertNull(ContinuousTestingStatusResolver.loopbackUrl("javascript:alert(1)"));
        assertNull(ContinuousTestingStatusResolver.loopbackUrl("file:///tmp/index.html"));
        assertNull(ContinuousTestingStatusResolver.loopbackUrl("http://user@localhost/tests/"));
        assertNull(ContinuousTestingStatusResolver.loopbackUrl(""));
    }

    @Test
    void theConfiguredReportUrlMustBeLoopback() throws IOException {
        int port = serve("/tests/state.json", failingState(""));

        ContinuousTestingStatus status = resolver(Map.of(ContinuousTestingConfiguration.PREFIX + ".report-url", "http://localhost:" + port + "/tests")).resolve();

        assertTrue(status.running());
        assertEquals("http://localhost:" + port + "/tests/", status.reportUrl());
    }

    private ContinuousTestingStatusResolver resolver(Map<String, Object> properties) throws IOException {
        Map<String, Object> all = new HashMap<>(properties);
        all.putIfAbsent(ContinuousTestingConfiguration.PREFIX + ".project-directory", project.toString());
        all.putIfAbsent(ContinuousTestingConfiguration.PREFIX + ".live-reload-port", freePort());
        context = ApplicationContext.run(all, Environment.DEVELOPMENT);
        return context.getBean(ContinuousTestingStatusResolver.class);
    }

    private int serve(String path, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static int freePort() throws IOException {
        // a port nothing listens on once the socket closes
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void writeReport(Path directory, String state) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("state.json"), state);
        Files.writeString(directory.resolve("index.html"), "<html></html>");
    }

    private String failingState(String extra) {
        return """
            {"version":1,"project":"%s",%s"generatedAt":"2026-10-11T10:00:05Z","live":true,
             "running":{"runId":3,"runner":"junit-platform","selection":"affected","everything":false,"startedAt":"2026-10-11T10:00:06Z"},
             "finished":1,"current":[],
             "last":{"runId":2,"passed":3,"failed":1,"errored":1,"skipped":1,"durationMillis":1234,"cancelled":false,"complete":true,
                     "selection":"affected by 2 changes","startedAt":"2026-10-11T10:00:00Z"},
             "history":[
               {"runId":2,"passed":3,"failed":1,"errored":1,"skipped":1,"durationMillis":1234,"cancelled":false,"complete":true,"selection":"affected","startedAt":"2026-10-11T10:00:00Z"},
               {"runId":1,"passed":6,"failed":0,"errored":0,"skipped":0,"durationMillis":4321,"cancelled":false,"complete":true,"selection":"all","startedAt":"2026-10-11T09:59:00Z"}
             ],
             "compileFailure":null,
             "classes":[
               {"name":"example.CalculatorTest","tests":[
                 {"id":"[a]","name":"shouldAdd","displayName":"shouldAdd()","status":"failed","durationMillis":3,"message":"expected: <4> but was: <5>\\nmore","stackTrace":"..."},
                 {"id":"[b]","name":"shouldSubtract","displayName":"shouldSubtract()","status":"passed","durationMillis":1}
               ]},
               {"name":"example.DivisionTest","tests":[
                 {"id":"[c]","name":"shouldDivide","displayName":"shouldDivide()","status":"errored","durationMillis":2,"message":"/ by zero"}
               ]}
             ],
             "sources":{}}
            """.formatted(project.getFileName(), extra);
    }
}
