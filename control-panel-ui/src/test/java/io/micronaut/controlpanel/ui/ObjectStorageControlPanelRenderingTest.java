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
package io.micronaut.controlpanel.ui;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStorageControlPanelRenderingTest {

    @Test
    void pagesRenderAnErrorWhenTheObjectsCannotBeListed(@TempDir Path tempDir) {
        Path missingPath = tempDir.resolve("missing");
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ObjectStorageControlPanelRenderingTest",
            "micronaut.security.enabled", false,
            "micronaut.object-storage.local.missing.path", missingPath.toString()
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {

            String category = client.toBlocking().retrieve(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH + "/categories/object-storage"));
            assertTrue(category.contains("Unable to list files: Error listing objects: NoSuchFileException: " + missingPath), category);

            String detail = client.toBlocking().retrieve(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH + "/object-storage-missing"));
            assertTrue(detail.contains("<strong>Unable to list files</strong>"), detail);
            assertTrue(detail.contains("<p>Error listing objects: NoSuchFileException: " + missingPath + "</p>"), detail);
            assertFalse(detail.contains("No files stored."), detail);
            assertFalse(Files.exists(missingPath));
        }
    }

    @Test
    void pagesRenderTheFilesWhenTheObjectsAreListed(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "hello");
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ObjectStorageControlPanelRenderingTest",
            "micronaut.security.enabled", false,
            "micronaut.object-storage.local.present.path", tempDir.toString()
        ));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {

            String category = client.toBlocking().retrieve(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH + "/categories/object-storage"));
            assertTrue(category.contains("1 files stored."), category);

            String detail = client.toBlocking().retrieve(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH + "/object-storage-present"));
            assertTrue(detail.contains("hello.txt"), detail);
            assertFalse(detail.contains("Unable to list files"), detail);
        }
    }
}
