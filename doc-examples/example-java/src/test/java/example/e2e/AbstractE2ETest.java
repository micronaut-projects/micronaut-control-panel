package example.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.impl.driver.jar.DriverJar;
import com.microsoft.playwright.options.AriaRole;
import io.micronaut.context.annotation.Value;
import io.micronaut.controlpanel.util.ControlPanelUtils;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.regex.Pattern;

class AbstractE2ETest {

    static {
        if (NativeImageUtils.inImageCode()) {
            try {
                URI uri = DriverJar.getDriverResourceURI();
                FileSystems.newFileSystem(uri, Map.of());
                new DriverJar();
            } catch (URISyntaxException | IOException e) {
                // Wrap and throw any exceptions that occur during initialization
                throw new RuntimeException(e);
            }
        }
    }

    @Inject
    EmbeddedServer server;

    @Value("${micronaut.server.context-path:/}")
    String appPath;

    @Value("${micronaut.control-panel.path:/control-panel}")
    String controlPanelPath;

    static Locator button(final Page page, final String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }


    static Locator body(final Page page) {
        return page.locator("body");
    }

    static Locator id(final Page page, final String id) {
        return page.locator("id=" + id);
    }

    static Locator controlPanelDetails(final Page page, final String name) {
        return page
            .locator(".cp-panel-card")
            .filter(
                new Locator.FilterOptions()
                    .setHas(
                        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setExact(true))
                    )
            )
            .locator(".cp-card-footer a, .cp-card-footer button")
            .first();
    }

    static Locator categoryLink(final Page page, final String name) {
        return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(nameRegex(name)));
    }

    static Pattern nameRegex(final String name) {
        return Pattern.compile("^.*" + name + ".*$");
    }

    String baseUrl() {
        return "%s%s".formatted(server.getURL(), ControlPanelUtils.computeControlPanelPath(appPath, controlPanelPath));
    }
}
