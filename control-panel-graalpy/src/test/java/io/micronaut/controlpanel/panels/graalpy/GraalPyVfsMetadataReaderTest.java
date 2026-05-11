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
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void capsLargeFilesListMetadata() throws IOException {
        writeFilesList(IntStream.range(0, GraalPyVfsMetadataReader.MAX_ENTRIES + 2)
            .mapToObj(index -> "src/file" + index + ".py")
            .collect(Collectors.joining("\n")));

        var metadata = read();

        assertEquals(GraalPyVfsMetadataReader.MAX_ENTRIES, metadata.entryCount());
        assertTrue(metadata.truncated());
        assertEquals(2, metadata.omittedEntries());
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
