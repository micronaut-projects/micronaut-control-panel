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
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.aws.AwsS3Configuration;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.local.LocalStorageConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    void getIconReturnsDefaultIconForAwsStorageConfiguration() {
        AwsS3Configuration awsConfig = new AwsS3Configuration("test-storage");
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), awsConfig, Mockito.mock(ControlPanelConfiguration.class));
        assertEquals(ObjectStorageControlPanel.DEFAULT_ICON_CLASS, panel.getIcon());
    }

    @Test
    void getIconReturnsDefaultIconForLocalStorageConfiguration() {
        LocalStorageConfiguration localConfig = new LocalStorageConfiguration("test-storage");
        ObjectStorageControlPanel panel = createPanel(Mockito.mock(ObjectStorageOperations.class), localConfig, Mockito.mock(ControlPanelConfiguration.class));
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
        assertTrue(body.entries().stream().anyMatch(entry -> entry.key().equals(entry1.getKey())));
        assertTrue(body.entries().stream().anyMatch(entry -> entry.key().equals(entry2.getKey())));
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
        assertTrue(body.entries().stream().anyMatch(entry -> entry.key().equals(entry1.getKey())));
        assertFalse(body.entries().stream().anyMatch(entry -> entry.key().equals(entry2.getKey())));
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
        AwsS3Configuration awsConfig = new AwsS3Configuration("test-storage");
        awsConfig.setBucket("mybucket");
        ObjectStorageControlPanel panel = new ObjectStorageControlPanel(Mockito.mock(ObjectStorageOperations.class), awsConfig, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals(1, metadata.size());
        assertEquals("mybucket", metadata.get("bucket"));
    }

    @Test
    void computeMetadataIncludesPathForLocalStorageConfiguration() {
        LocalStorageConfiguration localConfig = new LocalStorageConfiguration("test-storage");
        localConfig.setPath(Path.of("/tmp/test-storage"));
        ObjectStorageControlPanel panel = new ObjectStorageControlPanel(Mockito.mock(ObjectStorageOperations.class), localConfig, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals(1, metadata.size());
        assertEquals(Path.of("/tmp/test-storage"), metadata.get("path"));
    }
}
