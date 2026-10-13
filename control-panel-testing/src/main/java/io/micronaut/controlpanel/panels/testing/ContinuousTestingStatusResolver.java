/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.testing;

import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the continuous testing process of the project and the latest state of its report.
 *
 * <p>Continuous testing runs in a process of its own, the test mode of the development runtime, whose
 * {@code micronaut-dev-test-report} writes {@code index.html} and {@code state.json} to its report directory whenever
 * a run starts or finishes. When the LiveReload server of that process runs, it serves the directory, by default at
 * {@code http://localhost:35729/tests/}.</p>
 *
 * <p>The resolver reads {@code state.json} from the report directory: the one configured, the one
 * {@code micronaut.dev.test.html-report} names, or the first of the directories the build plugins use that holds a
 * report. The process runs when the report names a live process, by its id, {@code pid}, and the instant it started,
 * {@code pidStartedAt}, or else when its LiveReload server serves the report of the project.</p>
 *
 * @since 2.3.0
 */
@Singleton
public class ContinuousTestingStatusResolver {

    /**
     * The property naming the report directory of continuous testing.
     */
    public static final String HTML_REPORT_PROPERTY = "micronaut.dev.test.html-report";

    /**
     * The property naming the path the LiveReload server serves the report at.
     */
    public static final String HTML_REPORT_PATH_PROPERTY = "micronaut.dev.test.html-report-path";

    /**
     * The report directories of the build plugins and of the development runtime, relative to the project directory,
     * in the order they are looked for.
     */
    public static final List<String> DEFAULT_REPORT_DIRECTORIES = List.of(
        // ./gradlew mnTest
        "build/reports/tests/mnTest",
        // mvn mn:test
        "target/micronaut-dev/test-report",
        // pyronaut test -t
        "__pyronaut__/reports/tests",
        // micronaut-dev itself
        "build/micronaut-dev/test-report"
    );

    static final String STATE_FILE = "state.json";
    static final String INDEX_FILE = "index.html";
    static final String DEFAULT_REPORT_PATH = "/tests/";

    private static final Logger LOG = LoggerFactory.getLogger(ContinuousTestingStatusResolver.class);
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final Pattern PROJECT = Pattern.compile("\"project\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\\d+");
    private static final int PROBE_BYTES = 1024;
    private static final long MAX_STATE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_HISTORY = 10;
    private static final String FAILED = "failed";
    private static final String ERRORED = "errored";
    private static final long PROBE_TTL_NANOS = 1_000_000_000L;
    // the start instant of a process is known to the millisecond on some systems, to the clock tick on others
    private static final Duration PID_START_TOLERANCE = Duration.ofSeconds(1);

    private final ContinuousTestingConfiguration configuration;
    private final Environment properties;
    private volatile @Nullable CachedState cached;
    private volatile @Nullable CachedProbe lastProbe;

    /**
     * @param configuration The panel's configuration
     * @param properties The environment of the application
     */
    public ContinuousTestingStatusResolver(ContinuousTestingConfiguration configuration,
                                           Environment properties) {
        this.configuration = configuration;
        this.properties = properties;
    }

    /**
     * Finds the status of continuous testing.
     *
     * @return The status
     */
    public ContinuousTestingStatus resolve() {
        Path projectDirectory = projectDirectory();
        Path reportDirectory = reportDirectory(projectDirectory);
        Path stateFile = reportDirectory == null ? null : reportDirectory.resolve(STATE_FILE);
        Map<?, ?> state = stateFile == null ? null : readState(stateFile);
        // the report names its project after the project directory: the live report of another project is not this one's
        Path directoryName = projectDirectory.getFileName();
        String expectedProject = directoryName == null ? null : directoryName.toString();
        String reported = state == null ? null : text(state, "project");
        String project = reported == null ? expectedProject : reported;

        boolean running = false;
        boolean mayBeRunning = false;
        String liveUrl = null;
        Long pid = state == null ? null : longValue(state, "pid");
        Instant pidStartedAt = state == null ? null : instant(text(state, "pidStartedAt"));
        Boolean processAlive = pid == null || pidStartedAt == null ? null : processAlive(pid, pidStartedAt);
        if (processAlive != null) {
            // the process of another project's report is not this project's
            running = processAlive && sameProject(reported, expectedProject);
            String configuredUrl = loopbackUrl(configuration.reportUrl());
            String url = configuredUrl != null ? configuredUrl : bool(state, "live") ? loopbackUrl(text(state, "url")) : null;
            if (running && url == null && bool(state, "live")) {
                // a report served live that does not name its address: the default one, when it serves it
                String fallback = reportUrl(state);
                url = fallback != null && serves(fallback, expectedProject) ? fallback : null;
            }
            if (running) {
                liveUrl = url;
            }
        } else {
            String url = reportUrl(state);
            if (url != null && serves(url, expectedProject)) {
                running = true;
                liveUrl = url;
                if (state == null) {
                    state = fetchState(url);
                }
            } else if (state != null && !bool(state, "live")) {
                // a process whose LiveReload server could not start, or that runs without one, is only seen by its
                // report: that report does not tell whether it still runs
                mayBeRunning = true;
            }
        }

        Path index = reportDirectory == null ? null : reportDirectory.resolve(INDEX_FILE);
        String reportFile = index != null && Files.isRegularFile(index) ? index.toAbsolutePath().normalize().toString() : null;
        List<ContinuousTestingStatus.Command> commands = commands(projectDirectory);
        if (state == null) {
            return new ContinuousTestingStatus(running, mayBeRunning, liveUrl, reportFile, project, null, false, 0, null,
                List.of(), List.of(), 0, null, commands);
        }
        List<ContinuousTestingStatus.Failure> failures = new ArrayList<>();
        int failureCount = failures(state, failures);
        List<ContinuousTestingStatus.Run> history = new ArrayList<>();
        List<?> historyNode = array(state, "history");
        if (historyNode != null) {
            for (Object run : historyNode) {
                if (history.size() == MAX_HISTORY) {
                    break;
                }
                ContinuousTestingStatus.Run parsed = run(run);
                if (parsed != null) {
                    history.add(parsed);
                }
            }
        }
        boolean runInProgress = running && state.get("running") instanceof Map;
        Integer finished = intValue(state, "finished");
        return new ContinuousTestingStatus(
            running,
            mayBeRunning,
            liveUrl,
            reportFile,
            project,
            text(state, "generatedAt"),
            runInProgress,
            finished == null ? 0 : finished,
            run(state.get("last")),
            List.copyOf(history),
            List.copyOf(failures),
            failureCount,
            compileFailure(state.get("compileFailure")),
            commands
        );
    }

    /**
     * @return The project directory the report directories resolve against
     */
    Path projectDirectory() {
        String configured = configuration.projectDirectory();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    /**
     * The report directory: the one configured, or the one {@code micronaut.dev.test.html-report} names, else the
     * directory of the build plugins with the latest report.
     *
     * @param projectDirectory The project directory
     * @return The report directory, or null when there is none
     */
    @Nullable Path reportDirectory(Path projectDirectory) {
        String configured = configuration.reportDirectory();
        if (configured == null || configured.isBlank()) {
            configured = properties.getProperty(HTML_REPORT_PROPERTY, String.class).orElse(null);
        }
        if (configured != null && !configured.isBlank()) {
            return projectDirectory.resolve(configured).normalize();
        }
        Path latest = null;
        long latestModified = Long.MIN_VALUE;
        for (String candidate : DEFAULT_REPORT_DIRECTORIES) {
            Path directory = projectDirectory.resolve(candidate);
            Path state = directory.resolve(STATE_FILE);
            if (Files.isRegularFile(state)) {
                long modified = lastModified(state);
                if (latest == null || modified > latestModified) {
                    latest = directory;
                    latestModified = modified;
                }
            }
        }
        return latest;
    }

    /**
     * The address to look for the live report at.
     *
     * @param state The state of the report, when there is one
     * @return The address, ending with a slash, or null when it is not a loopback HTTP address
     */
    @Nullable String reportUrl(@Nullable Map<?, ?> state) {
        String configured = configuration.reportUrl();
        if (configured != null && !configured.isBlank()) {
            return loopbackUrl(configured);
        }
        String named = state == null ? null : loopbackUrl(text(state, "url"));
        if (named != null) {
            return named;
        }
        String path = properties.getProperty(HTML_REPORT_PATH_PROPERTY, String.class).orElse(DEFAULT_REPORT_PATH).strip();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return loopbackUrl("http://localhost:" + configuration.liveReloadPort() + path);
    }

    /**
     * Whether a LiveReload server serves the report of the project at an address.
     *
     * @param url The address of the report
     * @param project The project the report must belong to, or null to accept any
     * @return True when it does
     */
    boolean serves(String url, @Nullable String project) {
        long now = System.nanoTime();
        CachedProbe last = lastProbe;
        if (last != null && last.url.equals(url) && Objects.equals(last.project, project) && now - last.probedAt < PROBE_TTL_NANOS) {
            return last.serves;
        }
        boolean serves = probe(url, project);
        lastProbe = new CachedProbe(url, project, serves, now);
        return serves;
    }

    private boolean probe(String url, @Nullable String project) {
        byte[] head = get(url + STATE_FILE, PROBE_BYTES);
        if (head == null) {
            return false;
        }
        String text = new String(head, StandardCharsets.UTF_8);
        // a report starts with its version and its project: anything else is not one
        Matcher matcher = PROJECT.matcher(text);
        if (!text.stripLeading().startsWith("{") || !VERSION.matcher(text).find() || !matcher.find()) {
            return false;
        }
        String served;
        try {
            served = ReportJson.parse("\"" + matcher.group(1) + "\"") instanceof String name ? name : "";
        } catch (IllegalArgumentException e) {
            return false;
        }
        // the report of another project on the same port is not this project's
        return sameProject(served, project);
    }

    private @Nullable Map<?, ?> fetchState(String url) {
        byte[] body = get(url + STATE_FILE, MAX_STATE_BYTES);
        return body == null ? null : parse(body, url + STATE_FILE);
    }

    private @Nullable Map<?, ?> readState(Path stateFile) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(stateFile, BasicFileAttributes.class);
        } catch (IOException e) {
            return null;
        }
        FileTime modified = attributes.lastModifiedTime();
        long size = attributes.size();
        // the report replaces state.json with a move: a new file, whose key changes where the file system has keys
        Object key = attributes.fileKey();
        CachedState current = cached;
        if (current != null && current.file.equals(stateFile) && current.modified.equals(modified) && current.size == size
            && key != null && key.equals(current.key)) {
            return current.state;
        }
        if (size > MAX_STATE_BYTES) {
            LOG.debug("The continuous testing report {} is too large to read: {} bytes", stateFile, size);
            return null;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(stateFile);
        } catch (IOException e) {
            LOG.debug("Cannot read the continuous testing report {}: {}", stateFile, e.getMessage());
            return null;
        }
        Map<?, ?> state = parse(bytes, stateFile.toString());
        if (state != null) {
            cached = new CachedState(stateFile, key, modified, size, state);
        }
        return state;
    }

    private static @Nullable Map<?, ?> parse(byte[] bytes, String source) {
        try {
            return ReportJson.parse(new String(bytes, StandardCharsets.UTF_8)) instanceof Map<?, ?> map ? map : null;
        } catch (IllegalArgumentException e) {
            // a report being replaced, or one of another version
            LOG.debug("Cannot read the continuous testing report {}: {}", source, e.getMessage());
            return null;
        }
    }

    private byte @Nullable [] get(String url, long limit) {
        HttpURLConnection connection = null;
        try {
            int timeout = (int) Math.max(1, Math.min(Integer.MAX_VALUE, configuration.probeTimeout().toMillis()));
            connection = (HttpURLConnection) new URI(url).toURL().openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return read(in, limit);
            }
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            LOG.trace("No continuous testing report at {}: {}", url, e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] read(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while (total < limit && (read = in.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }

    /**
     * An HTTP address on the loopback interface, ending with a slash: the panel neither probes nor links to anything
     * else a report names.
     */
    static @Nullable String loopbackUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || !LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                return null;
            }
            String normalized = uri.toString();
            return normalized.endsWith("/") ? normalized : normalized + "/";
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private int failures(Map<?, ?> state, List<ContinuousTestingStatus.Failure> failures) {
        List<?> classes = array(state, "classes");
        if (classes == null) {
            return 0;
        }
        int count = 0;
        int max = Math.max(0, configuration.maxFailures());
        for (Object element : classes) {
            if (!(element instanceof Map<?, ?> testClass)) {
                continue;
            }
            String className = text(testClass, "name");
            List<?> tests = array(testClass, "tests");
            if (tests == null) {
                continue;
            }
            for (Object testElement : tests) {
                if (!(testElement instanceof Map<?, ?> test)) {
                    continue;
                }
                String status = text(test, "status");
                if (!FAILED.equals(status) && !ERRORED.equals(status)) {
                    continue;
                }
                count++;
                if (failures.size() < max) {
                    String displayName = text(test, "displayName");
                    failures.add(new ContinuousTestingStatus.Failure(
                        className == null ? "" : className,
                        displayName == null ? String.valueOf(text(test, "name")) : displayName,
                        status,
                        firstLine(text(test, "message"))
                    ));
                }
            }
        }
        return count;
    }

    private static ContinuousTestingStatus.@Nullable Run run(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> run)) {
            return null;
        }
        Long duration = longValue(run, "durationMillis");
        return new ContinuousTestingStatus.Run(
            intOrZero(run, "passed"),
            intOrZero(run, "failed"),
            intOrZero(run, "errored"),
            intOrZero(run, "skipped"),
            duration == null ? 0 : duration,
            text(run, "selection"),
            text(run, "startedAt"),
            bool(run, "cancelled")
        );
    }

    private static @Nullable List<String> compileFailure(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> failure)) {
            return null;
        }
        List<String> diagnostics = new ArrayList<>();
        List<?> nodes = array(failure, "diagnostics");
        if (nodes != null) {
            for (Object element : nodes) {
                if (!(element instanceof Map<?, ?> diagnostic)) {
                    continue;
                }
                String file = text(diagnostic, "file");
                Integer line = intValue(diagnostic, "line");
                String message = firstLine(text(diagnostic, "message"));
                StringBuilder text = new StringBuilder();
                if (file != null) {
                    text.append(file);
                    if (line != null && line > 0) {
                        text.append(':').append(line);
                    }
                    text.append(": ");
                }
                text.append(message == null ? "" : message);
                diagnostics.add(text.toString());
            }
        }
        return List.copyOf(diagnostics);
    }

    /**
     * The commands that start continuous testing, those of the build tools the project uses first.
     *
     * @param projectDirectory The project directory
     * @return The commands
     */
    static List<ContinuousTestingStatus.Command> commands(Path projectDirectory) {
        boolean gradle = exists(projectDirectory, "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts");
        boolean maven = exists(projectDirectory, "pom.xml");
        boolean pyronaut = exists(projectDirectory, "pyproject.toml");
        List<ContinuousTestingStatus.Command> all = List.of(
            new ContinuousTestingStatus.Command("Gradle", exists(projectDirectory, "gradlew") || !gradle ? "./gradlew mnTest" : "gradle mnTest", gradle),
            new ContinuousTestingStatus.Command("Maven", exists(projectDirectory, "mvnw") ? "./mvnw mn:test" : "mvn mn:test", maven),
            new ContinuousTestingStatus.Command("Pyronaut", "pyronaut test -t", pyronaut)
        );
        List<ContinuousTestingStatus.Command> ordered = new ArrayList<>();
        all.stream().filter(ContinuousTestingStatus.Command::detected).forEach(ordered::add);
        all.stream().filter(command -> !command.detected()).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private static boolean exists(Path directory, String... names) {
        for (String name : names) {
            if (Files.exists(directory.resolve(name))) {
                return true;
            }
        }
        return false;
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private static @Nullable String firstLine(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        int newline = stripped.indexOf('\n');
        return newline < 0 ? stripped : stripped.substring(0, newline).strip();
    }

    private static @Nullable String text(Map<?, ?> node, String name) {
        return node.get(name) instanceof String value ? value : null;
    }

    private static @Nullable Long longValue(Map<?, ?> node, String name) {
        return node.get(name) instanceof Number value ? value.longValue() : null;
    }

    private static @Nullable Integer intValue(Map<?, ?> node, String name) {
        return node.get(name) instanceof Number value ? value.intValue() : null;
    }

    private static int intOrZero(Map<?, ?> node, String name) {
        Integer value = intValue(node, name);
        return value == null ? 0 : value;
    }

    private static boolean sameProject(@Nullable String reported, @Nullable String expected) {
        return reported == null || reported.isBlank() || expected == null || expected.isBlank() || reported.equals(expected);
    }

    /**
     * Whether the process a report names runs: a process started at another time is another one with the same, reused,
     * id.
     *
     * @param pid The id of the process
     * @param startedAt When it started
     * @return Whether it runs, or null when the system does not tell when the process with that id started
     */
    private static @Nullable Boolean processAlive(long pid, Instant startedAt) {
        ProcessHandle handle = ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElse(null);
        if (handle == null) {
            return false;
        }
        return handle.info().startInstant()
            .map(started -> Duration.between(started, startedAt).abs().compareTo(PID_START_TOLERANCE) <= 0)
            .orElse(null);
    }

    private static @Nullable Instant instant(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean bool(Map<?, ?> node, String name) {
        return Boolean.TRUE.equals(node.get(name));
    }

    private static @Nullable List<?> array(Map<?, ?> node, String name) {
        return node.get(name) instanceof List<?> value ? value : null;
    }

    /**
     * The state last read from a report file, kept while the file is unchanged.
     *
     * @param file The state file
     * @param key The key of the file in its file system, or null
     * @param modified When it was last modified
     * @param size Its size
     * @param state What it holds
     */
    private record CachedState(Path file, @Nullable Object key, FileTime modified, long size, Map<?, ?> state) {
    }

    /**
     * The answer of the last probe, and when: a page renders the panel's badge and body together.
     *
     * @param url The address of the report
     * @param project The project the report had to belong to
     * @param serves Whether the address served it
     * @param probedAt When, from {@link System#nanoTime()}
     */
    private record CachedProbe(String url, @Nullable String project, boolean serves, long probedAt) {
    }
}
