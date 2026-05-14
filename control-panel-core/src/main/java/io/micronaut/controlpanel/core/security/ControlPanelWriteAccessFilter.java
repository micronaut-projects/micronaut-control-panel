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
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.HttpServerConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Optional;

import static io.micronaut.controlpanel.util.ControlPanelUtils.computeControlPanelPath;

/**
 * Fails explicit write-protection modes closed when Micronaut Security is not active.
 */
@Requires(condition = MissingSecurityFilterCondition.class)
@Filter(Filter.MATCH_ALL_PATTERN)
@Singleton
@Internal
final class ControlPanelWriteAccessFilter implements HttpServerFilter, Ordered {

    static final int ORDER = ServerFilterPhase.SECURITY.after();

    private final ControlPanelSecurityConfiguration.WriteAccess writeAccess;
    private final String controlPanelPath;

    ControlPanelWriteAccessFilter(ControlPanelSecurityConfiguration securityConfiguration,
                                  ControlPanelModuleConfiguration moduleConfiguration,
                                  HttpServerConfiguration serverConfiguration) {
        this.writeAccess = securityConfiguration.writeAccess();
        String applicationPath = Optional.ofNullable(serverConfiguration.getContextPath()).orElse("");
        this.controlPanelPath = computeControlPanelPath(applicationPath, moduleConfiguration.getPath());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (isDeniedWrite(request)) {
            return Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN));
        }
        return chain.proceed(request);
    }

    private boolean isDeniedWrite(HttpRequest<?> request) {
        return writeAccess != ControlPanelSecurityConfiguration.WriteAccess.INHERITED
            && writeAccess != ControlPanelSecurityConfiguration.WriteAccess.ANONYMOUS
            && ControlPanelSecurityPaths.isWriteRequest(controlPanelPath, request);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
