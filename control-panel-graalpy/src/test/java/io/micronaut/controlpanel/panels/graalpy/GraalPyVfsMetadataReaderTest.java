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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalPyVfsMetadataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPackagedFilesListMetadata() throws IOException {
        writeFilesList("""
            /src/dealer.py
            venv/lib/python3.13/site-packages/termcolor/__init__.py
            pyproject.toml
            """);

        var metadata = read();

        assertTrue(metadata.hasResources());
        assertEquals(1, metadata.resourceCount());
        assertEquals(3, metadata.entryCount());
        assertTrue(metadata.entries().stream().anyMatch(entry -> entry.path().equals("src/dealer.py")));
        assertTrue(metadata.entries().stream().anyMatch(entry -> entry.group().equals("site-packages")));
        assertFalse(metadata.truncated());
    }

    @Test
    void missingFilesListIsAnEmptyState() {
        var metadata = read();

        assertFalse(metadata.hasResources());
        assertFalse(metadata.hasEntries());
        assertFalse(metadata.truncated());
    }

    @Test
    void readsLocalJarFilesListMetadata() throws IOException {
        Path jar = tempDir.resolve("graalpy.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jar))) {
            jarOutputStream.putNextEntry(new JarEntry(GraalPyVfsMetadataReader.RESOURCE_PATH));
            jarOutputStream.write("src/dealer.py\n".getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[] { jar.toUri().toURL() }, null)) {
            var metadata = new GraalPyVfsMetadataReader().read(classLoader);

            assertTrue(metadata.hasResources());
            assertEquals(1, metadata.entryCount());
            assertEquals("jar", metadata.resources().get(0).kind());
            assertEquals("src/dealer.py", metadata.entries().get(0).path());
        }
    }

    @Test
    void capsLargeFilesListMetadata() throws IOException {
        writeFilesList(IntStream.range(0, GraalPyVfsMetadataReader.MAX_ENTRIES + 2)
            .mapToObj(index -> "src/file" + index + ".py")
            .collect(Collectors.joining("\n")));

        var metadata = read();

        assertEquals(GraalPyVfsMetadataReader.MAX_ENTRIES, metadata.entryCount());
        assertTrue(metadata.truncated());
        assertEquals(2, metadata.omittedEntries());
    }

    @Test
    void exactEntryCapIsNotReportedAsTruncatedWhenNoEntriesAreOmitted() throws IOException {
        writeFilesList(IntStream.range(0, GraalPyVfsMetadataReader.MAX_ENTRIES)
            .mapToObj(index -> "src/file" + index + ".py")
            .collect(Collectors.joining("\n")));

        var metadata = read();

        assertEquals(GraalPyVfsMetadataReader.MAX_ENTRIES, metadata.entryCount());
        assertFalse(metadata.truncated());
        assertEquals(0, metadata.omittedEntries());
    }

    @Test
    @SuppressWarnings("deprecation")
    void skipsNonLocalMetadataResourcesWithoutOpeningThem() throws IOException {
        AtomicBoolean opened = new AtomicBoolean();
        URL nonLocalResource = new URL(null, "https://example.invalid/" + GraalPyVfsMetadataReader.RESOURCE_PATH, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                opened.set(true);
                throw new AssertionError("Non-local VFS metadata URL must not be opened");
            }
        });
        ClassLoader classLoader = new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                assertEquals(GraalPyVfsMetadataReader.RESOURCE_PATH, name);
                return Collections.enumeration(Collections.singleton(nonLocalResource));
            }
        };

        var metadata = new GraalPyVfsMetadataReader().read(classLoader);

        assertTrue(metadata.hasResources());
        assertEquals(1, metadata.resourceCount());
        assertFalse(metadata.hasEntries());
        assertTrue(metadata.resources().get(0).unreadable());
        assertTrue(metadata.warnings().stream().anyMatch(warning -> warning.contains("Skipped non-local GraalPy VFS metadata")));
        assertFalse(opened.get());
    }

    @Test
    void skipsOversizedSingleLineMetadataWithoutDisplayingIt() throws IOException {
        writeFilesList("src/" + "a".repeat(5000) + ".py");

        var metadata = read();

        assertEquals(1, metadata.resourceCount());
        assertFalse(metadata.hasEntries());
        assertTrue(metadata.warnings().stream().anyMatch(warning -> warning.contains("Skipped an over-sized VFS metadata line")));
    }

    private GraalPyVfsMetadataReader.GraalPyVfsMetadata read() {
        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[] { tempDir.toUri().toURL() }, null)) {
            return new GraalPyVfsMetadataReader().read(classLoader);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void writeFilesList(String content) throws IOException {
        Path filesList = tempDir.resolve(GraalPyVfsMetadataReader.RESOURCE_PATH);
        Files.createDirectories(filesList.getParent());
        Files.writeString(filesList, content);
    }
}
