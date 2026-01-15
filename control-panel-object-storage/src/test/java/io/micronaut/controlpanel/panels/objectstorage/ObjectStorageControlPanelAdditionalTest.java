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
import io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration;
import io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration;
import io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ObjectStorageControlPanelAdditionalTest {

    @Test
    void computeMetadataIncludesContainerAndEndpointForAzure() {
        AzureBlobStorageConfiguration cfg = Mockito.mock(AzureBlobStorageConfiguration.class);
        Mockito.when(cfg.getContainer()).thenReturn("c1");
        Mockito.when(cfg.getEndpoint()).thenReturn("https://example.blob.core.windows.net");
        var panel = new ObjectStorageControlPanel(Mockito.mock(io.micronaut.objectstorage.ObjectStorageOperations.class), cfg, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals("c1", metadata.get("container"));
        assertEquals("https://example.blob.core.windows.net", metadata.get("endpoint"));
    }

    @Test
    void computeMetadataIncludesBucketForGoogleCloud() {
        GoogleCloudStorageConfiguration cfg = Mockito.mock(GoogleCloudStorageConfiguration.class);
        Mockito.when(cfg.getBucket()).thenReturn("gcs-bucket");
        var panel = new ObjectStorageControlPanel(Mockito.mock(io.micronaut.objectstorage.ObjectStorageOperations.class), cfg, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals("gcs-bucket", metadata.get("bucket"));
    }

    @Test
    void computeMetadataIncludesBucketAndNamespaceForOracleCloud() {
        OracleCloudStorageConfiguration cfg = Mockito.mock(OracleCloudStorageConfiguration.class);
        Mockito.when(cfg.getBucket()).thenReturn("oci-bucket");
        Mockito.when(cfg.getNamespace()).thenReturn("ns1");
        var panel = new ObjectStorageControlPanel(Mockito.mock(io.micronaut.objectstorage.ObjectStorageOperations.class), cfg, Mockito.mock(ControlPanelConfiguration.class));
        var metadata = panel.computeMetadata();
        assertEquals("oci-bucket", metadata.get("bucket"));
        assertEquals("ns1", metadata.get("namespace"));
    }
}
