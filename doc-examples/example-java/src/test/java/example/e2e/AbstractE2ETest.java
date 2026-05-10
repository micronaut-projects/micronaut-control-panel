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
        return page.locator(".cp-card-footer .btn[href$='/" + controlPanelName(name) + "']").first();
    }

    static Locator categoryLink(final Page page, final String name) {
        return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(nameRegex(name)));
    }

    static Pattern nameRegex(final String name) {
        return Pattern.compile("^.*" + name + ".*$");
    }

    private static String controlPanelName(final String title) {
        return switch (title) {
            case "Application Health" -> "health";
            case "Environment Properties" -> "env";
            case "HTTP Routes" -> "routes";
            case "Bean Definitions" -> "beans";
            case "Disabled Beans" -> "disabled-beans";
            case "Loggers" -> "loggers";
            case "my-oracle" -> "datasource-my-oracle";
            case "Kafka" -> "kafka-streams-default";
            case "my-postgres" -> "hibernate-my-postgres";
            default -> title;
        };
    }

    String baseUrl() {
        return "%s%s".formatted(server.getURL(), ControlPanelUtils.computeControlPanelPath(appPath, controlPanelPath));
    }
}
