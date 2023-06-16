package io.micronaut.controlpanel.ui;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HumanizeHelper;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.views.View;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Control panel web controller to render the UI.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Controller("/control-panel")
public class ControlPanelController {

    private final ControlPanelRepository repository;

    private final ApplicationConfiguration applicationConfiguration;
    private final Environment environment;

    public ControlPanelController(Handlebars handlebars, ControlPanelRepository repository, ApplicationConfiguration applicationConfiguration, Environment environment) {
        this.repository = repository;
        this.applicationConfiguration = applicationConfiguration;
        this.environment = environment;

        //TODO move this to a better place
        HumanizeHelper.register(handlebars);
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelpers(StringHelpers.class);
        handlebars.registerHelper("percentage", (ctx, opts) -> {
            Double value = ((Long) ctx).doubleValue();
            Double total = ((Long) opts.param(0)).doubleValue();
            return (int) Math.ceil((value / total) * 100);
        });
        handlebars.registerHelper("minus", (ctx, opts) -> {
            int a = (int) ctx;
            int b = opts.param(0);
            return a - b;
        });
        handlebars.registerHelper("mod", (ctx, opts) -> {
            int a = (int) ctx + 1;
            int b = opts.param(0);
            int c = opts.param(1);

            if ((a % b) == c) {
                return opts.fn();
            } else {
                return opts.inverse();
            }
        });
        handlebars.registerHelper("size", (ctx, opts) -> ((Collection<?>) ctx).size());
//        handlebars.registerHelper("invoke", (ctx, opts) -> {
//            Class<?> clazz = ctx.getClass();
//            String methodName = opts.param(0);
//            Optional<Method> method = ReflectionUtils.getMethod(clazz, methodName);
//            if (method.isPresent()) {
//                return ReflectionUtils.invokeMethod(ctx, method.get());
//            } else {
//                return opts.inverse();
//            }
//        });
    }

    @View("layout")
    @Get
    public HttpResponse<Map<String, Object>> index() {
        return byCategory(ControlPanel.Category.MAIN.id());
    }

    @View("layout")
    @Get("/categories/{categoryId}")
    public HttpResponse<Map<String, Object>> byCategory(String categoryId) {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        Optional<ControlPanel.Category> optionalCategory = repository.findCategoryById(categoryId);
        List<ControlPanel> controlPanels = repository.findAllByCategory(categoryId);
        return HttpResponse.ok(Map.of(
            "micronautVersion", VersionUtils.getMicronautVersion(),
            "applicationName", applicationConfiguration.getName().orElse("(unnamed)"),
            "activeEnvironments", environment.getActiveNames(),
            "controlPanels", controlPanels,
            "categories", categories,
            "currentCategory", optionalCategory.get(),
            "contentView", "index"
        ));

    }

    @View("layout")
    @Get("/{controlPanelName}")
    public HttpResponse<Map<String, Object>> detail(String controlPanelName) {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        Map<String, Object> response = new HashMap<>();
        response.put("micronautVersion", VersionUtils.getMicronautVersion());
        response.put("applicationName", applicationConfiguration.getName().orElse("(unnamed)"));
        response.put("activeEnvironments", environment.getActiveNames());
        response.put("categories", categories);
        response.put("contentView", "detail");

        Optional<ControlPanel> optionalControlPanel = repository.findByName(controlPanelName);
        if (optionalControlPanel.isPresent()) {
            Optional<ControlPanel.Category> optionalCategory = repository.findCategoryById(optionalControlPanel.get().getCategory().id());
            response.putAll(Map.of(
                "controlPanel", optionalControlPanel.get(),
                "currentCategory", optionalCategory.get()
            ));
        }
        return HttpResponse.ok(response);
    }
}
