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
package io.micronaut.controlpanel.panels.reactor;

import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds Reactor route diagnostics from route metadata without invoking routes.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
final class ReactorRouteAnalyzer {

    private static final String MONO = "reactor.core.publisher.Mono";
    private static final String FLUX = "reactor.core.publisher.Flux";
    private static final String MODE_SINGLE = "single";
    private static final String MODE_MULTI = "multi";
    private static final String MODE_STREAMING = "streaming";
    private static final String MODE_SSE = "sse";
    private static final String MODE_UNKNOWN = "unknown";
    private static final Comparator<UriRouteInfo<?, ?>> ROUTE_ORDER =
        Comparator.comparing((UriRouteInfo<?, ?> route) -> route.getUriMatchTemplate().toPathString())
            .thenComparing(UriRouteInfo::getHttpMethodName)
            .thenComparing(route -> route.getTargetMethod().getDeclaringType().getName())
            .thenComparing(route -> route.getTargetMethod().getName());

    private final Router router;

    ReactorRouteAnalyzer(Router router) {
        this.router = router;
    }

    List<ReactorDiagnosticsBody.ReactiveRoute> routes() {
        return router.uriRoutes()
            .filter(route -> !isMicronautRoute(route))
            .filter(route -> reactorKind(route.getReturnType().asArgument()) != ReactorKind.NONE)
            .distinct()
            .sorted(ROUTE_ORDER)
            .map(this::toRoute)
            .toList();
    }

    private ReactorDiagnosticsBody.ReactiveRoute toRoute(UriRouteInfo<?, ?> route) {
        Argument<?> returnType = route.getReturnType().asArgument();
        ReactorKind kind = reactorKind(returnType);
        String mode = mode(kind, route.getProduces());
        return new ReactorDiagnosticsBody.ReactiveRoute(
            route.getHttpMethodName(),
            route.getUriMatchTemplate().toPathString(),
            route.getTargetMethod().getDeclaringType().getSimpleName() + "." + route.getTargetMethod().getName() + "(" + Argument.toString(route.getTargetMethod().getArguments()) + ")",
            returnType.getTypeString(false),
            mode,
            modeDescription(mode),
            mediaTypes(route.getProduces()),
            mediaTypes(route.getConsumes()),
            true
        );
    }

    private static boolean isMicronautRoute(UriRouteInfo<?, ?> route) {
        Package p = route.getTargetMethod().getDeclaringType().getPackage();
        return p != null && p.getName().startsWith("io.micronaut");
    }

    private static ReactorKind reactorKind(Argument<?> argument) {
        String typeName = argument.getType().getName();
        String typeString = argument.getTypeString(false);
        if (MONO.equals(typeName) || typeString.startsWith(MONO + "<") || typeString.equals(MONO)) {
            return ReactorKind.MONO;
        }
        if (FLUX.equals(typeName) || typeString.startsWith(FLUX + "<") || typeString.equals(FLUX)) {
            return ReactorKind.FLUX;
        }
        for (Argument<?> typeParameter : argument.getTypeParameters()) {
            ReactorKind nested = reactorKind(typeParameter);
            if (nested != ReactorKind.NONE) {
                return ReactorKind.NESTED;
            }
        }
        return ReactorKind.NONE;
    }

    private static String mode(ReactorKind kind, List<MediaType> produces) {
        return switch (kind) {
            case MONO -> MODE_SINGLE;
            case FLUX -> fluxMode(produces);
            case NESTED -> MODE_UNKNOWN;
            case NONE -> MODE_UNKNOWN;
        };
    }

    private static String fluxMode(List<MediaType> produces) {
        if (produces.stream().map(ReactorRouteAnalyzer::mediaTypeName).anyMatch(MediaType.TEXT_EVENT_STREAM::equals)) {
            return MODE_SSE;
        }
        if (produces.stream().map(ReactorRouteAnalyzer::mediaTypeName).anyMatch(ReactorRouteAnalyzer::isStreamingMediaType)) {
            return MODE_STREAMING;
        }
        return MODE_MULTI;
    }

    private static String mediaTypeName(MediaType mediaType) {
        return mediaType.getName().toLowerCase(Locale.ROOT);
    }

    private static boolean isStreamingMediaType(String mediaType) {
        return mediaType.equals(MediaType.APPLICATION_JSON_STREAM)
            || mediaType.equals("application/x-ndjson")
            || mediaType.contains("+stream");
    }

    private static String modeDescription(String mode) {
        return switch (mode) {
            case MODE_SINGLE -> "Mono single result";
            case MODE_SSE -> "Flux server-sent events";
            case MODE_STREAMING -> "Flux streaming response";
            case MODE_MULTI -> "Flux multi-value response";
            default -> "Nested Reactor type";
        };
    }

    private static List<String> mediaTypes(List<MediaType> mediaTypes) {
        if (mediaTypes.isEmpty()) {
            return List.of();
        }
        return mediaTypes.stream().map(MediaType::toString).toList();
    }

    private enum ReactorKind {
        NONE,
        MONO,
        FLUX,
        NESTED
    }
}
