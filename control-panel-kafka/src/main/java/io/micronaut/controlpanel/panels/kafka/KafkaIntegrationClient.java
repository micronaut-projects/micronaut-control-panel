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
package io.micronaut.controlpanel.panels.kafka;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP gateway for optional Kafka ecosystem integrations.
 */
@Internal
interface KafkaIntegrationClient {

    JsonNode request(String integration, String method, String path, @Nullable Object body) throws Exception;

    static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

@Singleton
@Internal
final class DefaultKafkaIntegrationClient implements KafkaIntegrationClient {

    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_PATCH = "PATCH";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";

    private final KafkaIntegrationConfiguration configuration;
    private final SchemaRegistryIntegrationHttpClient schemaRegistryClient;
    private final KafkaConnectIntegrationHttpClient kafkaConnectClient;
    private final KsqlDbIntegrationHttpClient ksqlDbClient;
    private final @Nullable String schemaRegistryUrl;
    private final @Nullable String kafkaConnectUrl;
    private final @Nullable String kafkaKsqlDbUrl;
    private final JsonMapper jsonMapper;

    DefaultKafkaIntegrationClient(KafkaIntegrationConfiguration configuration,
                                  SchemaRegistryIntegrationHttpClient schemaRegistryClient,
                                  KafkaConnectIntegrationHttpClient kafkaConnectClient,
                                  KsqlDbIntegrationHttpClient ksqlDbClient,
                                  @Nullable @Value("${micronaut.control-panel.panels.kafka.integrations.schema-registry.url:}") String schemaRegistryUrl,
                                  @Nullable @Value("${micronaut.control-panel.panels.kafka.integrations.connect.url:}") String kafkaConnectUrl,
                                  @Nullable @Value("${micronaut.control-panel.panels.kafka.integrations.ksqldb.url:}") String kafkaKsqlDbUrl,
                                  JsonMapper jsonMapper) {
        this.configuration = configuration;
        this.schemaRegistryClient = schemaRegistryClient;
        this.kafkaConnectClient = kafkaConnectClient;
        this.ksqlDbClient = ksqlDbClient;
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.kafkaConnectUrl = kafkaConnectUrl;
        this.kafkaKsqlDbUrl = kafkaKsqlDbUrl;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public JsonNode request(String integration, String method, String path, @Nullable Object body) throws Exception {
        String baseUrl = baseUrl(integration);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(integration + " is not configured");
        }
        KafkaIntegrationHttpClient client = client(integration);
        String clientPath = decodePath(normalizePath(path));
        try {
            String response = switch (method) {
                case METHOD_GET -> client.get(clientPath);
                case METHOD_POST -> client.post(clientPath, body);
                case METHOD_PUT -> client.put(clientPath, body);
                case METHOD_PATCH -> client.patch(clientPath, body);
                case METHOD_DELETE -> client.delete(clientPath);
                default -> throw new IllegalArgumentException("Unsupported integration HTTP method: " + method);
            };
            return response == null || response.isBlank() ? JsonNode.nullNode() : jsonMapper.readValue(response.getBytes(StandardCharsets.UTF_8), JsonNode.class);
        } catch (HttpClientResponseException e) {
            if (e.getStatus().getCode() == 204) {
                return JsonNode.nullNode();
            }
            throw new IOException(integration + " request failed with HTTP " + e.getStatus().getCode() + ": " + responseText(e), e);
        }
    }

    @Nullable
    private String baseUrl(String integration) {
        return switch (integration) {
            case KafkaClusterService.INTEGRATION_SCHEMA_REGISTRY -> schemaRegistryUrl();
            case KafkaClusterService.INTEGRATION_CONNECT -> configuredUrl(configuration.connect().url(), kafkaConnectUrl);
            case KafkaClusterService.INTEGRATION_KSQLDB -> configuredUrl(configuration.ksqldb().url(), kafkaKsqlDbUrl);
            default -> null;
        };
    }

    private KafkaIntegrationHttpClient client(String integration) {
        return switch (integration) {
            case KafkaClusterService.INTEGRATION_SCHEMA_REGISTRY -> schemaRegistryClient;
            case KafkaClusterService.INTEGRATION_CONNECT -> kafkaConnectClient;
            case KafkaClusterService.INTEGRATION_KSQLDB -> ksqlDbClient;
            default -> throw new IllegalArgumentException("Unknown integration: " + integration);
        };
    }

    @Nullable
    private String schemaRegistryUrl() {
        return configuredUrl(schemaRegistryUrl, configuration.schemaRegistry().url());
    }

    @Nullable
    private static String configuredUrl(@Nullable String primaryUrl, @Nullable String fallbackUrl) {
        if (primaryUrl != null && !primaryUrl.isBlank()) {
            return primaryUrl;
        }
        return fallbackUrl == null || fallbackUrl.isBlank() ? null : fallbackUrl;
    }

    private static String normalizePath(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String decodePath(String path) {
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private static String responseText(HttpClientResponseException e) {
        return e.getResponse().getBody(String.class).orElse("");
    }
}

@Internal
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface KafkaIntegrationHttpClient {

    @Get("/{+path}")
    @Nullable
    String get(@PathVariable String path);

    @Post("/{+path}")
    @Nullable
    String post(@PathVariable String path, @Nullable @Body Object body);

    @Put("/{+path}")
    @Nullable
    String put(@PathVariable String path, @Nullable @Body Object body);

    @Patch("/{+path}")
    @Nullable
    String patch(@PathVariable String path, @Nullable @Body Object body);

    @Delete("/{+path}")
    @Nullable
    String delete(@PathVariable String path);
}

@Internal
@Client("${micronaut.control-panel.panels.kafka.integrations.schema-registry.url:`http://localhost`}")
interface SchemaRegistryIntegrationHttpClient extends KafkaIntegrationHttpClient {
}

@Internal
@Client("${micronaut.control-panel.panels.kafka.integrations.connect.url:`http://localhost`}")
interface KafkaConnectIntegrationHttpClient extends KafkaIntegrationHttpClient {
}

@Internal
@Client("${micronaut.control-panel.panels.kafka.integrations.ksqldb.url:`http://localhost`}")
interface KsqlDbIntegrationHttpClient extends KafkaIntegrationHttpClient {
}
