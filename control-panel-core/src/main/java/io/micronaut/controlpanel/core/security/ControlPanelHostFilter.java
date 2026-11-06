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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Protects an anonymously reachable control panel against DNS rebinding by rejecting requests whose
 * {@code Host} header is not on an allowlist or that do not originate from a loopback address.
 *
 * <p>The filter only matches the control panel path and the helper routes nested under it,
 * including any server context path. The check applies to any such request that reaches this filter without an
 * authenticated principal. It runs after Micronaut Security, so when authentication is required
 * unauthenticated requests are already rejected and never reach here, whereas routes exposed
 * anonymously (security inactive, {@code access=ANONYMOUS}, or an {@code intercept-url-map} rule
 * granting anonymous access) are covered. An authenticated request is exempt. The check can be
 * disabled with {@code micronaut.control-panel.security.host-check-enabled=false}.</p>
 */
@Requires(condition = ControlPanelEnabledCondition.class)
@ServerFilter(
    value = {ControlPanelSecurityPaths.CONTROL_PANEL_FILTER_PATTERN, ControlPanelSecurityPaths.CONTROL_PANEL_SUBPATHS_FILTER_PATTERN},
    appendContextPath = false
)
@Internal
final class ControlPanelHostFilter implements Ordered {

    private static final int ORDER = ServerFilterPhase.SECURITY.after();
    private static final Set<String> BUILTIN_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final boolean hostCheckEnabled;
    private final Set<String> allowedHosts;

    ControlPanelHostFilter(ControlPanelRequestConfiguration requestConfiguration) {
        this.hostCheckEnabled = requestConfiguration.isHostCheckEnabled();
        Set<String> hosts = new HashSet<>(BUILTIN_HOSTS);
        Set<String> configured = requestConfiguration.getAllowedHosts();
        if (configured != null) {
            for (String host : configured) {
                if (StringUtils.isNotEmpty(host)) {
                    // Compare on host only, consistent with how the incoming Host header is reduced.
                    hosts.add(stripPort(host).toLowerCase(Locale.ROOT));
                }
            }
        }
        this.allowedHosts = Set.copyOf(hosts);
    }

    /**
     * Rejects an anonymous control panel request whose {@code Host} header is not allowed or that
     * does not come from a loopback address.
     *
     * @param request the control panel request
     * @return a forbidden response to reject the request, or {@code null} to continue
     */
    @RequestFilter
    @Nullable HttpResponse<?> filterRequest(HttpRequest<?> request) {
        return isRejected(request) ? HttpResponse.status(HttpStatus.FORBIDDEN) : null;
    }

    private boolean isRejected(HttpRequest<?> request) {
        if (!hostCheckEnabled) {
            return false;
        }
        // An authenticated request has proven its identity and is exempt. Any request that reaches
        // this post-security filter without a principal is anonymously reachable (security inactive,
        // ANONYMOUS access, or an intercept-url-map rule) and must satisfy the host/loopback check.
        if (request.getUserPrincipal().isPresent()) {
            return false;
        }
        return !isAllowedHost(request.getHeaders().get(HttpHeaders.HOST)) || !isLoopback(request);
    }

    private boolean isAllowedHost(String hostHeader) {
        if (StringUtils.isEmpty(hostHeader)) {
            return false;
        }
        String host = stripPort(hostHeader).toLowerCase(Locale.ROOT);
        if (allowedHosts.contains(host)) {
            return true;
        }
        return host.endsWith(".localhost");
    }

    private static String stripPort(String hostHeader) {
        String host = hostHeader.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end >= 0 ? host.substring(0, end + 1) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    private static boolean isLoopback(HttpRequest<?> request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null) {
            return false;
        }
        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().isLoopbackAddress();
        }
        String hostString = remoteAddress.getHostString();
        return "localhost".equalsIgnoreCase(hostString) || "127.0.0.1".equals(hostString)
            || "::1".equals(hostString) || "0:0:0:0:0:0:0:1".equals(hostString);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
