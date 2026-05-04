package example.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@UsePlaywright(ControlPanelE2ETest.HeadlessBrowserOptions.class)
@MicronautTest(environments = {"kafka", "oracle"})
class ControlPanelE2ETest extends AbstractE2ETest {

    @Test
    void testDashboard(Page page) {
        page.navigate(baseUrl());
        var body = body(page);

        //Control Panels in the dashboard
        assertThat(body).containsText("Control Panel");
        assertThat(body).containsText("my-application");
        assertThat(body).containsText("Application Health");
        assertThat(body).containsText("Environment Properties");
        assertThat(body).containsText("HTTP Routes");
        assertThat(body).containsText("Loggers");

        //Category links
        assertThat(categoryLink(page, "Dashboard")).isVisible();
        assertThat(categoryLink(page, "Beans")).isVisible();
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
        assertThat(body(page)).containsText("micronaut.control-panel.env.show-values");
        assertThat(body(page)).containsText("true");

        // Sensitive data is still masked
        assertThat(body(page)).containsText("test.password");
        assertThat(body(page)).containsText("*****");
    }

    @Test
    void testHttpRoutes(Page page, @Client("/") HttpClient httpClient) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "HTTP Routes").click();

        assertThat(body(page)).containsText("Application routes");
        assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("/demo/test1"))).hasCount(1);
        assertThat(page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName("/demo/test2"))).hasCount(1);
        assertThat(body(page)).containsText("Micronaut Framework routes");

        var viewerLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(nameRegex("Swagger UI")));
        assertThat(viewerLink).isVisible();

