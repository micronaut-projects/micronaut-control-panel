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
package io.micronaut.controlpanel.panels.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

abstract class AbstractMongoDbDiagnosticService implements MongoDbDiagnosticService {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoDbDiagnosticService.class);

    private static final List<String> SENSITIVE_OPTION_PARTS = List.of(
        "auth",
        "credential",
        "password",
        "secret",
        "token",
        "key",
        "certificate"
    );

    protected final MongoDbControlPanelConfiguration configuration;
    protected final MongoDbSanitizer sanitizer;
    protected final MongoDbSchemaAnalyzer schemaAnalyzer;
    private final String beanName;
    private final String mode;
    private final Collection<AbstractMongoConfiguration> configurations;

    AbstractMongoDbDiagnosticService(String beanName,
                                     String mode,
                                     Collection<AbstractMongoConfiguration> configurations,
                                     MongoDbControlPanelConfiguration configuration) {
        this.beanName = normalizeBeanName(beanName);
        this.mode = mode;
        this.configurations = configurations;
        this.configuration = configuration;
        this.sanitizer = new MongoDbSanitizer();
        this.schemaAnalyzer = new MongoDbSchemaAnalyzer(configuration, sanitizer);
    }

    @Override
    public final String beanName() {
        return beanName;
    }

    @Override
    public final String mode() {
        return mode;
    }

    protected MongoDbModels.ClientSummary clientSummary(String buildVersion, boolean buildOk) {
        var mongoConfiguration = mongoConfiguration();
        Optional<ConnectionString> connectionString = mongoConfiguration.flatMap(AbstractMongoConfiguration::getConnectionString);
        Optional<MongoClientSettings> settings = mongoConfiguration.map(this::buildSettings);
        return new MongoDbModels.ClientSummary(
            beanName,
            mode,
            connectionString.map(ConnectionString::getDatabase).orElse(""),
            connectionString.map(ConnectionString::getHosts).orElseGet(List::of),
            connectionString.map(this::connectionOptions).orElseGet(Map::of),
            settings.map(MongoClientSettings::getReadPreference).map(Object::toString).orElse(""),
            settings.map(MongoClientSettings::getReadConcern).map(Object::toString).orElse(""),
            settings.map(MongoClientSettings::getWriteConcern).map(Object::toString).orElse(""),
            settings.map(MongoClientSettings::getUuidRepresentation).map(Object::toString).orElse(""),
            settings.map(MongoClientSettings::getApplicationName).orElse(""),
            settings.map(MongoClientSettings::getSslSettings).map(ssl -> ssl.isEnabled()).orElse(false),
            mongoConfiguration.map(c -> c.getCodecs().size()).orElse(0),
            mongoConfiguration.map(c -> c.getCodecRegistries().size()).orElse(0),
            settings.map(s -> s.getCommandListeners().size()).orElse(0),
            settings.map(s -> s.getConnectionPoolSettings().getConnectionPoolListeners().size()).orElse(0),
            mongoConfiguration.map(c -> c.getPackageNames().size()).orElse(0),
            settings.map(s -> poolSummary(s.getConnectionPoolSettings().getMinSize(), s.getConnectionPoolSettings().getMaxSize())).orElse(""),
            buildVersion,
            buildOk
        );
    }

    private Optional<AbstractMongoConfiguration> mongoConfiguration() {
        return configurations.stream()
            .filter(configuration -> isConfigurationForBean(configuration, beanName))
            .findFirst();
    }

    private static boolean isConfigurationForBean(AbstractMongoConfiguration configuration, String beanName) {
        if (configuration instanceof NamedMongoConfiguration namedMongoConfiguration) {
            return namedMongoConfiguration.getServerName().equals(beanName);
        }
        return "default".equals(beanName) || "primary".equals(beanName);
    }

    private MongoClientSettings buildSettings(AbstractMongoConfiguration configuration) {
        try {
            return configuration.buildSettings();
        } catch (RuntimeException e) {
            LOG.debug("Cannot build MongoDB settings for bean '{}': {}", beanName, e.getMessage());
            return MongoClientSettings.builder().build();
        }
    }

    private Map<String, String> connectionOptions(ConnectionString connectionString) {
        Map<String, String> options = new LinkedHashMap<>();
        put(options, "applicationName", connectionString.getApplicationName());
        put(options, "readPreference", connectionString.getReadPreference());
        put(options, "readConcern", connectionString.getReadConcern());
        put(options, "writeConcern", connectionString.getWriteConcern());
        put(options, "retryReads", connectionString.getRetryReads());
        put(options, "retryWrites", connectionString.getRetryWritesValue());
        put(options, "directConnection", connectionString.isDirectConnection());
        put(options, "loadBalanced", connectionString.isLoadBalanced());
        put(options, "ssl", connectionString.getSslEnabled());
        put(options, "requiredReplicaSetName", connectionString.getRequiredReplicaSetName());
        put(options, "serverSelectionTimeout", connectionString.getServerSelectionTimeout());
        put(options, "connectTimeout", connectionString.getConnectTimeout());
        put(options, "socketTimeout", connectionString.getSocketTimeout());
        return options.entrySet()
            .stream()
            .filter(entry -> !sensitive(entry.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static void put(Map<String, String> options, String key, Object value) {
        if (value != null) {
            options.put(key, String.valueOf(value));
        }
    }

    private static boolean sensitive(String key) {
        var lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_OPTION_PARTS.stream().anyMatch(lower::contains);
    }

    private static String poolSummary(int minSize, int maxSize) {
        return "min " + minSize + ", max " + maxSize;
    }

    protected static String buildVersion(Document buildInfo) {
        Object version = buildInfo.get("version");
        return version == null ? "" : String.valueOf(version);
    }

    protected List<MongoDbModels.IndexInfo> indexes(List<Document> documents) {
        return documents.stream()
            .map(this::indexInfo)
            .sorted(Comparator.comparing(MongoDbModels.IndexInfo::name))
            .toList();
    }

    protected MongoDbModels.IndexInfo indexInfo(Document index) {
        return new MongoDbModels.IndexInfo(
            String.valueOf(index.getOrDefault("name", "")),
            sanitizer.toJson(index.get("key")),
            Boolean.TRUE.equals(index.getBoolean("unique", false)),
            Boolean.TRUE.equals(index.getBoolean("sparse", false)),
            value(index.get("expireAfterSeconds")),
            sanitizer.toJson(index.get("partialFilterExpression")),
            sanitizer.toJson(index.get("collation")),
            sanitizer.toJson(redactedIndexOptions(index))
        );
    }

    protected Document redactedCollectionOptions(Document collectionInfo) {
        var options = collectionInfo.get("options");
        if (options instanceof Document document) {
            return sanitizer.redactDocument(document);
        }
        return new Document();
    }

    protected String validationJson(Document collectionInfo) {
        Object options = collectionInfo.get("options");
        if (options instanceof Document document) {
            var validator = document.get("validator");
            if (validator != null) {
                return sanitizer.toJson(validator);
            }
            var validation = new Document();
            copy(document, validation, "validationLevel");
            copy(document, validation, "validationAction");
            if (!validation.isEmpty()) {
                return sanitizer.toJson(validation);
            }
        }
        return "";
    }

    protected List<MongoDbModels.DiagnosticError> error(String scope, RuntimeException e) {
        return List.of(new MongoDbModels.DiagnosticError(scope, safeMessage(e)));
    }

    protected String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return sanitizer.redactText(message);
    }

    protected long timeoutMillis() {
        Duration timeout = configuration.getTimeout();
        return Math.max(1, timeout.toMillis());
    }

    protected int positiveLimit(int limit) {
        return Math.max(1, limit);
    }

    protected int nonNegativeLimit(int limit) {
        return Math.max(0, limit);
    }

    protected static String normalizeBeanName(String beanName) {
        return "primary".equals(beanName) ? "default" : beanName;
    }

    protected static String databaseName(Document databaseInfo) {
        Object name = databaseInfo.get("name");
        return name == null ? "" : String.valueOf(name);
    }

    protected static String collectionType(Document collectionInfo) {
        Object type = collectionInfo.get("type");
        return type == null ? "collection" : String.valueOf(type);
    }

    protected static String collectionName(Document collectionInfo) {
        Object name = collectionInfo.get("name");
        return name == null ? "" : String.valueOf(name);
    }

    protected static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    protected static List<Document> limitDocuments(Iterable<Document> documents, int limit) {
        List<Document> result = new ArrayList<>();
        int count = 0;
        for (Document document : documents) {
            result.add(document);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return result;
    }

    protected static String bsonType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ObjectId) {
            return "objectId";
        }
        if (value instanceof Document) {
            return "document";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        return value.getClass().getSimpleName();
    }

    private Document redactedIndexOptions(Document index) {
        var options = new Document(index);
        options.remove("name");
        options.remove("key");
        return sanitizer.redactDocument(options);
    }

    private static void copy(Document source, Document target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    protected long timeout(TimeUnit timeUnit) {
        return timeUnit.convert(timeoutMillis(), TimeUnit.MILLISECONDS);
    }

    protected static final class SchemaSamplingBudget {
        private final boolean enabled;
        private int remaining;

        SchemaSamplingBudget(boolean enabled, int maxCollections) {
            this.enabled = enabled;
            this.remaining = maxCollections;
        }

        boolean tryAcquire() {
            if (!enabled || remaining <= 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }
}
