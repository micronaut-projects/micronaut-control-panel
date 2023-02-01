package io.micronaut.controlpanel.ui;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.View;

import java.util.List;
import java.util.Map;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Controller("/control-panel")
public class HomeController {

    private Handlebars handlebars;
    private ControlPanelRepository repository;

    public HomeController(Handlebars handlebars, ControlPanelRepository repository) {
        this.handlebars = handlebars;
        this.repository = repository;

        handlebars.registerHelper("eq", ConditionalHelpers.eq);
    }

    @View("index")
    @Get
    public HttpResponse index() {
        return byCategory(ControlPanel.Category.MAIN.id());
    }

    @View("index")
    @Get("/categories/{categoryId}")
    public HttpResponse byCategory(String categoryId) {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        ControlPanel.Category currentCategory = repository.findCategoryById(categoryId);
        List<ControlPanel> controlPanels = repository.findAllByCategory(categoryId);
        return HttpResponse.ok(Map.of(
            "micronautVersion", VersionUtils.getMicronautVersion(),
            "controlPanels", controlPanels,
            "categories", categories,
            "currentCategory", currentCategory
        ));

    }

    @View("detail")
    @Get("/{controlPanelName}")
    public HttpResponse detail(String controlPanelName) {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        ControlPanel controlPanel = repository.findByName(controlPanelName);
        ControlPanel.Category currentCategory = repository.findCategoryById(controlPanel.getCategory().id());
        return HttpResponse.ok(Map.of(
            "micronautVersion", VersionUtils.getMicronautVersion(),
            "controlPanel", controlPanel,
            "categories", categories,
            "currentCategory", currentCategory
        ));
    }
}