//        String href = viewerLink.getAttribute("href");
//        var resp = httpClient.toBlocking().exchange(io.micronaut.http.HttpRequest.GET(href));
//        assertEquals(HttpStatus.OK, resp.getStatus());
    }

    @Test
    void testBeanDefinitions(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Beans").click();
        controlPanelDetails(page, "Bean Definitions").click();

        assertThat(body(page)).containsText("Other beans");
        assertThat(body(page)).containsText("Micronaut Framework");
    }

    @Test
    void testDisabledBeans(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Beans").click();
        controlPanelDetails(page, "Disabled Beans").click();

        var searchBox = page.getByRole(AriaRole.TEXTBOX, new GetByRoleOptions().setName("Search disabled beans"));
        searchBox.click();
        searchBox.fill("jcache");

        assertThat(page.locator("tbody")).containsText("io.micronaut.cache.jcache.JCacheManager");
    }

    @Test
    void testLoggers(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Loggers").click();

        assertThat(body(page)).containsText("ROOT");
        assertThat(body(page)).containsText("example");
        assertThat(body(page)).containsText("io.micronaut.controlpanel");

        assertThat(page.getByRole(AriaRole.DEFINITION).nth(1)).containsText("INFO");

        page.locator("tbody tr")
            .filter(new Locator.FilterOptions().setHasText("ROOT"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Reconfigure"))
            .click();
        assertThat(page.locator("#modalLabel")).containsText("Reconfigure logger ROOT");

        page.locator("#actionsModal").getByLabel("Level:").selectOption("DEBUG");
        id(page, "submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Logger configured");

        page.locator("#actionsModal .modal-footer [data-dismiss='modal']").click();
        page.navigate(baseUrl() + "/loggers");
        assertThat(body(page)).containsText("DEBUG");

        page.locator("tbody tr")
            .filter(new Locator.FilterOptions().setHasText("ROOT"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Reconfigure"))
            .click();
        page.locator("#actionsModal").getByLabel("Level:").selectOption("INFO");
        id(page, "submit").click();
    }

    @Test
    void testCustomCategory(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "My Application").click();

        assertThat(body(page)).containsText("My Application Control Panel");
        assertThat(body(page)).containsText("This is the body of the application-provided control panel");

        page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Details")).click();
        assertThat(body(page)).containsText("This is an application-provided control panel. This text is coming from the body");
    }

    @Test
    void testRefresh(Page page) {
        page.navigate(baseUrl());
        id(page,"refreshButton").click();

        assertThat(page.locator("#stopRefreshModalLabel")).containsText("Confirm application refresh");
        id(page, "refreshConfirm").click();

        assertThat(page.locator("#globalAlertTitle")).containsText("Application refreshed");
        assertThat(page.locator("#globalAlertMessage")).containsText("All Refreshable beans have been recreated.");

        id(page,"refreshButton").click();
        id(page, "refreshForce").click();

        assertThat(page.locator("#globalAlertTitle")).containsText("Application refreshed");
        assertThat(page.locator("#globalAlertMessage")).containsText("All Refreshable beans have been recreated regardless of environment changes.");
    }

    @Test
    void testObjectStorage(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Object Storage").click();

        assertThat(body(page)).containsText("my-local");
        assertThat(body(page)).containsText("files stored.");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Details")).click();

        assertThat(page.getByRole(AriaRole.DEFINITION)).containsText("/tmp/foo");
    }

    @Test
    void testCache(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Cache").click();

        assertThat(body(page)).containsText("my-caffeine");
        assertThat(body(page)).containsText("my-ehcache");

        for (var text : body(page).getByText("objects in the cache.").all()) {
            assertThat(text).containsText("2 objects in the cache.");
        }

        var buttons = page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Details"));
        for (int index = 1; index < buttons.count() ; index++) {
            var button = buttons.nth(index);
            button.click();

            assertThat(page.locator("tbody")).containsText("foo");
            assertThat(body(page)).containsText("foo");
            assertThat(body(page)).containsText("counter");

            var fooRow = page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText("foo"))
                .first();
            fooRow.locator("[data-actions-menu] summary").click();
            fooRow.locator("[data-target='#invalidateConfirmModal']").click();
            id(page, "invalidateConfirm").click();

            assertThat(page.locator("#globalAlertTitle")).containsText("Success");
            assertThat(page.locator("#globalAlertMessage")).containsText("Object with key foo successfully removed from the cache");

            page.navigate(baseUrl());
            categoryLink(page, "Cache").click();
            button.click();

            assertThat(body(page)).not().containsText("foo");
            assertThat(body(page)).containsText("counter");

            page
                .locator(".card")
                .filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Entries"))))
                .locator(".card-tools [data-actions-menu] summary")
                .click();
            page.locator("[data-target='#invalidateAllConfirmModal']").click();
            id(page, "invalidateAllConfirm").click();

            assertThat(page.locator("#globalAlertTitle")).containsText("Success");
            assertThat(page.locator("#globalAlertMessage")).containsText("emptied successfully");
            assertThat(body(page)).not().containsText("foo");
            assertThat(body(page)).not().containsText("counter");
        }
    }

    @Test
    @DisabledInNativeImage
    void testDatasource(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Data Sources").click();

        assertThat(body(page)).containsText("my-oracle");
        assertThat(body(page)).containsText("my-postgres");

        controlPanelDetails(page, "my-oracle").click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show ER diagram")).click();
        assertThat(page.locator("#erDiagramDialog")).containsText("Entity Relationship Diagram");
        assertThat(page.locator("#mermaidErCode")).containsText("TEST_DEPT");
        assertThat(page.locator("#mermaidErCode")).containsText("TEST_EMP");

        page.locator("#erDiagramDialog button[aria-label='Close']").click();
        assertThat(page.locator("[data-schema-tree]")).containsText("DEPT");
        assertThat(page.locator("[data-schema-tree]")).containsText("EMP");

        page.locator("#sql-console").getByRole(AriaRole.TEXTBOX).fill("SELECT * FROM DEPT");
        id(page, "executeQuery").click();

        assertThat(page.locator("tbody")).containsText("ACCOUNTING");
    }

    @Test
    @DisabledInNativeImage
    void testKafka(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Kafka").click();
        controlPanelDetails(page, "Kafka").click();

        assertThat(page.locator("html").getByRole(AriaRole.DOCUMENT)).matchesAriaSnapshot("- document: \"Sub-topology: 2 Sub-topology: 1 Sub-topology: 0 KTABLE SELECT 0000000028 variant detail source variant detail source source variant stock source variant stock source source KSTREAM SINK 0000000030 KSTREAM KEY SELECT 0000000013 attribute source KTABLE JOINOTHER 0000000024 KSTREAM MAPVALUES 0000000038 product json sink KSTREAM FILTER 0000000017 KTABLE MERGE 0000000025 KTABLE JOINTHIS 0000000023 product sink description source description source source KSTREAM AGGREGATE STATE STORE 0000000014 KSTREAM AGGREGATE STATE STORE 0000000014 repartition KTABLE AGGREGATE STATE STORE 0000000029 repartition KTABLE AGGREGATE STATE STORE 0000000029 product_description_v1 STATE STORE 0000000000 product_description_v1 product_json_v1 product_v1 product_attribute_v3 product_variant_stock_v2 STATE STORE 0000000010 product_variant_detail_v1 STATE STORE 0000000004 product_variant_detail_v1 product_variant_stock_v2\"");
    }

    public static class HeadlessBrowserOptions implements OptionsFactory {
        @Override
        public Options getOptions() {
            if (System.getenv("CI") == null) {
                return new Options().setHeadless(false);
            } else {
                return new Options();
            }
        }
    }

}
