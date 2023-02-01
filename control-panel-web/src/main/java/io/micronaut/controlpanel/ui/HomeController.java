package io.micronaut.controlpanel.ui;

import com.github.jknack.handlebars.Handlebars;
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
    }

    @View("index")
    @Get
    public HttpResponse index() {
        List<ControlPanel.Category> categories = repository.findAllCategories();
        List<ControlPanel> controlPanels = repository.findAll();
        return HttpResponse.ok(Map.of(
            "micronautVersion", VersionUtils.getMicronautVersion(),
            "controlPanels", controlPanels,
            "categories", categories
        ));
    }
}
