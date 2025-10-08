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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.aws.AwsS3Configuration;
import io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration;
import io.micronaut.objectstorage.local.LocalStorageConfiguration;
import io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration;
import jakarta.inject.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A control panel for managing object storage.
 *
 * This control panel provides a user interface for viewing and managing object storage entries.
 * It is configured using an {@link AbstractObjectStorageConfiguration} instance.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.10.0
 */
@EachBean(AbstractObjectStorageConfiguration.class)
public class ObjectStorageControlPanel extends AbstractControlPanel<ObjectStorageControlPanel.Body> {

    public static final String NAME = "object-storage";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final String DEFAULT_ICON_CLASS = "fa-cloud-arrow-down";

    private final ObjectStorageOperations<?, ?, ?> operations;
    private final AbstractObjectStorageConfiguration objectStorageConfiguration;

    public ObjectStorageControlPanel(@Parameter ObjectStorageOperations<?, ?, ?> operations,
                                     @Parameter AbstractObjectStorageConfiguration objectStorageConfiguration,
                                     @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.operations = operations;
        this.objectStorageConfiguration = objectStorageConfiguration;
    }

    @Override
    public Body getBody() {
        var entries = operations.listObjects()
            .stream()
            .map(operations::retrieve)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        var metadata = computeMetadata();

        return new Body(entries, metadata);
    }

    @Override
    public String getTitle() {
        return objectStorageConfiguration.getName();
    }

    @Override
    public String getName() {
        return NAME + "-" + objectStorageConfiguration.getName();
    }

    @Override
    public View getBodyView() {
        return new View("/views/" + NAME + "/body");
    }

    @Override
    public View getDetailedView() {
        return new View("/views/" + NAME + "/detail");
    }

    @Override
    public String getBadge() {
        return String.valueOf(operations.listObjects().size());
    }

    @Override
    public String getIcon() {
        if (objectStorageConfiguration instanceof LocalStorageConfiguration) {
            return "fa-hard-drive";
        } else if (objectStorageConfiguration instanceof AwsS3Configuration) {
            return "fa-brands fa-aws";
        } else if (objectStorageConfiguration instanceof AzureBlobStorageConfiguration) {
            return "fa-brands fa-microsoft";
        } else if (objectStorageConfiguration instanceof GoogleCloudStorageConfiguration) {
            return "fa-brands fa-google";
        }
        return DEFAULT_ICON_CLASS;
    }

    /**
     * Computes the metadata for this control panel, based on the object storage configuration.
     *
     * @return the metadata for this control panel
     */
    Map<String, Object> computeMetadata() {
        var metadata = new HashMap<String, Object>();
        if (objectStorageConfiguration instanceof LocalStorageConfiguration localConfiguration) {
            metadata.put("path", localConfiguration.getPath());
        } else if (objectStorageConfiguration instanceof AwsS3Configuration awsS3Configuration) {
            metadata.put("bucket", awsS3Configuration.getBucket());
        } else if (objectStorageConfiguration instanceof AzureBlobStorageConfiguration azureBlobConfiguration) {
            metadata.put("container", azureBlobConfiguration.getContainer());
            metadata.put("endpoint", azureBlobConfiguration.getEndpoint());
        } else if (objectStorageConfiguration instanceof GoogleCloudStorageConfiguration googleCloudConfiguration) {
            metadata.put("bucket", googleCloudConfiguration.getBucket());
        } else if (objectStorageConfiguration instanceof OracleCloudStorageConfiguration oracleCloudConfiguration) {
            metadata.put("bucket", oracleCloudConfiguration.getBucket());
            metadata.put("namespace", oracleCloudConfiguration.getNamespace());
        }
        return metadata;
    }

    @Override
    public Category getCategory() {
        return new Category("object-storage", "Object Storage", DEFAULT_ICON_CLASS);
    }

    /**
     * A record representing the body of this control panel, containing a list of object storage entries and metadata.
     *
     * @param entries the list of object storage entries
     * @param metadata the metadata for this control panel
     */
    public record Body(List<? extends ObjectStorageEntry<?>> entries, Map<String, Object> metadata) { }
}
