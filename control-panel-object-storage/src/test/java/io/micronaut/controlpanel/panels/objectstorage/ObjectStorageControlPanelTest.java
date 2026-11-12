/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.objectstorage;

import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.aws.AwsS3Configuration;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.local.LocalStorageConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ObjectStorageControlPanelTest {

    private ObjectStorageControlPanel createPanel(ObjectStorageOperations operations,
                                                  AbstractObjectStorageConfiguration configuration,
                                                  ControlPanelConfiguration controlPanelConfig) {
        return new ObjectStorageControlPanel(operations, configuration, controlPanelConfig);
    }

    private ObjectStorageEntry createMockEntry(String key) {
        ObjectStorageEntry entry = Mockito.mock(ObjectStorageEntry.class);
        Mockito.when(entry.getKey()).thenReturn(key);
        return entry;
    }

    @Test
    void itHasCorrectName() {
        AbstractObjectStorageConfiguration configuration = Mockito.mock(AbstractObjectStorageConfiguration.class);
        Mockito.when(configuration.getName()).thenReturn("test-storage");
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), configuration, Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("object-storage-test-storage", panel.getName());
    }

    @Test
    void itHasCorrectTitle() {
        AbstractObjectStorageConfiguration configuration = Mockito.mock(AbstractObjectStorageConfiguration.class);
        Mockito.when(configuration.getName()).thenReturn("test-storage");
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), configuration, Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("test-storage", panel.getTitle());
    }

    @Test
    void itHasCorrectIcon() {
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertEquals(ObjectStorageControlPanel.DEFAULT_ICON_CLASS, panel.getIcon());
    }

    @Test
    void itHasCorrectBodyView() {
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("/views/object-storage/body", panel.getBodyView().file());
    }

    @Test
    void itHasCorrectDetailedView() {
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("/views/object-storage/detail", panel.getDetailedView().file());
    }

    @Test
    void itHasCorrectCategory() {
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("object-storage", panel.getCategory().id());
        assertEquals("Object Storage", panel.getCategory().name());
        assertEquals(ObjectStorageControlPanel.DEFAULT_ICON_CLASS, panel.getCategory().iconClass());
    }

    @Test
    void getBodyReturnsEntriesFromOperations() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        ObjectStorageEntry entry1 = createMockEntry("entry1");
        ObjectStorageEntry entry2 = createMockEntry("entry2");
        Mockito.doReturn(java.util.Set.of(entry1.getKey(), entry2.getKey())).when(operations).listObjects();
        Mockito.when(operations.retrieve(entry1.getKey())).thenReturn(Optional.of(entry1));
        Mockito.when(operations.retrieve(entry2.getKey())).thenReturn(Optional.of(entry2));

        ObjectStorageControlPanel panel = createPanel(operations, Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        var body = panel.getBody();
        assertEquals(2, body.entries().size());
        assertTrue(body.entries().contains(entry1));
        assertTrue(body.entries().contains(entry2));
    }

    @Test
    void getBodyFiltersOutEntriesThatCannotBeRetrieved() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        ObjectStorageEntry entry1 = createMockEntry("entry1");
        ObjectStorageEntry entry2 = createMockEntry("entry2");
        Mockito.doReturn(java.util.Set.of(entry1.getKey(), entry2.getKey())).when(operations).listObjects();
        Mockito.when(operations.retrieve(entry1.getKey())).thenReturn(Optional.of(entry1));
        Mockito.when(operations.retrieve(entry2.getKey())).thenReturn(Optional.empty());

        ObjectStorageControlPanel panel = createPanel(operations, Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        var body = panel.getBody();
        assertEquals(1, body.entries().size());
        assertTrue(body.entries().contains(entry1));
        assertFalse(body.entries().contains(entry2));
    }

    @Test
    void getBodyHasNoErrorWhenObjectsAreListed() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        Mockito.doReturn(java.util.Set.of()).when(operations).listObjects();

        ObjectStorageControlPanel panel = createPanel(operations, Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertNull(panel.getBody().error());
    }

    @Test
    void getBodyReportsAnErrorWhenListingObjectsFails() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        Mockito.when(operations.listObjects()).thenThrow(
            new ObjectStorageException("Error listing objects", new NoSuchFileException("/tmp/missing-storage"))
        );
        LocalStorageConfiguration localConfig = Mockito.mock(LocalStorageConfiguration.class);
        Mockito.when(localConfig.getName()).thenReturn("my-local");
        Mockito.when(localConfig.getPath()).thenReturn(Path.of("/tmp/missing-storage"));

        ObjectStorageControlPanel panel = createPanel(operations, localConfig, Mockito.mock(ControlPanelConfiguration.class));
        var body = panel.getBody();
        assertTrue(body.entries().isEmpty());
        assertEquals("Error listing objects: NoSuchFileException: /tmp/missing-storage", body.error());
        assertEquals(Path.of("/tmp/missing-storage"), body.metadata().get("path"));
    }

    @Test
    void getBodyReportsAnErrorWhenRetrievingAnObjectFails() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        Mockito.doReturn(java.util.Set.of("entry1")).when(operations).listObjects();
        Mockito.when(operations.retrieve("entry1")).thenThrow(new ObjectStorageException("Access denied"));

        ObjectStorageControlPanel panel = createPanel(operations, Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        var body = panel.getBody();
        assertTrue(body.entries().isEmpty());
        assertEquals("Access denied", body.error());
    }

    @Test
    void describeFailureSkipsCausesWhoseMessageIsAlreadyIncluded() {
        var cause = new NoSuchFileException("/tmp/missing-storage");
        assertEquals("java.nio.file.NoSuchFileException: /tmp/missing-storage", ObjectStorageControlPanel.describeFailure(new RuntimeException(cause)));
        assertEquals("IllegalStateException", ObjectStorageControlPanel.describeFailure(new IllegalStateException()));
    }

    @Test
    void bodyCreatedWithoutAnErrorHasNoError() {
        var body = new ObjectStorageControlPanel.Body(java.util.List.of(), java.util.Map.of());
        assertNull(body.error());
    }

    @Test
    void getBadgeIsEmptyWhenObjectCountIsRenderedInTheBody() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        Mockito.doReturn(java.util.Set.of(Mockito.mock(ObjectStorageEntry.class), Mockito.mock(ObjectStorageEntry.class), Mockito.mock(ObjectStorageEntry.class))).when(operations).listObjects();
        ObjectStorageControlPanel panel = createPanel(operations, Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("", panel.getBadge());
    }

    @Test
    void getBadgeReturnsZeroWhenNoObjects() {
        ObjectStorageOperations operations = Mockito.mock(ObjectStorageOperations.class);
        Mockito.doReturn(java.util.Set.of()).when(operations).listObjects();
        ObjectStorageControlPanel panel = createPanel(operations, Mockito.mock(AbstractObjectStorageConfiguration.class), Mockito.mock(ControlPanelConfiguration.class));
        assertEquals("", panel.getBadge());
    }

    @Test
    void computeMetadataReturnsBucketForAws() {
        AwsS3Configuration awsConfig = Mockito.mock(AwsS3Configuration.class);
        Mockito.when(awsConfig.getBucket()).thenReturn("mybucket");
        ObjectStorageControlPanel panel = new ObjectStorageControlPanel(Mockito.mock(ObjectStorageOperations.class), awsConfig, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals(1, metadata.size());
        assertEquals("mybucket", metadata.get("bucket"));
    }

    @Test
    void computeMetadataIncludesPathForLocalStorageConfiguration() {
        LocalStorageConfiguration localConfig = Mockito.mock(LocalStorageConfiguration.class);
        Mockito.when(localConfig.getPath()).thenReturn(Path.of("/tmp/test-storage"));
        ObjectStorageControlPanel panel = new ObjectStorageControlPanel(Mockito.mock(ObjectStorageOperations.class), localConfig, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals(1, metadata.size());
        assertEquals(Path.of("/tmp/test-storage"), metadata.get("path"));
    }
}
