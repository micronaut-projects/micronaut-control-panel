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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalPyVfsMetadataReaderTest {

    private static final String APPLICATION_FILES_LIST =
        GraalPyVfsMetadataReader.APPLICATION_ROOT + "/" + GraalPyVfsMetadataReader.FILES_LIST;
    private static final String LEGACY_FILES_LIST =
        GraalPyVfsMetadataReader.LEGACY_ROOT + "/" + GraalPyVfsMetadataReader.FILES_LIST;

    @TempDir
    Path tempDir;

    @Test
    void probesTheCoreApplicationRootBeforeTheLegacyRoot() {
        assertEquals(
            List.of(GraalPyVfsMetadataReader.APPLICATION_ROOT, GraalPyVfsMetadataReader.LEGACY_ROOT),
            new GraalPyVfsMetadataReader(null).roots());
    }

    @Test
    void readsPackagedFilesListMetadataFromTheCoreApplicationRoot() throws IOException {
        writeFilesList(APPLICATION_FILES_LIST, """
            /src/dealer.py
            venv/lib/python3.13/site-packages/termcolor/__init__.py
            venv/bin/python
            pyproject.toml
            """);

        var metadata = read();

        assertTrue(metadata.hasResources());
        assertEquals(1, metadata.resourceCount());
        assertEquals(GraalPyVfsMetadataReader.APPLICATION_ROOT, metadata.resources().get(0).root());
        assertEquals(4, metadata.entryCount());
        assertTrue(metadata.entries().stream().anyMatch(entry -> entry.path().equals("src/dealer.py")));
        assertTrue(metadata.entries().stream().anyMatch(entry -> entry.group().equals("site-packages")));
        assertTrue(metadata.entries().stream().anyMatch(entry -> entry.group().equals("venv")));
        assertFalse(metadata.truncated());
    }

    @Test
    void readsPackagedFilesListMetadataFromTheLegacyGraalPyPluginRoot() throws IOException {
        writeFilesList(LEGACY_FILES_LIST, "src/dealer.py\n");

        var metadata = read();

        assertEquals(1, metadata.resourceCount());
        assertEquals(GraalPyVfsMetadataReader.LEGACY_ROOT, metadata.resources().get(0).root());
        assertEquals(1, metadata.entryCount());
    }

    @Test
    void readsBothRootsWhenBothArePackaged() throws IOException {
        writeFilesList(APPLICATION_FILES_LIST, "src/dealer.py\n");
        writeFilesList(LEGACY_FILES_LIST, "venv/bin/python\n");

        var metadata = read();

        assertEquals(2, metadata.resourceCount());
        assertEquals(2, metadata.entryCount());
        assertEquals(
            List.of(GraalPyVfsMetadataReader.APPLICATION_ROOT, GraalPyVfsMetadataReader.LEGACY_ROOT),
            metadata.resources().stream().map(GraalPyVfsMetadataReader.GraalPyVfsResource::root).toList());
    }

    @Test
    void readsAdditionalConfiguredRoots() throws IOException {
        writeFilesList("custom/vfs/" + GraalPyVfsMetadataReader.FILES_LIST, "src/custom.py\n");

        var reader = new GraalPyVfsMetadataReader(List.of("/custom/vfs/"));
        assertEquals(
            List.of(GraalPyVfsMetadataReader.APPLICATION_ROOT, GraalPyVfsMetadataReader.LEGACY_ROOT, "custom/vfs"),
            reader.roots());

        var metadata = readWith(reader);

        assertEquals(1, metadata.entryCount());
        assertEquals("custom/vfs", metadata.resources().get(0).root());
    }

    @Test
    void ignoresBlankAndTraversingConfiguredRoots() {
        var reader = new GraalPyVfsMetadataReader(List.of("  ", "../escape", "ok/root/fileslist.txt"));

        assertEquals(
            List.of(GraalPyVfsMetadataReader.APPLICATION_ROOT, GraalPyVfsMetadataReader.LEGACY_ROOT, "ok/root"),
            reader.roots());
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
            jarOutputStream.putNextEntry(new JarEntry(APPLICATION_FILES_LIST));
            jarOutputStream.write("src/dealer.py\n".getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, null)) {
            var metadata = new GraalPyVfsMetadataReader(null).read(classLoader);

            assertTrue(metadata.hasResources());
            assertEquals(1, metadata.entryCount());
            assertEquals("jar", metadata.resources().get(0).kind());
            assertEquals("src/dealer.py", metadata.entries().get(0).path());
        }
    }

    @Test
    void readsMultipleFilesListResources() throws IOException {
        Path first = tempDir.resolve("first");
        Path second = tempDir.resolve("second");
        writeFilesList(first, APPLICATION_FILES_LIST, "src/dealer.py\n");
        writeFilesList(second, APPLICATION_FILES_LIST, "venv/bin/python\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { first.toUri().toURL(), second.toUri().toURL() }, null)) {
            var metadata = new GraalPyVfsMetadataReader(null).read(classLoader);

            assertEquals(2, metadata.resourceCount());
            assertEquals(2, metadata.entryCount());
            assertFalse(metadata.truncated());
        }
    }

    @Test
    void capsLargeFilesListMetadata() throws IOException {
        writeFilesList(APPLICATION_FILES_LIST, IntStream.range(0, GraalPyVfsMetadataReader.MAX_ENTRIES + 2)
            .mapToObj(index -> "src/file" + index + ".py")
            .collect(Collectors.joining("\n")));

        var metadata = read();

        assertEquals(GraalPyVfsMetadataReader.MAX_ENTRIES, metadata.entryCount());
        assertTrue(metadata.truncated());
        assertEquals(2, metadata.omittedEntries());
    }

    @Test
    void exactEntryCapIsNotReportedAsTruncatedWhenNoEntriesAreOmitted() throws IOException {
        writeFilesList(APPLICATION_FILES_LIST, IntStream.range(0, GraalPyVfsMetadataReader.MAX_ENTRIES)
            .mapToObj(index -> "src/file" + index + ".py")
            .collect(Collectors.joining("\n")));

        var metadata = read();

        assertEquals(GraalPyVfsMetadataReader.MAX_ENTRIES, metadata.entryCount());
        assertFalse(metadata.truncated());
        assertEquals(0, metadata.omittedEntries());
    }

    @Test
    void capsOversizedMetadataFiles() throws IOException {
        writeFilesList(APPLICATION_FILES_LIST, "src/dealer.py\n".repeat(90_000));

        var metadata = read();

        assertTrue(metadata.truncated());
        assertTrue(metadata.warnings().stream().anyMatch(warning -> warning.contains("Stopped reading over-sized VFS metadata")));
    }

    @Test
    void fallsBackToDefaultClassLoaderWhenThreadContextClassLoaderIsNull() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            assertNotNull(new GraalPyVfsMetadataReader(null).read());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void skipsNonLocalMetadataResourcesWithoutOpeningThem() throws IOException {
        AtomicBoolean opened = new AtomicBoolean();
        URL nonLocalResource = new URL(null, "https://example.invalid/" + APPLICATION_FILES_LIST, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                opened.set(true);
                throw new AssertionError("Non-local VFS metadata URL must not be opened");
            }
        });
        ClassLoader classLoader = new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                return APPLICATION_FILES_LIST.equals(name)
                    ? Collections.enumeration(Collections.singleton(nonLocalResource))
                    : Collections.emptyEnumeration();
            }
        };

        var metadata = new GraalPyVfsMetadataReader(null).read(classLoader);

        assertTrue(metadata.hasResources());
        assertEquals(1, metadata.resourceCount());
        assertFalse(metadata.hasEntries());
        assertTrue(metadata.resources().get(0).unreadable());
        assertTrue(metadata.warnings().stream().anyMatch(warning -> warning.contains("Skipped non-local GraalPy VFS metadata")));
        assertFalse(opened.get());
    }

    @Test
    void skipsOversizedSingleLineMetadataWithoutDisplayingIt() throws IOException {
        writeFilesList(APPLICATION_FILES_LIST, "src/" + "a".repeat(5000) + ".py");

        var metadata = read();

        assertEquals(1, metadata.resourceCount());
        assertFalse(metadata.hasEntries());
        assertTrue(metadata.warnings().stream().anyMatch(warning -> warning.contains("Skipped an over-sized VFS metadata line")));
    }

    private GraalPyVfsMetadataReader.GraalPyVfsMetadata read() {
        return readWith(new GraalPyVfsMetadataReader(null));
    }

    private GraalPyVfsMetadataReader.GraalPyVfsMetadata readWith(GraalPyVfsMetadataReader reader) {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { tempDir.toUri().toURL() }, null)) {
            return reader.read(classLoader);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void writeFilesList(String resourcePath, String content) throws IOException {
        writeFilesList(tempDir, resourcePath, content);
    }

    private void writeFilesList(Path root, String resourcePath, String content) throws IOException {
        Path filesList = root.resolve(resourcePath);
        Files.createDirectories(filesList.getParent());
        Files.writeString(filesList, content);
    }
}
