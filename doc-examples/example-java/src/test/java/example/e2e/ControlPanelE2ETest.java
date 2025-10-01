package example.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.impl.driver.jar.DriverJar;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@UsePlaywright
@MicronautTest
class ControlPanelE2ETest {

    @Inject
    EmbeddedServer server;

    @BeforeAll
    static void graalVmSetup() {
        if (StringUtils.hasText(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
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

    @Test
    void testDashboard(Page page) {
        page.navigate(baseUrl());
        var body = page.locator("body");

        //Control Panels in the dashboard
        assertThat(body).containsText("Micronaut Control Panel for my-application");
        assertThat(body).containsText("Application Health");
        assertThat(body).containsText("Environment Properties");
        assertThat(body).containsText("HTTP Routes");
        assertThat(body).containsText("Bean Definitions");
        assertThat(body).containsText("Loggers");

        //Category links
        assertThat(categoryLink(page, "Dashboard")).isVisible();
        assertThat(categoryLink(page, "My Application")).isVisible();

        //Action buttons
        assertThat(page.locator("id=refreshButton")).isVisible();
        assertThat(page.locator("id=stopButton")).isVisible();
    }

    @Test
    void testApplicationHealth(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Application Health").click();
        assertThat(page.locator("body")).containsText("All health checks passed");
        assertThat(page.locator("body")).containsText("Composite Discovery Client");
        assertThat(page.locator("body")).containsText("Disk Space");
    }

    private static Locator controlPanelDetails(final Page page, final String name) {
        return page
            .locator(".row")
            .filter(
                new Locator.FilterOptions()
                    .setHas(page.getByRole(AriaRole.HEADING, new GetByRoleOptions().setName(nameRegex(name))))
            )
            .getByRole(AriaRole.BUTTON);
    }

    private static Locator categoryLink(final Page page, final String name) {
        return page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(nameRegex(name)));
    }

    private static Pattern nameRegex(final String name) {
        return Pattern.compile("^.*" + name + ".*$");
    }

    private String baseUrl() {
        return "%s%s".formatted(server.getURL(), ControlPanelModuleConfiguration.DEFAULT_PATH);
    }
}
