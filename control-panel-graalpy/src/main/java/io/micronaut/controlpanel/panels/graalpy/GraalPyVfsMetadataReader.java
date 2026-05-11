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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;
            int lineNumber = 0;
            int metadataChars = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                metadataChars += line.length();
                if (metadataChars > MAX_METADATA_CHARS) {
                    warnings.add("Stopped reading over-sized VFS metadata in " + resource.label() + ".");
                    truncated = true;
                    break;
                }
                if (line.length() > MAX_LINE_CHARS) {
                    warnings.add("Skipped an over-sized VFS metadata line in " + resource.label() + ".");
                    continue;
                }
                String path = sanitizePath(line);
                if (path.isBlank()) {
                    continue;
                }
                if (entries.size() < remainingCapacity) {
                    entries.add(new GraalPyVfsEntry(path, group(path), resource.label()));
                } else {
                    truncated = true;
                    omittedEntries++;
                }
            }
            resource = new GraalPyVfsResource(resource.label(), resource.kind(), lineNumber, false);
        } catch (IOException e) {
            warnings.add("Unable to read " + resource.label() + "; partial GraalPy VFS metadata is shown when available.");
            resource = new GraalPyVfsResource(resource.label(), resource.kind(), 0, true);
        }
        return new ResourceRead(resource, entries, warnings, truncated, omittedEntries);
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
