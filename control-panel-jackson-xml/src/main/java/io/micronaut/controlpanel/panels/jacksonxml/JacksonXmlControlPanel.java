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
package io.micronaut.controlpanel.panels.jacksonxml;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Control panel for development-time Jackson XML HTTP serialization diagnostics.
 */
@Singleton
@Requires(property = JacksonXmlControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class JacksonXmlControlPanel extends AbstractControlPanel<JacksonXmlControlPanel.Body> {

    public static final String NAME = "jackson-xml";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("serialization", "Serialization", "fa-code");

    private static final String XML_MAPPER_BEAN = "xml";
    private static final String JACKSON_XML_CONFIGURATION_CLASS = "io.micronaut.xml.jackson.JacksonXmlConfiguration";
    private static final String XML_MAPPER_CLASS = "tools.jackson.dataformat.xml.XmlMapper";
    private static final String OBJECT_MAPPER_CLASS = "tools.jackson.databind.ObjectMapper";
    private static final String APPLICATION_XML = "application/xml";
    private static final String TEXT_XML = "text/xml";
    private static final String NOT_AVAILABLE = "Not available";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final Comparator<UriRouteInfo<?, ?>> ROUTE_COMPARATOR =
        Comparator.comparing((UriRouteInfo<?, ?> route) -> route.getUriMatchTemplate().toPathString())
            .thenComparing(UriRouteInfo::getHttpMethodName)
            .thenComparing(route -> route.getTargetMethod().getDeclaringType().getName())
            .thenComparing(route -> route.getTargetMethod().getName());
    private static final List<CustomizationKind> CUSTOMIZATION_KINDS = List.of(
        new CustomizationKind("module", "tools.jackson.databind.JacksonModule"),
        new CustomizationKind("serializer", "tools.jackson.databind.ValueSerializer"),
        new CustomizationKind("deserializer", "tools.jackson.databind.ValueDeserializer"),
        new CustomizationKind("serializer-modifier", "tools.jackson.databind.ser.ValueSerializerModifier"),
        new CustomizationKind("deserializer-modifier", "tools.jackson.databind.deser.ValueDeserializerModifier"),
        new CustomizationKind("key-deserializer", "tools.jackson.databind.KeyDeserializer")
    );

    private final BeanContext beanContext;
    private final Router router;
    private final Environment environment;

    public JacksonXmlControlPanel(BeanContext beanContext,
                                  Router router,
                                  Environment environment,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanContext = beanContext;
        this.router = router;
        this.environment = environment;
    }

    @Override
    public Body getBody() {
        try {
            return buildBody();
        } catch (RuntimeException e) {
            return Body.error("Jackson XML diagnostics are unavailable: " + e.getMessage());
        }
    }

    @Override
    public String getBadge() {
        return String.valueOf(getBody().summary().xmlRoutes());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    private Body buildBody() {
        boolean dependencyPresent = isClassPresent(JACKSON_XML_CONFIGURATION_CLASS) && isClassPresent(XML_MAPPER_CLASS);
        List<XmlRoute> routes = dependencyPresent ? xmlRoutes() : List.of();
        MapperDiagnostic mapper = mapperDiagnostic(dependencyPresent);
        List<Customization> customizations = dependencyPresent ? customizations() : List.of();
        Summary summary = new Summary(
            routes.size(),
            (int) routes.stream().filter(XmlRoute::consumesXml).count(),
            (int) routes.stream().filter(XmlRoute::producesXml).count(),
            customizations.size(),
            mapper.status()
        );
        PanelState state = state(dependencyPresent, mapper, routes);
        return new Body(
            state,
            stateMessage(state),
            summary,
            mapper,
            routes,
            customizations,
            !routes.isEmpty(),
            !customizations.isEmpty()
        );
    }

    private MapperDiagnostic mapperDiagnostic(boolean dependencyPresent) {
        if (!dependencyPresent) {
            return new MapperDiagnostic(false, false, "", null, NOT_AVAILABLE, List.of(), List.of(), "Missing dependency", false, false);
        }
        Optional<BeanDefinition<Object>> mapperDefinition = findNamedXmlMapperDefinition();
        String mapperClassName = mapperDefinition.map(this::mapperClassName).orElse("");
        Optional<Object> xmlConfiguration = xmlConfiguration();
        Optional<Boolean> defaultUseWrapper = defaultUseWrapper(xmlConfiguration);
        List<FeatureOverride> parserSettings = featureOverrides(xmlConfiguration, "getParserSettings", "jackson.xml.parser");
        List<FeatureOverride> generatorSettings = featureOverrides(xmlConfiguration, "getGeneratorSettings", "jackson.xml.generator");
        return new MapperDiagnostic(
            true,
            mapperDefinition.isPresent(),
            mapperClassName,
            defaultUseWrapper.orElse(null),
            defaultUseWrapper.map(String::valueOf).orElse(NOT_AVAILABLE),
            parserSettings,
            generatorSettings,
            mapperDefinition.isPresent() ? "Active" : "No named xml mapper",
            !parserSettings.isEmpty(),
            !generatorSettings.isEmpty()
        );
    }

    private PanelState state(boolean dependencyPresent, MapperDiagnostic mapper, List<XmlRoute> routes) {
        if (!dependencyPresent) {
            return PanelState.MISSING_DEPENDENCY;
        }
        if (!mapper.namedMapperPresent()) {
            return PanelState.NO_MAPPER;
        }
        if (routes.isEmpty()) {
            return PanelState.EMPTY;
        }
        return PanelState.POPULATED;
    }

    private String stateMessage(PanelState state) {
        return switch (state) {
            case POPULATED -> "XML HTTP route metadata and mapper diagnostics are available.";
            case EMPTY -> "No application route declares XML consumes or produces metadata.";
            case MISSING_DEPENDENCY -> "Micronaut Jackson XML is not on the application classpath.";
            case NO_MAPPER -> "Micronaut Jackson XML is present, but no named xml mapper bean is available.";
            case ERROR -> "Jackson XML diagnostics failed.";
        };
    }

    private List<XmlRoute> xmlRoutes() {
        List<UriRouteInfo<?, ?>> routes = router.uriRoutes()
            .filter(route -> mediaTypesIncludeXml(route.getConsumes()) || mediaTypesIncludeXml(route.getProduces()))
            .distinct()
            .sorted(ROUTE_COMPARATOR)
            .toList();
        List<XmlRoute> result = new ArrayList<>(routes.size());
        for (int i = 0; i < routes.size(); i++) {
            UriRouteInfo<?, ?> route = routes.get(i);
            if (!isSkippableHead(route, i, routes)) {
                result.add(xmlRoute(route));
            }
        }
        return List.copyOf(result);
    }

    private XmlRoute xmlRoute(UriRouteInfo<?, ?> route) {
        var targetMethod = route.getTargetMethod();
        String declaringType = targetMethod.getDeclaringType().getName();
        List<String> consumes = mediaTypeNames(route.getConsumes());
        List<String> produces = mediaTypeNames(route.getProduces());
        return new XmlRoute(
            route.getHttpMethodName(),
            route.getUriMatchTemplate().toPathString(),
            consumes,
            produces,
            declaringType,
            targetMethod.getName(),
            argumentSummary(targetMethod.getArguments()),
            isFrameworkType(declaringType) ? "framework" : "application",
            mediaNamesIncludeXml(consumes),
            mediaNamesIncludeXml(produces)
        );
    }

    private static List<String> mediaTypeNames(List<MediaType> mediaTypes) {
        if (mediaTypes.isEmpty()) {
            return List.of();
        }
        return mediaTypes.stream().map(MediaType::toString).sorted().toList();
    }

    private static String argumentSummary(Argument<?>[] arguments) {
        if (arguments.length == 0) {
            return "";
        }
        List<String> values = new ArrayList<>(arguments.length);
        for (Argument<?> argument : arguments) {
            values.add(argument.toString());
        }
        return String.join(", ", values);
    }

    private static boolean mediaTypesIncludeXml(List<MediaType> mediaTypes) {
        return mediaNamesIncludeXml(mediaTypeNames(mediaTypes));
    }

    private static boolean mediaNamesIncludeXml(List<String> mediaTypes) {
        return mediaTypes.stream().anyMatch(JacksonXmlControlPanel::isXmlMediaType);
    }

    static boolean isXmlMediaType(@Nullable String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        if (parameters >= 0) {
            normalized = normalized.substring(0, parameters);
        }
        normalized = normalized.trim();
        if (APPLICATION_XML.equals(normalized) || TEXT_XML.equals(normalized)) {
            return true;
        }
        int separator = normalized.indexOf('/');
        if (separator < 0 || separator == normalized.length() - 1) {
            return false;
        }
        String subtype = normalized.substring(separator + 1);
        return subtype.equals("xml") || subtype.endsWith("+xml") || subtype.endsWith(".xml");
    }

    private static boolean isFrameworkType(String declaringType) {
        return declaringType.startsWith("io.micronaut.");
    }

    private static boolean isSkippableHead(UriRouteInfo<?, ?> route, int index, List<UriRouteInfo<?, ?>> routes) {
        if (!METHOD_HEAD.equals(route.getHttpMethodName())) {
            return false;
        }
        for (int i = index - 1; i >= 0; i--) {
            UriRouteInfo<?, ?> candidate = routes.get(i);
            if (sameRoute(candidate, route) && METHOD_GET.equals(candidate.getHttpMethodName())) {
                return true;
            }
            if (!candidate.getUriMatchTemplate().toPathString().equals(route.getUriMatchTemplate().toPathString())) {
                return false;
            }
        }
        return false;
    }

    private static boolean sameRoute(UriRouteInfo<?, ?> first, UriRouteInfo<?, ?> second) {
        return first.getUriMatchTemplate().toPathString().equals(second.getUriMatchTemplate().toPathString())
            && first.getConsumes().equals(second.getConsumes())
            && first.getProduces().equals(second.getProduces())
            && first.getTargetMethod().getDeclaringType().equals(second.getTargetMethod().getDeclaringType())
            && first.getTargetMethod().getName().equals(second.getTargetMethod().getName());
    }

    private Optional<BeanDefinition<Object>> findNamedXmlMapperDefinition() {
        Qualifier<Object> qualifier = Qualifiers.byName(XML_MAPPER_BEAN);
        return beanContext.getBeanDefinitions(qualifier)
            .stream()
            .filter(definition -> isXmlMapperType(definition.getBeanType()))
            .findFirst();
    }

    private boolean isXmlMapperType(Class<?> beanType) {
        if (XML_MAPPER_CLASS.equals(beanType.getName())) {
            return true;
        }
        Optional<Class<?>> objectMapperClass = ClassUtils.forName(OBJECT_MAPPER_CLASS, environment.getClassLoader());
        return objectMapperClass
            .filter(type -> type.isAssignableFrom(beanType))
            .filter(type -> beanType.getName().toLowerCase(Locale.ROOT).contains("xml"))
            .isPresent();
    }

    private String mapperClassName(BeanDefinition<Object> definition) {
        try {
            return beanContext.getBean(definition).getClass().getName();
        } catch (RuntimeException _) {
            return definition.getBeanType().getName();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Object> xmlConfiguration() {
        return ClassUtils.forName(JACKSON_XML_CONFIGURATION_CLASS, environment.getClassLoader())
            .flatMap(type -> beanContext.findBean((Class) type));
    }

    private Optional<Boolean> defaultUseWrapper(Optional<Object> xmlConfiguration) {
        return xmlConfiguration
            .flatMap(config -> invoke(config, "isDefaultUseWrapper", Boolean.class))
            .or(() -> environment.getProperty("jackson.xml.default-use-wrapper", Boolean.class));
    }

    private List<FeatureOverride> featureOverrides(Optional<Object> xmlConfiguration, String methodName, String configKeyFamily) {
        Map<?, ?> settings = xmlConfiguration
            .flatMap(config -> invoke(config, methodName, Map.class))
            .orElse(Map.of());
        if (settings.isEmpty()) {
            settings = environment.getProperties(configKeyFamily, StringConvention.RAW);
        }
        return settings.entrySet()
            .stream()
            .map(entry -> new FeatureOverride(featureName(entry.getKey()), String.valueOf(entry.getValue()), configKeyFamily))
            .sorted(Comparator.comparing(FeatureOverride::featureName))
            .toList();
    }

    private static String featureName(Object feature) {
        if (feature instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return String.valueOf(feature);
    }

    private <T> Optional<T> invoke(Object target, String methodName, Class<T> returnType) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (returnType.isInstance(result)) {
                return Optional.of(returnType.cast(result));
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException _) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private List<Customization> customizations() {
        List<Customization> result = new ArrayList<>();
        for (CustomizationKind kind : CUSTOMIZATION_KINDS) {
            ClassUtils.forName(kind.typeName(), environment.getClassLoader())
                .ifPresent(type -> addCustomizations(result, kind.name(), type));
        }
        return result.stream()
            .sorted(Comparator.comparing(Customization::kind).thenComparing(Customization::implementationClassName))
            .toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addCustomizations(List<Customization> customizations, String kind, Class<?> type) {
        Collection<BeanDefinition<Object>> definitions = (Collection) beanContext.getBeanDefinitions((Class) type);
        for (BeanDefinition<Object> definition : definitions) {
            customizations.add(new Customization(
                kind,
                definition.getBeanName().orElse(""),
                definition.getBeanType().getName(),
                targetTypeNames(definition)
            ));
        }
    }

    private List<String> targetTypeNames(BeanDefinition<Object> definition) {
        return definition.getAnnotationValuesByType(io.micronaut.context.annotation.Type.class)
            .stream()
            .flatMap(annotation -> Arrays.stream(annotation.classValues("value")))
            .map(Class::getName)
            .sorted()
            .toList();
    }

    private boolean isClassPresent(String className) {
        return ClassUtils.isPresent(className, environment.getClassLoader());
    }

    private record CustomizationKind(String name, String typeName) {
    }

    /**
     * Overall panel state.
     */
    @ReflectiveAccess
    public enum PanelState {
        POPULATED,
        EMPTY,
        MISSING_DEPENDENCY,
        NO_MAPPER,
        ERROR
    }

    /**
     * Summary counters rendered above the diagnostics sections.
     *
     * @param xmlRoutes number of routes with explicit XML metadata
     * @param consumesXml number of routes that consume XML
     * @param producesXml number of routes that produce XML
     * @param customizations number of Jackson customization beans
     * @param mapperStatus mapper availability status
     */
    @ReflectiveAccess
    public record Summary(int xmlRoutes,
                          int consumesXml,
                          int producesXml,
                          int customizations,
                          String mapperStatus) {
    }

    /**
     * Mapper diagnostics rendered by the panel.
     *
     * @param dependencyPresent whether Micronaut Jackson XML is present
     * @param namedMapperPresent whether the named XML mapper bean is present
     * @param mapperClassName mapper implementation class name
     * @param defaultUseWrapper configured default wrapper value
     * @param defaultUseWrapperDisplay rendered wrapper value
     * @param parserFeatures configured XML parser feature overrides
     * @param generatorFeatures configured XML generator feature overrides
     * @param status mapper status label
     * @param hasParserFeatures whether parser feature overrides exist
     * @param hasGeneratorFeatures whether generator feature overrides exist
     */
    @ReflectiveAccess
    public record MapperDiagnostic(boolean dependencyPresent,
                                   boolean namedMapperPresent,
                                   String mapperClassName,
                                   @Nullable Boolean defaultUseWrapper,
                                   String defaultUseWrapperDisplay,
                                   List<FeatureOverride> parserFeatures,
                                   List<FeatureOverride> generatorFeatures,
                                   String status,
                                   boolean hasParserFeatures,
                                   boolean hasGeneratorFeatures) {
    }

    /**
     * XML parser or generator feature override.
     *
     * @param featureName feature name
     * @param enabled configured value
     * @param source configuration key family
     */
    @ReflectiveAccess
    public record FeatureOverride(String featureName,
                                  String enabled,
                                  String source) {
    }

    /**
     * Jackson customization bean metadata.
     *
     * @param kind customization kind
     * @param beanName bean name when available
     * @param implementationClassName implementation class name
     * @param targetTypeNames target type names from safe annotation metadata
     */
    @ReflectiveAccess
    public record Customization(String kind,
                                String beanName,
                                String implementationClassName,
                                List<String> targetTypeNames) {
        public boolean hasTargetTypeNames() {
            return !targetTypeNames.isEmpty();
        }
    }

    /**
     * Route metadata row for routes with explicit XML consumes or produces metadata.
     *
     * @param httpMethod HTTP method
     * @param path route path
     * @param consumes declared consumed media types
     * @param produces declared produced media types
     * @param declaringType declaring controller type
     * @param methodName handler method name
     * @param argumentSummary handler argument summary
     * @param source route source classification
     * @param consumesXml whether the route consumes XML
     * @param producesXml whether the route produces XML
     */
    @ReflectiveAccess
    public record XmlRoute(String httpMethod,
                           String path,
                           List<String> consumes,
                           List<String> produces,
                           String declaringType,
                           String methodName,
                           String argumentSummary,
                           String source,
                           boolean consumesXml,
                           boolean producesXml) {
    }

    /**
     * Body model for the Jackson XML panel.
     *
     * @param state panel state
     * @param stateMessage user-facing state message
     * @param summary summary counters
     * @param mapper mapper diagnostics
     * @param routes XML route rows
     * @param customizations Jackson customization metadata
     * @param hasRoutes whether route rows exist
     * @param hasCustomizations whether customization rows exist
     */
    @ReflectiveAccess
    public record Body(PanelState state,
                       String stateMessage,
                       Summary summary,
                       MapperDiagnostic mapper,
                       List<XmlRoute> routes,
                       List<Customization> customizations,
                       boolean hasRoutes,
                       boolean hasCustomizations) {
        static Body error(String message) {
            return new Body(
                PanelState.ERROR,
                message,
                new Summary(0, 0, 0, 0, "Error"),
                new MapperDiagnostic(false, false, "", null, NOT_AVAILABLE, List.of(), List.of(), "Error", false, false),
                List.of(),
                List.of(),
                false,
                false
            );
        }
    }
}
