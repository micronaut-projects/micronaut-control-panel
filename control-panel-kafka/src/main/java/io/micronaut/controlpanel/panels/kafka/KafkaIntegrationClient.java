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

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaIntegrationConfiguration configuration;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    DefaultKafkaIntegrationClient(KafkaIntegrationConfiguration configuration, JsonMapper jsonMapper) {
        this.configuration = configuration;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    @Override
    public JsonNode request(String integration, String method, String path, @Nullable Object body) throws Exception {
        String baseUrl = baseUrl(integration);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(integration + " is not configured");
        }
        URI uri = URI.create(trimTrailingSlash(baseUrl) + normalizePath(path));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        }
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException(integration + " request failed with HTTP " + status + ": " + responseText(response.body()));
        }
        byte[] bytes = response.body();
        if (bytes.length == 0) {
            return JsonNode.nullNode();
        }
        return jsonMapper.readValue(bytes, JsonNode.class);
    }

    @Nullable
    private String baseUrl(String integration) {
        return switch (integration) {
            case KafkaClusterService.INTEGRATION_SCHEMA_REGISTRY -> configuration.getSchemaRegistry().getUrl();
            case KafkaClusterService.INTEGRATION_CONNECT -> configuration.getConnect().getUrl();
            case KafkaClusterService.INTEGRATION_KSQLDB -> configuration.getKsqldb().getUrl();
            default -> null;
        };
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String responseText(byte[] body) {
        if (body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}
