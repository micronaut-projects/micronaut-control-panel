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
package io.micronaut.controlpanel.panels.views;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsModelDecorator;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.views.csp.CspConfiguration;
import io.micronaut.views.http.ViewsFilterConfiguration;
import io.micronaut.views.model.ViewModelProcessor;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads the Micronaut Views configuration, renderers, model processors and view routes.
 *
 * <p>It does not call controllers or render templates, and it does not read requests, sessions or models. Looking
 * up a template only asks each renderer whether it exists.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
public class ViewsInspector {

    private static final Logger LOG = LoggerFactory.getLogger(ViewsInspector.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public ViewsInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @param checkTemplates whether to look up the template of each route
     * @return what Micronaut Views is configured to render
     */
    public ViewsInfo inspect(boolean checkTemplates) {
        Optional<ViewsConfiguration> configuration = beanContext.findBean(ViewsConfiguration.class);
        boolean filterEnabled = beanContext.findBean(ViewsFilterConfiguration.class).map(ViewsFilterConfiguration::isEnabled).orElse(false);
        List<RendererBean> renderers = renderers(checkTemplates);
        return new ViewsInfo(
            configuration.map(ViewsConfiguration::isEnabled).orElse(false),
            configuration.map(ViewsConfiguration::getFolder).orElse("views/"),
            filterEnabled,
            csp(),
            renderers.stream().map(RendererBean::info).toList(),
            modelProcessors(),
            routes(checkTemplates ? renderers : null),
            checkTemplates
        );
    }

    private ViewsInfo.@Nullable Csp csp() {
        return beanContext.findBean(CspConfiguration.class)
            .filter(CspConfiguration::isEnabled)
            .map(csp -> new ViewsInfo.Csp(csp.isReportOnly(), csp.isNonceEnabled(), csp.getFilterPath(), csp.getPolicyDirectives().orElse(null)))
            .orElse(null);
    }

    @SuppressWarnings("rawtypes")
    private List<RendererBean> renderers(boolean instantiate) {
        List<RendererBean> result = new ArrayList<>();
        for (BeanDefinition<ViewsRenderer> definition : beanContext.getBeanDefinitions(ViewsRenderer.class)) {
            List<String> mediaTypes = Arrays.asList(definition.stringValues(Produces.class));
            ViewsInfo.Renderer info = new ViewsInfo.Renderer(definition.getBeanType().getName(), mediaTypes);
            ViewsRenderer<?, ?> renderer = null;
            if (instantiate) {
                try {
                    renderer = beanContext.getBean(definition);
                } catch (RuntimeException e) {
                    LOG.debug("Unable to create views renderer {}", definition.getBeanType().getName(), e);
                }
            }
            result.add(new RendererBean(info, renderer));
        }
        result.sort(Comparator.comparing(bean -> bean.info().type()));
        return result;
    }

    private List<String> modelProcessors() {
        TreeSet<String> types = new TreeSet<>();
        Stream.concat(
            beanContext.getBeanDefinitions(ViewModelProcessor.class).stream(),
            beanContext.getBeanDefinitions(ViewsModelDecorator.class).stream()
        ).forEach(definition -> types.add(definition.getBeanType().getName()));
        return List.copyOf(types);
    }

    private List<ViewsInfo.ViewRoute> routes(@Nullable List<RendererBean> renderers) {
        Optional<Router> router;
        try {
            router = beanContext.findBean(Router.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the routes", e);
            return List.of();
        }
        if (router.isEmpty()) {
            return List.of();
        }
        List<ViewsInfo.ViewRoute> result = new ArrayList<>();
        router.get().uriRoutes().forEach(route -> {
            // Micronaut adds a HEAD route for each GET route, which renders the same view
            if (route.getHttpMethod() == HttpMethod.HEAD && !route.getAnnotationMetadata().hasDeclaredAnnotation(Head.class)) {
                return;
            }
            Optional<String> view = route.getAnnotationMetadata().stringValue(View.class).filter(value -> !value.isBlank());
            if (view.isEmpty() && !returnsModelAndView(route)) {
                return;
            }
            String viewName = view.orElse(null);
            result.add(new ViewsInfo.ViewRoute(
                route.getHttpMethodName(),
                route.getUriMatchTemplate().toString(),
                viewName,
                route.getDeclaringType().getName() + "#" + route.getTargetMethod().getMethodName(),
                renderers == null || viewName == null ? null : template(viewName, renderers)
            ));
        });
        result.sort(Comparator.comparing(ViewsInfo.ViewRoute::uri).thenComparing(ViewsInfo.ViewRoute::method));
        return result;
    }

    private static boolean returnsModelAndView(UriRouteInfo<?, ?> route) {
        Argument<?> type = route.getReturnType().asArgument();
        if (ModelAndView.class.isAssignableFrom(type.getType())) {
            return true;
        }
        // Covers HttpResponse<ModelAndView> and reactive or async return types, such as Mono<ModelAndView>
        return Arrays.stream(type.getTypeParameters()).anyMatch(argument -> ModelAndView.class.isAssignableFrom(argument.getType()));
    }

    private static String template(String view, Collection<RendererBean> renderers) {
        boolean failed = false;
        for (RendererBean renderer : renderers) {
            ViewsRenderer<?, ?> instance = renderer.renderer();
            if (instance == null) {
                continue;
            }
            try {
                if (instance.exists(view)) {
                    return simpleName(renderer.info().type());
                }
            } catch (RuntimeException e) {
                failed = true;
                LOG.debug("Unable to look up view '{}' with renderer {}", view, renderer.info().type(), e);
            }
        }
        return failed ? ViewsInfo.ViewRoute.CHECK_FAILED : ViewsInfo.ViewRoute.MISSING;
    }

    private static String simpleName(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    private record RendererBean(ViewsInfo.Renderer info, @Nullable ViewsRenderer<?, ?> renderer) {
    }
}
