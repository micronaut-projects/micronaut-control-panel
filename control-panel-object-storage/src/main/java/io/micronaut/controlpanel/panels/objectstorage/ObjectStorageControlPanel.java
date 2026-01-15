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
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.aws.AwsS3Configuration;
import io.micronaut.objectstorage.aws.AwsS3ObjectStorageEntry;
import io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration;
import io.micronaut.objectstorage.azure.AzureBlobStorageEntry;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.googlecloud.GoogleCloudStorageConfiguration;
import io.micronaut.objectstorage.googlecloud.GoogleCloudStorageEntry;
import io.micronaut.objectstorage.local.LocalStorageConfiguration;
import io.micronaut.objectstorage.local.LocalStorageEntry;
import io.micronaut.objectstorage.oraclecloud.OracleCloudStorageConfiguration;
import io.micronaut.objectstorage.oraclecloud.OracleCloudStorageEntry;
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
public class ObjectStorageControlPanel extends AbstractEachBeanControlPanel<ObjectStorageControlPanel.Body> {

    public static final String NAME = "object-storage";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final String DEFAULT_ICON_CLASS = "fa-cloud-arrow-down";
    public static final String BUCKET = "bucket";

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
    protected String getBeanName() {
        return objectStorageConfiguration.getName();
    }

    @Override
    protected String getPanelName() {
        return NAME;
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
    public String getBadge() {
        return String.valueOf(operations.listObjects().size());
    }

    @Override
    public String getIcon() {
        return switch (objectStorageConfiguration) {
            case LocalStorageConfiguration ignored -> "fa-hard-drive";
            case AwsS3Configuration ignored -> "fa-brands fa-aws";
            case AzureBlobStorageConfiguration ignored -> "fa-brands fa-microsoft";
            case GoogleCloudStorageConfiguration ignored -> "fa-brands fa-google";
            default -> DEFAULT_ICON_CLASS;
        };
    }

    /**
     * Computes the metadata for this control panel, based on the object storage configuration.
     *
     * @return the metadata for this control panel
     */
    Map<String, Object> computeMetadata() {
        var metadata = new HashMap<String, Object>();
        switch (objectStorageConfiguration) {
            case LocalStorageConfiguration localConfiguration -> metadata.put("path", localConfiguration.getPath());
            case AwsS3Configuration awsS3Configuration -> metadata.put(BUCKET, awsS3Configuration.getBucket());
            case AzureBlobStorageConfiguration azureBlobConfiguration -> {
                metadata.put("container", azureBlobConfiguration.getContainer());
                metadata.put("endpoint", azureBlobConfiguration.getEndpoint());
            }
            case GoogleCloudStorageConfiguration googleCloudConfiguration -> metadata.put(BUCKET, googleCloudConfiguration.getBucket());
            case OracleCloudStorageConfiguration oracleCloudConfiguration -> {
                metadata.put(BUCKET, oracleCloudConfiguration.getBucket());
                metadata.put("namespace", oracleCloudConfiguration.getNamespace());
            }
            default -> { /* no-op */ }
        }
        return metadata;
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Object Storage", DEFAULT_ICON_CLASS);
    }

    /**
     * A record representing the body of this control panel, containing a list of object storage entries and metadata.
     *
     * @param entries the list of object storage entries
     * @param metadata the metadata for this control panel
     */
    @ReflectiveAccess
    @TypeHint(
        value = {
            LocalStorageEntry.class,
            AwsS3ObjectStorageEntry.class,
            AzureBlobStorageEntry.class,
            GoogleCloudStorageEntry.class,
            OracleCloudStorageEntry.class
        },
        accessType = TypeHint.AccessType.ALL_PUBLIC
    )
    public record Body(List<? extends ObjectStorageEntry<?>> entries, Map<String, Object> metadata) { }
}
