package example.e2e;

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
        assertThat(body).containsText("Micronaut Control Panel for my-application");
        assertThat(body).containsText("Application Health");
        assertThat(body).containsText("Environment Properties");
        assertThat(body).containsText("HTTP Routes");
        assertThat(body).containsText("Metrics");
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
        assertThat(body(page)).containsText("micronaut.control-panel.env.show-values = true");

        // Sensitive data is still masked
        assertThat(body(page)).containsText("test.password = *****");
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
        assertThat(body(page)).containsText("Micronaut Framework beans");
    }

    @Test
    void testDisabledBeans(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Beans").click();
        controlPanelDetails(page, "Disabled Beans").click();

        var searchBox = page.getByRole(AriaRole.SEARCHBOX, new GetByRoleOptions().setName("Search:"));
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

        page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName(nameRegex("Reconfigure"))).first().getByRole(AriaRole.BUTTON).click();
        assertThat(page.locator("#modalLabel")).containsText("Reconfigure logger ROOT");

        page.getByLabel("Level:").selectOption("DEBUG");
        button(page, "Submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Logger configured");

        page.getByText("Close").click();
        assertThat(page.getByRole(AriaRole.DEFINITION).nth(1)).containsText("DEBUG");

        page.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName(nameRegex("Reconfigure"))).first().getByRole(AriaRole.BUTTON).click();
        page.getByLabel("Level:").selectOption("INFO");
        button(page, "Submit").click();
    }

    @Test
    void testMetrics(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Metrics").click();

        assertThat(body(page)).containsText("example.orders.processed");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nameRegex("example.orders.processed"))).click();

        assertThat(body(page)).containsText("Processed example orders");
        assertThat(body(page)).containsText("COUNT");
        assertThat(body(page)).containsText("channel");
        assertThat(body(page)).containsText("status");

        page.getByLabel("channel").selectOption("web");
        button(page, "Apply filters").click();

        assertThat(body(page)).containsText("channel:web");
        assertThat(body(page)).containsText("COUNT");
        assertThat(body(page)).containsText("4");
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
        button(page, "Refresh").click();

        assertThat(page.locator("#globalAlertTitle")).containsText("Application refreshed");
        assertThat(page.locator("#globalAlertMessage")).containsText("All Refreshable beans have been recreated.");

        id(page,"refreshButton").click();
        button(page, "Force refresh").click();

        assertThat(page.locator("#globalAlertTitle")).containsText("Application refreshed");
        assertThat(page.locator("#globalAlertMessage")).containsText("All Refreshable beans have been recreated regardless of environment changes.");
    }

    @Test
    void testObjectStorage(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Object Storage").click();

        assertThat(body(page)).containsText("my-local");
        assertThat(body(page)).containsText("0 files stored.");

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

            page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName("foo bar Invalidate")).getByRole(AriaRole.BUTTON).click();
            id(page, "invalidateConfirm").click();

            assertThat(page.locator("#globalAlertTitle")).containsText("Success");
            assertThat(page.locator("#globalAlertMessage")).containsText("Object with key foo successfully removed from the cache");

            page.navigate(baseUrl());
            categoryLink(page, "Cache").click();
            button.click();

            assertThat(body(page)).not().containsText("foo");
            assertThat(body(page)).containsText("counter");

            button(page, "Invalidate all keys").click();
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

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Details")).first().click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show ER diagram")).click();
        assertThat(page.locator("#erDiagramContainer")).matchesAriaSnapshot("- document: /TEST_DEPT numeric\\(2\\) DEPTNO PK NOT NULL string\\(\\d+\\) DNAME string\\(\\d+\\) LOC TEST_EMP numeric\\(4\\) EMPNO PK NOT NULL string\\(\\d+\\) ENAME string\\(9\\) JOB numeric\\(4\\) MGR FK date HIREDATE numeric\\(7\\) SAL numeric\\(7\\) COMM numeric\\(2\\) DEPTNO FK FK_DEPTNO FK_EMPNO/");

        page.getByLabel("Close").click();
        assertThat(page.locator("#jstree")).matchesAriaSnapshot("- tree:\n  - treeitem \" DEPT\" [level=1]\n  - treeitem \" EMP\" [level=1]");

        page.getByRole(AriaRole.TEXTBOX).fill("SELECT * FROM DEPT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute query")).click();

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
            if (System.getenv("CI") == null && System.getenv("DISPLAY") != null) {
                return new Options().setHeadless(false);
            } else {
                return new Options();
            }
        }
    }

}
