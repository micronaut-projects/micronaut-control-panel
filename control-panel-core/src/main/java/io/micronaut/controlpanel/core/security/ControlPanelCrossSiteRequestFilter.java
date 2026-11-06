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
package io.micronaut.controlpanel.core.security;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelEnabledCondition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.util.HttpHostResolver;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects cross-site mutating requests to the control panel HTTP surface.
 *
 * <p>The filter only matches the control panel path and the helper routes nested under it,
 * including any server context path. Every such request that is not a {@code GET}, {@code HEAD} or
 * {@code OPTIONS} must look like it originated from the control panel UI itself:</p>
 * <ul>
 *   <li>the {@code Sec-Fetch-Site} Fetch Metadata header, when the browser sends it, must be
 *   {@code same-origin} or {@code none};</li>
 *   <li>an {@code Origin} header, when present, must match the request's own origin;</li>
 *   <li>the control panel request header
 *   ({@value io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths#REQUESTED_WITH_HEADER})
 *   must be present with the expected value, which only same-origin scripts can set.</li>
 * </ul>
 */
@Requires(condition = ControlPanelEnabledCondition.class)
@ServerFilter(
    value = {ControlPanelSecurityPaths.CONTROL_PANEL_FILTER_PATTERN, ControlPanelSecurityPaths.CONTROL_PANEL_SUBPATHS_FILTER_PATTERN},
    appendContextPath = false
)
@Internal
final class ControlPanelCrossSiteRequestFilter implements Ordered {

    private static final int ORDER = ServerFilterPhase.FIRST.order();
    private static final Set<String> ALLOWED_FETCH_SITES = Set.of("same-origin", "none");
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

    private final boolean enabled;
    private final HttpHostResolver hostResolver;

    ControlPanelCrossSiteRequestFilter(ControlPanelRequestConfiguration requestConfiguration,
                                       HttpHostResolver hostResolver) {
        this.enabled = requestConfiguration.isCsrfProtectionEnabled();
        this.hostResolver = hostResolver;
    }

    /**
     * Rejects a mutating control panel request that does not look like it came from the control
     * panel UI.
     *
     * @param request the control panel request
     * @return a forbidden response to reject the request, or {@code null} to continue
     */
    @RequestFilter
    @Nullable HttpResponse<?> filterRequest(HttpRequest<?> request) {
        return isRejected(request) ? HttpResponse.status(HttpStatus.FORBIDDEN) : null;
    }

    private boolean isRejected(HttpRequest<?> request) {
        if (!enabled || isSafe(request.getMethod())) {
            return false;
        }
        HttpHeaders headers = request.getHeaders();
        String fetchSite = headers.get(SEC_FETCH_SITE);
        if (fetchSite != null && !ALLOWED_FETCH_SITES.contains(fetchSite.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String origin = headers.getOrigin().orElse(null);
        if (origin != null && !isSameOrigin(origin, request)) {
            return true;
        }
        String requestedWith = headers.get(ControlPanelSecurityPaths.REQUESTED_WITH_HEADER);
        return !ControlPanelSecurityPaths.REQUESTED_WITH_VALUE.equals(requestedWith);
    }

    private boolean isSameOrigin(String origin, HttpRequest<?> request) {
        String normalizedOrigin = normalizeOrigin(origin);
        // The forwarded-header-aware resolved origin covers reverse-proxy deployments.
        if (normalizedOrigin.equals(normalizeOrigin(hostResolver.resolve(request)))) {
            return true;
        }
        // Fall back to the request's own Host header, which preserves IPv6 brackets such as [::1].
        String host = request.getHeaders().get(HttpHeaders.HOST);
        if (host != null) {
            String scheme = request.isSecure() ? "https" : "http";
            return normalizedOrigin.equals(normalizeOrigin(scheme + "://" + host));
        }
        return false;
    }

    /**
     * Normalises an origin to {@code scheme://host:port} (lower-cased, with the default port made
     * explicit) using IPv6-aware URI parsing, so bracketed IPv6 authorities compare correctly.
     */
    private static String normalizeOrigin(String value) {
        if (value == null) {
            return "";
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return value.trim().toLowerCase(Locale.ROOT);
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static boolean isSafe(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
