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
package io.micronaut.controlpanel.panels.neo4j;

import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jConnectionInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves safe, masked connection information from Micronaut Neo4j configuration.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
public class Neo4jConnectionSummaryResolver {

    private static final String DEFAULT_BEAN_NAME = "default";
    private static final String CUSTOM_DRIVER = "custom Driver bean";
    private static final Set<String> SECRET_TERMS = Set.of("password", "passwd", "pwd", "token", "secret", "credential", "credentials", "key");
    private static final Pattern QUERY_PAIR_SEPARATOR = Pattern.compile("&");

    private final String beanName;
    private final Environment environment;

    /**
     * Constructor.
     *
     * @param beanName the Neo4j driver bean name
     * @param environment the Micronaut environment
     */
    public Neo4jConnectionSummaryResolver(String beanName, Environment environment) {
        this.beanName = beanName;
        this.environment = environment;
    }

    /**
     * @return safe connection information for display
     */
    public Neo4jConnectionInfo resolve() {
        String uri = property("uri").orElse("");
        String username = property("username").orElse("");
        String encrypted = property("encrypted")
            .or(() -> property("encryption"))
            .orElse("");
        String trustStrategy = property("trust-strategy")
            .or(() -> property("trustStrategy"))
            .orElse("");
        if (uri.isBlank() && username.isBlank() && encrypted.isBlank() && trustStrategy.isBlank()) {
            return new Neo4jConnectionInfo(beanName, CUSTOM_DRIVER, "", "", "");
        }
        return new Neo4jConnectionInfo(beanName, maskUri(uri), username, encrypted, maskSecretValue(trustStrategy));
    }

    private Optional<String> property(String name) {
        if (DEFAULT_BEAN_NAME.equals(beanName)) {
            return environment.getProperty("neo4j." + name, String.class);
        }
        return environment.getProperty("neo4j." + beanName + "." + name, String.class)
            .or(() -> environment.getProperty("neo4j." + name, String.class));
    }

    static String maskUri(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(value);
            String rawQuery = uri.getRawQuery();
            String sanitizedQuery = sanitizeQuery(rawQuery);
            URI sanitized = new URI(uri.getScheme(), userInfo(uri), uri.getHost(), uri.getPort(), uri.getRawPath(), sanitizedQuery, uri.getRawFragment());
            return sanitized.toString();
        } catch (URISyntaxException e) {
            return maskUserInfoFallback(maskSecretValue(value));
        }
    }

    private static String userInfo(URI uri) {
        return uri.getRawUserInfo() == null ? null : "***";
    }

    private static String sanitizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return rawQuery;
        }
        String[] pairs = QUERY_PAIR_SEPARATOR.split(rawQuery);
        for (int i = 0; i < pairs.length; i++) {
            int equals = pairs[i].indexOf('=');
            if (equals > -1 && isSecretName(pairs[i].substring(0, equals))) {
                pairs[i] = pairs[i].substring(0, equals + 1) + "***";
            }
        }
        return String.join("&", pairs);
    }

    static String maskSecretValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value;
        for (String term : SECRET_TERMS) {
            sanitized = sanitized.replaceAll("(?i)(" + Pattern.quote(term) + "\\s*[=:]\\s*)[^\\s,;&]+", "$1***");
        }
        return sanitized;
    }

    static boolean isSecretName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return SECRET_TERMS.stream().anyMatch(lower::contains);
    }

    private static String maskUserInfoFallback(String value) {
        int scheme = value.indexOf("://");
        int userInfoStart = scheme > -1 ? scheme + 3 : 0;
        int at = value.indexOf('@', userInfoStart);
        if (at > userInfoStart) {
            return value.substring(0, userInfoStart) + "***@" + value.substring(at + 1);
        }
        return value;
    }
}
