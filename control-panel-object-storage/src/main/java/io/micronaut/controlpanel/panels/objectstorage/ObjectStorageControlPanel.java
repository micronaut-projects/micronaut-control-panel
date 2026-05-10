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
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import jakarta.inject.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

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
    public static final String DEFAULT_ICON_CLASS = "fas fa-cloud-arrow-down";
    public static final String BUCKET = "bucket";
    private static final String AWS_PACKAGE = ".aws";
    private static final String AWS_S3_CONFIGURATION = "AwsS3Configuration";
    private static final String AZURE_PACKAGE = ".azure";
    private static final String AZURE_BLOB_STORAGE_CONFIGURATION = "AzureBlobStorageConfiguration";
    private static final String GOOGLE_CLOUD_PACKAGE = ".googlecloud";
    private static final String GOOGLE_CLOUD_STORAGE_CONFIGURATION = "GoogleCloudStorageConfiguration";
    private static final String LOCAL_PACKAGE = ".local";
    private static final String LOCAL_STORAGE_CONFIGURATION = "LocalStorageConfiguration";
    private static final String ORACLE_CLOUD_PACKAGE = ".oraclecloud";
    private static final String ORACLE_CLOUD_STORAGE_CONFIGURATION = "OracleCloudStorageConfiguration";
    private static final String CONTAINER = "container";
    private static final String ENDPOINT = "endpoint";
    private static final String NAMESPACE = "namespace";
    private static final String PATH = "path";

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
            .map(ObjectStorageControlPanel::entry)
            .toList();
        var metadata = computeMetadata();

        return new Body(entries, metadata);
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getIcon() {
        var configurationType = configurationType();
        if (isConfiguration(configurationType, LOCAL_PACKAGE, LOCAL_STORAGE_CONFIGURATION)) {
            return "fa-hard-drive";
        }
        if (isConfiguration(configurationType, AWS_PACKAGE, AWS_S3_CONFIGURATION)) {
            return "fa-brands fa-aws";
        }
        if (isConfiguration(configurationType, AZURE_PACKAGE, AZURE_BLOB_STORAGE_CONFIGURATION)) {
            return "fa-brands fa-microsoft";
        }
        if (isConfiguration(configurationType, GOOGLE_CLOUD_PACKAGE, GOOGLE_CLOUD_STORAGE_CONFIGURATION)) {
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
        var configurationType = configurationType();
        if (isConfiguration(configurationType, LOCAL_PACKAGE, LOCAL_STORAGE_CONFIGURATION)) {
            putMetadata(metadata, PATH);
        } else if (isConfiguration(configurationType, AWS_PACKAGE, AWS_S3_CONFIGURATION)) {
            putMetadata(metadata, BUCKET);
        } else if (isConfiguration(configurationType, AZURE_PACKAGE, AZURE_BLOB_STORAGE_CONFIGURATION)) {
            putMetadata(metadata, CONTAINER);
            putMetadata(metadata, ENDPOINT);
        } else if (isConfiguration(configurationType, GOOGLE_CLOUD_PACKAGE, GOOGLE_CLOUD_STORAGE_CONFIGURATION)) {
            putMetadata(metadata, BUCKET);
        } else if (isConfiguration(configurationType, ORACLE_CLOUD_PACKAGE, ORACLE_CLOUD_STORAGE_CONFIGURATION)) {
            putMetadata(metadata, BUCKET);
            putMetadata(metadata, NAMESPACE);
        }
        return metadata;
    }

    private void putMetadata(Map<String, Object> metadata, String propertyName) {
        try {
            BeanWrapper.findWrapper(objectStorageConfiguration)
                .flatMap(wrapper -> wrapper.getIntrospection().getReadProperty(propertyName))
                .map(property -> property.get(objectStorageConfiguration))
                .or(() -> readProperty(propertyName))
                .ifPresent(value -> metadata.put(propertyName, value));
        } catch (LinkageError e) {
            readProperty(propertyName).ifPresent(value -> metadata.put(propertyName, value));
        }
    }

    private Optional<Object> readProperty(String propertyName) {
        try {
            var method = objectStorageConfiguration.getClass().getMethod(getterName(propertyName));
            return Optional.ofNullable(method.invoke(objectStorageConfiguration));
        } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static String getterName(String propertyName) {
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private static Entry entry(ObjectStorageEntry<?> entry) {
        return new Entry(entry.getKey(), entry.getContentType());
    }

    private Class<?> configurationType() {
        try {
            return objectStorageConfiguration.getClass();
        } catch (LinkageError e) {
            return null;
        }
    }

    private static boolean isConfiguration(Class<?> type, String packageSuffix, String simpleName) {
        var currentType = type;
        while (currentType != null) {
            try {
                var packageName = currentType.getPackageName();
                if (simpleName.equals(currentType.getSimpleName()) && packageName.endsWith(packageSuffix)) {
                    return true;
                }
                currentType = currentType.getSuperclass();
            } catch (LinkageError e) {
                return false;
            }
        }
        return false;
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
    @TypeHint(value = { Entry.class }, accessType = TypeHint.AccessType.ALL_PUBLIC)
    public record Body(List<Entry> entries, Map<String, Object> metadata) { }

    /**
     * Render-safe object storage entry metadata.
     *
     * @param key the object key
     * @param contentType the optional content type
     */
    @ReflectiveAccess
    public record Entry(String key, Optional<String> contentType) { }
}
