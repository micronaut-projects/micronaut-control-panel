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
        var body = body(page);

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
        assertThat(id(page,"refreshButton")).isVisible();
        assertThat(id(page,"stopButton")).isVisible();
    }

    @Test
    void testApplicationHealth(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Application Health").click();

        assertThat(body(page)).containsText("All health checks passed");
        assertThat(body(page)).containsText("Composite Discovery Client");
        assertThat(body(page)).containsText("Disk Space");
    }

    @Test
    void testEnvironmentProperties(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Environment Properties").click();

        // Values appear in plain text
        assertThat(body(page)).containsText("micronaut.control-panel.env.show-values = true");

        // Sensitive data is still masked
        assertThat(body(page)).containsText("test.password = *****");
    }

    @Test
    void testHttpRoutes(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "HTTP Routes").click();

        assertThat(body(page)).containsText("Application routes");
        assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("/demo/test1"))).hasCount(2);
        assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("/demo/test2"))).hasCount(2);

        assertThat(body(page)).containsText("Micronaut Framework routes");
    }

    @Test
    void testBeanDefinitions(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Bean Definitions").click();

        assertThat(body(page)).containsText("Other beans");
        button(page, "Package example").first().click();
        assertThat(page.locator("#theGraph"))
            .matchesAriaSnapshot("- document: example.DemoController example.MyApplicationControlPanel io.micronaut.controlpanel.core.config.ControlPanelConfiguration");

        assertThat(body(page)).containsText("Micronaut Framework beans");
    }

    @Test
    void testLoggers(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Loggers").click();

        assertThat(body(page)).containsText("ROOT");
        assertThat(body(page)).containsText("example");
        assertThat(body(page)).containsText("io.micronaut.controlpanel");

        assertThat(page.getByRole(AriaRole.DEFINITION).nth(1)).containsText("INFO");

        page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName(nameRegex("Reconfigure"))).first().getByRole(AriaRole.BUTTON).click();
        assertThat(page.locator("#modalLabel")).containsText("Reconfigure logger ROOT");

        page.getByLabel("Level:").selectOption("DEBUG");
        button(page, "Submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Logger configured");

        page.getByText("Close").click();
        assertThat(page.getByRole(AriaRole.DEFINITION).nth(1)).containsText("DEBUG");
    }

    @Test
    void testCustomCategory(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "My Application").click();

        assertThat(body(page)).containsText("My Application Control Panel");
        assertThat(body(page)).containsText("This is the body of the application-provided control panel");

        button(page, "Details").click();
        assertThat(body(page)).containsText("This is an application-provided control panel. This text is coming from the body");
    }

    private static Locator button(final Page page, final String name) {
        return page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(name));
    }


    private static Locator body(final Page page) {
        return page.locator("body");
    }

    private static Locator id(final Page page, final String id) {
        return page.locator("id=" + id);
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
