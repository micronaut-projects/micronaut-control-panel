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
package io.micronaut.controlpanel.panels.graalpy;

import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Reads GraalPy virtual filesystem metadata without opening any listed Python files.
 */
@Singleton
public class GraalPyVfsMetadataReader {

    static final String RESOURCE_PATH = "org.graalvm.python.vfs/fileslist.txt";
    static final int MAX_ENTRIES = 500;
    private static final int MAX_LINE_CHARS = 4096;
    private static final int MAX_METADATA_CHARS = 1_000_000;

    /**
     * Constructor.
     */
    public GraalPyVfsMetadataReader() {
    }

    GraalPyVfsMetadata read() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = GraalPyVfsMetadataReader.class.getClassLoader();
        }
        return read(classLoader);
    }

    GraalPyVfsMetadata read(ClassLoader classLoader) {
        List<GraalPyVfsResource> resources = new ArrayList<>();
        List<GraalPyVfsEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean truncated = false;
        int omittedEntries = 0;

        try {
            Enumeration<URL> resourceUrls = classLoader.getResources(RESOURCE_PATH);
            int resourceIndex = 0;
            while (resourceUrls.hasMoreElements()) {
                URL url = resourceUrls.nextElement();
                resourceIndex++;
                ResourceRead read = readResource(resourceIndex, url, MAX_ENTRIES - entries.size());
                resources.add(read.resource());
                entries.addAll(read.entries());
                warnings.addAll(read.warnings());
                truncated = truncated || read.truncated();
                omittedEntries += read.omittedEntries();
                if (entries.size() >= MAX_ENTRIES) {
                    truncated = true;
                }
            }
        } catch (IOException e) {
            warnings.add("Unable to enumerate GraalPy VFS metadata resources.");
        }

        entries.sort(Comparator.comparing(GraalPyVfsEntry::path));
        return new GraalPyVfsMetadata(resources, entries, warnings, truncated, omittedEntries);
    }

    private static ResourceRead readResource(int resourceIndex, URL url, int remainingCapacity) {
        List<GraalPyVfsEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean truncated = remainingCapacity <= 0;
        int omittedEntries = 0;
        GraalPyVfsResource resource = new GraalPyVfsResource(resourceLabel(resourceIndex, url), protocolLabel(url), 0, false);

        if (!isLocalClasspathResource(url)) {
            warnings.add("Skipped non-local GraalPy VFS metadata in " + resource.label() + ".");
            resource = new GraalPyVfsResource(resource.label(), resource.kind(), 0, true);
            return new ResourceRead(resource, entries, warnings, truncated, omittedEntries);
        }

        try (Reader reader = new BufferedReader(new InputStreamReader(openLocalStream(url), StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            ParseResult result = parseMetadata(reader, resource, entries, warnings, truncated, remainingCapacity);
            resource = new GraalPyVfsResource(resource.label(), resource.kind(), result.lineCount(), false);
            truncated = result.truncated();
            omittedEntries = result.omittedEntries();
        } catch (IOException e) {
            warnings.add("Unable to read " + resource.label() + "; partial GraalPy VFS metadata is shown when available.");
            resource = new GraalPyVfsResource(resource.label(), resource.kind(), 0, true);
        }
        return new ResourceRead(resource, entries, warnings, truncated, omittedEntries);
    }

    private static InputStream openLocalStream(URL url) throws IOException {
        try {
            if ("file".equals(url.getProtocol())) {
                return Files.newInputStream(Path.of(URI.create(url.toExternalForm())));
            }
            return openLocalJarStream(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid local resource URL", e);
        }
    }

    private static InputStream openLocalJarStream(URL url) throws IOException {
        String file = url.getFile();
        int separator = file.indexOf("!/");
        Path jarPath = Path.of(URI.create(file.substring(0, separator)));
        String entryPath = file.substring(separator + 2);
        JarFile jarFile = new JarFile(jarPath.toFile());
        var entry = jarFile.getJarEntry(entryPath);
        if (entry == null) {
            jarFile.close();
            throw new IOException("Missing jar entry");
        }
        InputStream entryStream = jarFile.getInputStream(entry);
        return new FilterInputStream(entryStream) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    jarFile.close();
                }
            }
        };
    }

    private static ParseResult parseMetadata(Reader reader,
                                             GraalPyVfsResource resource,
                                             List<GraalPyVfsEntry> entries,
                                             List<String> warnings,
                                             boolean truncated,
                                             int remainingCapacity) throws IOException {
        char[] buffer = new char[8192];
        StringBuilder line = new StringBuilder(Math.min(MAX_LINE_CHARS, 256));
        boolean lineOversized = false;
        int lineNumber = 0;
        int metadataChars = 0;
        int omittedEntries = 0;

        int read;
        while ((read = reader.read(buffer)) != -1) {
            for (int i = 0; i < read; i++) {
                metadataChars++;
                if (metadataChars > MAX_METADATA_CHARS) {
                    warnings.add("Stopped reading over-sized VFS metadata in " + resource.label() + ".");
                    return new ParseResult(lineNumber, true, omittedEntries);
                }
                char character = buffer[i];
                if (character == '\n') {
                    lineNumber++;
                    LineResult result = processLine(line, lineOversized, resource, entries, warnings, truncated, remainingCapacity, omittedEntries);
                    truncated = result.truncated();
                    omittedEntries = result.omittedEntries();
                    line.setLength(0);
                    lineOversized = false;
                } else if (character != '\r') {
                    if (line.length() < MAX_LINE_CHARS) {
                        line.append(character);
                    } else {
                        lineOversized = true;
                    }
                }
            }
        }
        if (!line.isEmpty() || lineOversized) {
            lineNumber++;
            LineResult result = processLine(line, lineOversized, resource, entries, warnings, truncated, remainingCapacity, omittedEntries);
            truncated = result.truncated();
            omittedEntries = result.omittedEntries();
        }
        return new ParseResult(lineNumber, truncated, omittedEntries);
    }

    private static LineResult processLine(StringBuilder line,
                                          boolean lineOversized,
                                          GraalPyVfsResource resource,
                                          List<GraalPyVfsEntry> entries,
                                          List<String> warnings,
                                          boolean truncated,
                                          int remainingCapacity,
                                          int omittedEntries) {
        if (lineOversized) {
            warnings.add("Skipped an over-sized VFS metadata line in " + resource.label() + ".");
            return new LineResult(truncated, omittedEntries);
        }
        String path = sanitizePath(line.toString());
        if (path.isBlank()) {
            return new LineResult(truncated, omittedEntries);
        }
        if (entries.size() < remainingCapacity) {
            entries.add(new GraalPyVfsEntry(path, group(path), resource.label()));
        } else {
            truncated = true;
            omittedEntries++;
        }
        return new LineResult(truncated, omittedEntries);
    }

    private static boolean isLocalClasspathResource(URL url) {
        return switch (url.getProtocol()) {
            case "file" -> true;
            case "jar" -> isLocalJarResource(url);
            default -> false;
        };
    }

    private static boolean isLocalJarResource(URL url) {
        String file = url.getFile();
        int separator = file.indexOf("!/");
        if (separator < 0) {
            return false;
        }
        try {
            return "file".equals(URI.create(file.substring(0, separator)).getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String sanitizePath(String line) {
        String path = line.strip();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private static String group(String path) {
        if (path.startsWith("src/")) {
            return "src";
        }
        if (path.contains("/site-packages/")) {
            return "site-packages";
        }
        if (path.startsWith("venv/")) {
            return "venv";
        }
        int slash = path.indexOf('/');
        return slash > 0 ? path.substring(0, slash) : "other";
    }

    private static String protocolLabel(URL url) {
        String protocol = url.getProtocol();
        return protocol == null || protocol.isBlank() ? "resource" : protocol;
    }

    private static String resourceLabel(int resourceIndex, URL url) {
        return "classpath resource #" + resourceIndex + " (" + protocolLabel(url) + ")";
    }

    private record ResourceRead(
        GraalPyVfsResource resource,
        List<GraalPyVfsEntry> entries,
        List<String> warnings,
        boolean truncated,
        int omittedEntries) {
    }

    private record ParseResult(int lineCount, boolean truncated, int omittedEntries) {
    }

    private record LineResult(boolean truncated, int omittedEntries) {
    }

    /**
     * GraalPy VFS metadata discovered from classpath resources.
     *
     * @param resources metadata resources
     * @param entries VFS file entries
     * @param warnings safe warnings for partial metadata
     * @param truncated whether displayed metadata was truncated
     * @param omittedEntries entries omitted after the display cap
     */
    @ReflectiveAccess
    public record GraalPyVfsMetadata(
        List<GraalPyVfsResource> resources,
        List<GraalPyVfsEntry> entries,
        List<String> warnings,
        boolean truncated,
        int omittedEntries) {

        /**
         * @return whether metadata resources were found
         */
        public boolean hasResources() {
            return !resources.isEmpty();
        }

        /**
         * @return whether any VFS entries were read
         */
        public boolean hasEntries() {
            return !entries.isEmpty();
        }

        /**
         * @return whether partial-data warnings are available
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        /**
         * @return number of metadata resources
         */
        public int resourceCount() {
            return resources.size();
        }

        /**
         * @return number of displayed VFS entries
         */
        public int entryCount() {
            return entries.size();
        }
    }

    /**
     * Sanitized metadata resource description.
     *
     * @param label display label
     * @param kind resource protocol kind
     * @param lineCount line count read
     * @param unreadable whether the resource could not be read
     */
    @ReflectiveAccess
    public record GraalPyVfsResource(String label, String kind, int lineCount, boolean unreadable) {
    }

    /**
     * Packaged VFS path entry.
     *
     * @param path relative VFS path
     * @param group display group
     * @param resource sanitized source resource label
     */
    @ReflectiveAccess
    public record GraalPyVfsEntry(String path, String group, String resource) {
    }
}
