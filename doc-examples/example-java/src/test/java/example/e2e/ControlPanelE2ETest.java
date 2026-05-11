package example.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@UsePlaywright(ControlPanelBrowserOptions.class)
@MicronautTest(environments = {"hibernate", "kafka", "oracle"})
@Property(name = "kafka.streams.default.state.dir", value = "build/tmp/kafka-streams-e2e")
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
        assertThat(body).containsText("Metrics");
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
        assertThat(page.locator("#actionsModal")).isVisible();
        assertThat(page.locator("#modalLabel")).containsText("Reconfigure logger ROOT");

        var loggerInput = page.locator("#actionsModal").getByLabel("Logger name:");
        loggerInput.fill(" ");
        id(page, "submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Enter a logger name");
        assertThat(loggerInput).hasAttribute("aria-invalid", "true");

        loggerInput.fill("ROOT");
        page.locator("#actionsModal").getByLabel("Level:").selectOption("DEBUG");
        id(page, "submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Logger configured through the Control Panel route.");
        assertThat(page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Refresh table"))).isVisible();

        page.locator("#actionsModal .modal-footer [data-dismiss='modal']").click();
        assertThat(body(page)).containsText("DEBUG");
        assertTrue((Boolean) page.evaluate("document.activeElement && document.activeElement.dataset.logger === 'ROOT'"));

        page.locator("tbody tr")
            .filter(new Locator.FilterOptions().setHasText("ROOT"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Reconfigure"))
            .click();
        page.locator("#actionsModal").getByLabel("Level:").selectOption("INFO");
        id(page, "submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Logger configured through the Control Panel route.");
        page.locator("#actionsModal .modal-footer [data-dismiss='modal']").click();

        page.route("**/control-panel/loggers-control-panel-controller/**", route -> route.fulfill(new Route.FulfillOptions()
            .setStatus(403)
            .setContentType("application/json")
            .setBody("{\"message\":\"Forbidden\"}")));
        page.locator("tbody tr")
            .filter(new Locator.FilterOptions().setHasText("ROOT"))
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Reconfigure"))
            .click();
        page.locator("#actionsModal").getByLabel("Level:").selectOption("DEBUG");
        id(page, "submit").click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("You do not have permission to reconfigure loggers through the Control Panel.");
        assertThat(id(page, "submit")).isDisabled();
    }

    @Test
    void testMetrics(Page page) {
        page.navigate(baseUrl());
        controlPanelDetails(page, "Metrics").click();

        assertThat(body(page)).containsText("control.panel.demo.requests");
        assertThat(body(page)).containsText("Rows per page");
        assertThat(body(page)).containsText("Showing 1-12");
        page.getByLabel("Search metrics").fill("control.panel.demo.requests");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("control.panel.demo.requests")).click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("control.panel.demo.requests"))).isVisible();
        assertThat(body(page)).containsText("Deterministic demo requests");
        assertThat(body(page)).containsText("success:demo");

        page.getByLabel("outcome").selectOption("outcome:success:demo");
        assertThat(page.locator("#metricMeasurements")).containsText("COUNT");
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
            assertThat(page.locator("#invalidateConfirmModal")).isVisible();
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
            assertThat(page.locator("#invalidateAllConfirmModal")).isVisible();
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

        assertDatasourceList(page);
        controlPanelDetails(page, "my-oracle").click();
        assertDatasourceTablesInitialState(page);

        String schema = filterDatasourceTables(page, "dept");
        String expectedTableDetailSubtitle = expectedTableSubtitle(schema, "DEPT");
        selectTable(page, "DEPT");
        assertDepartmentTableDetail(page, expectedTableDetailSubtitle);

        assertSelectedTableDeselects(page);
        assertTableDetailToggle(page);
        assertTablesListToggleDisabled(page);
        assertRelationshipNavigation(page, schema, expectedTableDetailSubtitle);

        executeSelectAllTableQuery(page);
        assertTablesTabAfterBrowserBack(page);
        executeDisplayTableAsJson(page);
        assertTablesTabAfterBrowserBack(page);
        assertDatasourcePoolTab(page);
    }

    private static void assertDatasourceList(Page page) {
        assertThat(body(page)).containsText("my-oracle");
        assertThat(body(page)).containsText("my-postgres");
    }

    private static void assertDatasourceTablesInitialState(Page page) {
        assertThat(page.locator("#tablesPageContainer")).containsText("DEPT");
        assertThat(page.locator("#tablesPageContainer")).containsText("EMP");
        assertThat(page.locator("#tableDetailCard")).hasClass(Pattern.compile(".*collapsed-card.*"));
        assertThat(page.locator("#tableDetailCard")).hasClass(Pattern.compile(".*detail-rail-collapsed.*"));
    }

    private static String filterDatasourceTables(Page page, String search) {
        String schema = page.locator("[data-table-schema-filter] option:not([value='__all__'])").first().getAttribute("value");
        page.locator("[data-table-schema-filter]").selectOption(schema);
        page.locator("[data-table-search]").fill(search);
        assertThat(page.locator("#tablesPageContainer")).containsText("DEPT");
        assertThat(page.locator("#tablesPageContainer")).not().containsText("EMP");
        return schema;
    }

    private static void assertDepartmentTableDetail(Page page, String expectedTableDetailSubtitle) {
        assertThat(page.locator("#tableDetailCard")).not().hasClass(Pattern.compile(".*collapsed-card.*"));
        assertThat(page.locator("#tableDetailSubtitle")).containsText(expectedTableDetailSubtitle);
        assertThat(page.locator("#tableDetailContainer")).containsText("Entity Relationship Diagram");
        assertThat(page.locator("#tableErDiagramCode")).containsText("TEST_DEPT");
        assertThat(page.locator(".cp-datasource-table-browser")).not().hasClass(Pattern.compile(".*tables-collapsed.*"));
    }

    private static void assertSelectedTableDeselects(Page page) {
        selectTable(page, "DEPT");
        assertThat(page.locator("#tableDetailCard")).hasClass(Pattern.compile(".*collapsed-card.*"));
        assertThat(page.locator("#tableDetailCard")).hasClass(Pattern.compile(".*detail-rail-collapsed.*"));
        selectTable(page, "DEPT");
        assertThat(page.locator("#tableDetailCard")).not().hasClass(Pattern.compile(".*collapsed-card.*"));
        assertThat(page.locator(".cp-datasource-table-browser")).not().hasClass(Pattern.compile(".*tables-collapsed.*"));
    }

    private static void assertTableDetailToggle(Page page) {
        page.locator("#toggleTableDetail").click();
        assertThat(page.locator("#tableDetailCard")).hasClass(Pattern.compile(".*collapsed-card.*"));
        assertThat(page.locator("#tableDetailCard")).hasClass(Pattern.compile(".*detail-rail-collapsed.*"));
        assertThat(page.locator(".cp-datasource-table-browser")).hasClass(Pattern.compile(".*detail-collapsed.*"));
        page.locator("#toggleTableDetail").click();
        assertThat(page.locator("#tableDetailCard")).not().hasClass(Pattern.compile(".*collapsed-card.*"));
        assertThat(page.locator("#tableDetailCard")).not().hasClass(Pattern.compile(".*detail-rail-collapsed.*"));
        assertThat(page.locator(".cp-datasource-table-browser")).not().hasClass(Pattern.compile(".*detail-collapsed.*"));
    }

    private static void assertTablesListToggleDisabled(Page page) {
        page.locator("#toggleTablesList").click();
        assertThat(page.locator(".cp-datasource-table-browser")).not().hasClass(Pattern.compile(".*tables-collapsed.*"));
    }

    private static void assertRelationshipNavigation(Page page, String schema, String expectedTableDetailSubtitle) {
        page.locator("[data-table-search]").fill("emp");
        assertThat(page.locator("#tablesPageContainer")).containsText("EMP");
        selectTable(page, "EMP");
        assertThat(page.locator("#tableDetailSubtitle")).containsText(expectedTableSubtitle(schema, "EMP"));
        page.locator("#tableDetailContainer [data-table-detail-link][data-table-name='DEPT']").first().click();
        assertThat(page.locator("#tableDetailSubtitle")).containsText(expectedTableDetailSubtitle);
    }

    private static void executeSelectAllTableQuery(Page page) {
        page.locator("#tableDetailActions > summary").click();
        page.locator("#selectAllTableQuery").click();
        assertThat(page.locator("#sql-console").getByRole(AriaRole.TEXTBOX)).isVisible();
        executeQueryShortcut(page);
        assertThat(page.locator("#queryResultsContainer tbody")).containsText("ACCOUNTING");
    }

    private static void assertTablesTabAfterBrowserBack(Page page) {
        page.evaluate("history.back()");
        assertThat(page.locator("#datasourceTablesTab")).isVisible();
        assertThat(page.locator("#tablesPageContainer")).containsText("EMP");
    }

    private static void executeDisplayTableAsJson(Page page) {
        page.locator("#tableDetailActions > summary").click();
        page.locator("#displayTableAsJson").click();
        assertThat(page.locator("#datasourceQueryTab")).isVisible();
        assertThat(page.locator("#sql-console").getByRole(AriaRole.TEXTBOX)).containsText("JSON_OBJECT");
        executeQueryShortcut(page);

        var jsonCell = page.locator("#queryResultsContainer [data-query-json-cell]").first();
        assertThat(jsonCell).isVisible();
        assertThat(jsonCell.locator("[data-query-json-raw]")).containsText("ACCOUNTING");
        jsonCell.click();
        assertThat(jsonCell).hasClass(Pattern.compile(".*cp-query-json-cell-formatted.*"));
        assertTrue(jsonCell.locator("code").textContent().contains("\n  \"DNAME\""));
        jsonCell.click();
        assertThat(jsonCell).not().hasClass(Pattern.compile(".*cp-query-json-cell-formatted.*"));
        assertThat(jsonCell.locator("[data-query-json-raw]")).containsText("ACCOUNTING");
    }

    private static void assertDatasourcePoolTab(Page page) {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Pool").setExact(true)).click();
        assertThat(page.locator("#datasourcePoolTab")).isVisible();
        assertThat(page.locator("#poolStatusCardContainer")).containsText("HikariCP");
        assertThat(page.locator("#poolStatusCardContainer")).containsText("example-oracle-pool");
        assertThat(page.locator("#poolStatusCardContainer")).containsText("Connection status");
        assertThat(page.locator("#poolStatusCardContainer")).containsText("Active");
        assertThat(page.locator("#poolStatusCardContainer")).containsText("Idle");
        assertThat(page.locator("#poolStatusCardContainer")).containsText("Waiting");
        assertThat(page.locator("#datasourcePoolTab")).containsText("Pool options");
        assertThat(page.locator("#datasourcePoolTab")).containsText("Maximum pool size");
        assertThat(page.locator("#datasourcePoolTab")).containsText("5");
        assertThat(page.locator("#datasourcePoolTab")).containsText("Connection test query");
        assertThat(page.locator("#datasourcePoolTab")).containsText("SELECT 1 FROM DUAL");
        page.locator("#poolStatusCardContainer [data-pool-status-refresh]").click();
        assertThat(page.locator("#poolStatusCardContainer")).containsText("Connection status");
    }

    private static void selectTable(Page page, String tableName) {
        page.locator("#tablesPageContainer [data-table-row][data-table-name='" + tableName + "']").click();
    }

    private static String expectedTableSubtitle(String schema, String tableName) {
        return schema == null || schema.isBlank() ? tableName : schema + "." + tableName;
    }

    @Test
    @DisabledInNativeImage
    void testKafka(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Kafka").click();
        controlPanelDetails(page, "Kafka").click();

        assertThat(page.locator("html").getByRole(AriaRole.DOCUMENT)).matchesAriaSnapshot("- document: \"Sub-topology: 2 Sub-topology: 1 Sub-topology: 0 KTABLE SELECT 0000000028 variant detail source variant detail source source variant stock source variant stock source source KSTREAM SINK 0000000030 KSTREAM KEY SELECT 0000000013 attribute source KTABLE JOINOTHER 0000000024 KSTREAM MAPVALUES 0000000038 product json sink KSTREAM FILTER 0000000017 KTABLE MERGE 0000000025 KTABLE JOINTHIS 0000000023 product sink description source description source source KSTREAM AGGREGATE STATE STORE 0000000014 KSTREAM AGGREGATE STATE STORE 0000000014 repartition KTABLE AGGREGATE STATE STORE 0000000029 repartition KTABLE AGGREGATE STATE STORE 0000000029 product_description_v1 STATE STORE 0000000000 product_description_v1 product_json_v1 product_v1 product_attribute_v3 product_variant_stock_v2 STATE STORE 0000000010 product_variant_detail_v1 STATE STORE 0000000004 product_variant_detail_v1 product_variant_stock_v2\"");
    }

    @Test
    @DisabledInNativeImage
    void testHibernate(Page page) {
        openHibernateCategory(page);
        assertHibernateOverview(page);
        openPostgresHibernateDetail(page);
        assertHibernateStatisticsDisabledState(page);

        fillPostgresHibernateStatistics(page);
        openHibernateCategory(page);
        openPostgresHibernateDetail(page);

        assertHibernatePerformanceTab(page);
        assertHibernateSessionDetailsTab(page);
        assertHibernateDataSourceTab(page);
        assertHibernateCacheTab(page);
        assertHibernateEntityTypesTab(page);
        assertHibernateNamedQueriesTab(page);
        assertHibernateHqlConsole(page);
        assertHibernateQueryStatisticsTab(page);
        assertHibernateEntityEvictionActions(page);
        assertHibernateCacheEvictionActions(page);
        assertHibernateStatisticsClearAndDisable(page);
    }

    private void openHibernateCategory(Page page) {
        page.navigate(baseUrl());
        categoryLink(page, "Hibernate").click();
    }

    private static void openPostgresHibernateDetail(Page page) {
        controlPanelDetails(page, "my-postgres").click();
        page.waitForLoadState();
    }

    private static void assertHibernateOverview(Page page) {
        assertThat(body(page)).containsText("my-postgres");
        assertThat(body(page)).containsText("hibernate-reporting");
        assertThat(body(page)).containsText("2 entities");
        assertThat(body(page)).containsText("1 collection roles");
    }

    private static void assertHibernateStatisticsDisabledState(Page page) {
        openHibernateTab(page, "Session statistics");
        assertHibernateStatisticsToggle(page, false);
        assertThat(hibernateCard(page, "Session statistics")).containsText("No runtime statistics recorded while statistics collection is disabled.");
        openHibernateTab(page, "Performance");
        assertThat(hibernateCard(page, "Common performance metrics")).containsText("Enable statistics to display common performance metrics to monitor.");
        assertThat(hibernateCard(page, "Common performance metrics")).not().containsText("Query executions");
        openHibernateTab(page, "Query statistics");
        assertThat(hibernateCard(page, "Query statistics")).containsText("Enable statistics to record Hibernate query executions.");
        openHibernateTab(page, "Cache");
        assertThat(hibernateCard(page, "Cache")).containsText("Enable statistics to see cache hits, misses, puts, elements, and memory size.");
        openHibernateTab(page, "Session details");
        assertThat(hibernateCard(page, "Session details")).containsText("Shows the selected Hibernate persistence unit");
        assertThat(hibernateTableRow(page, "Session details", "Second-level cache")).containsText("true");
        assertThat(hibernateSessionDetailRow(page, "Statistics", "false")).isVisible();
    }

    private void fillPostgresHibernateStatistics(Page page) {
        page.navigate(server.getURL() + "/hibernate-demo/my-postgres/statistics/fill?runs=100");
        assertThat(body(page)).containsText("hibernateSessionFactory");
        assertThat(body(page)).containsText("datasource");
        assertThat(body(page)).containsText("my-postgres");
        assertThat(body(page)).containsText(Pattern.compile("\"runs\"\\s*:\\s*100"));
        assertThat(body(page)).containsText("statisticsEnabled");
        assertThat(body(page)).containsText("statisticsEnabledByEndpoint");
        assertThat(body(page)).containsText("cacheWarmupAuthors");
        assertThat(body(page)).containsText("cacheWarmupBooks");
        assertThat(body(page)).containsText("cacheWarmupCollectionItems");
        assertThat(body(page)).containsText("collectionItemsRead");
        assertThat(body(page)).containsText("entityLoads");
        assertThat(body(page)).containsText("nativeRowsRead");
        assertThat(body(page)).containsText("Native SQL");
        assertThat(body(page)).containsText("rowsUpdated");
    }

    private static void assertHibernatePerformanceTab(Page page) {
        openHibernateTab(page, "Performance");
        assertThat(hibernateCard(page, "Common performance metrics")).containsText("Query executions");
        assertThat(hibernateCard(page, "Common performance metrics")).containsText("Second-level cache hits / misses");
        assertThat(hibernateCard(page, "Common performance metrics")).containsText("Slowest query time");
        assertMetricRecorded(page, "Common performance metrics", "Query executions");
        assertMetricRecorded(page, "Common performance metrics", "Second-level cache hits / misses");
        assertThat(body(page)).containsText("Session statistics");
        openHibernateTab(page, "Session statistics");
        assertMetricRecorded(page, "Session statistics", "Query executions");
        assertMetricRecorded(page, "Session statistics", "Query cache hits");
        assertMetricRecorded(page, "Session statistics", "Second-level cache hits");
        assertHibernateTableFitsPage(page, "Session statistics", false);
    }

    private static void assertHibernateSessionDetailsTab(Page page) {
        openHibernateTab(page, "Session details");
        assertThat(hibernateCard(page, "Session details")).containsText("Shows the selected Hibernate persistence unit");
        assertThat(hibernateTableRow(page, "Session details", "Second-level cache")).containsText("true");
        assertThat(hibernateTableRow(page, "Session details", "Query cache")).containsText("true");
        assertThat(hibernateSessionDetailRow(page, "Statistics", "true")).isVisible();
        assertThat(hibernateTableRow(page, "Session details", "hibernate.default_batch_fetch_size")).isVisible();
        assertThat(hibernateTableRow(page, "Session details", "hibernate.jdbc.batch_size")).isVisible();
        assertThat(hibernateTableRow(page, "Session details", "hibernate.cache.use_minimal_puts")).isVisible();
    }

    private static void assertHibernateDataSourceTab(Page page) {
        openHibernateTab(page, "JDBC Data Source");
        assertThat(hibernateCard(page, "JDBC Data Source")).containsText("Shows the JDBC connection");
        assertThat(hibernateCard(page, "JDBC Data Source")).containsText("JDBC URL");
        assertThat(hibernateCard(page, "JDBC Data Source")).containsText("Supports batch updates");
    }

    private static void assertHibernateCacheTab(Page page) {
        openHibernateTab(page, "Cache");
        assertThat(page.locator("[data-tabs-panel=\"cache\"] > .cp-dashboard-stack > .card").first()).containsText("Shows Hibernate second-level and query cache regions");
        assertThat(hibernateCard(page, "Elements in memory")).containsText("Provider-reported entries");
        assertThat(hibernateCard(page, "Size in memory")).containsText("Provider-reported memory usage");
        assertThat(hibernateCard(page, "Cache")).containsText("Shows Hibernate second-level and query cache regions");
        assertThat(hibernateTableRow(page, "Cache", "example.HibernateBook")).containsText(Pattern.compile("example\\.HibernateBook\\s+[0-9]+\\s+[0-9]+\\s+[1-9][0-9]*"));
    }

    private static void assertHibernateEntityTypesTab(Page page) {
        openHibernateTab(page, "Entity types");
        assertThat(hibernateCard(page, "Entities")).containsText("Shows mapped Hibernate entity types");
        assertThat(hibernateCard(page, "Collections")).containsText("Shows persistent association roles");
        assertThat(body(page)).containsText("example.HibernateAuthor");
        assertThat(body(page)).containsText("example.HibernateBook");
        assertThat(body(page)).containsText("example.HibernateAuthor.books");
        var bookEntityRow = hibernateTableRow(page, "Entities", "example.HibernateBook");
        assertThat(bookEntityRow).containsText("java.lang.Long");
        assertThat(bookEntityRow.locator(".cp-hibernate-field-count > .badge")).containsText("6");
        assertThat(bookEntityRow.locator("td").nth(4)).containsText(Pattern.compile("[1-9][0-9]*"));
        var authorBooksCollectionRow = hibernateTableRow(page, "Collections", "example.HibernateAuthor.books");
        assertThat(authorBooksCollectionRow.locator("td").nth(1)).containsText(Pattern.compile("[1-9][0-9]*"));
        assertHibernateEntityFieldHover(bookEntityRow);
        assertHibernateEntityProperties(page);
        assertHibernateEntityQueryActions(page);
    }

    private static void assertHibernateEntityFieldHover(Locator bookEntityRow) {
        var fieldDetails = bookEntityRow.locator(".cp-hibernate-field-count");
        fieldDetails.hover();
        assertThat(fieldDetails.locator(".cp-hover-card-content")).containsText("title");
        assertThat(fieldDetails.locator(".cp-hover-card-content")).containsText("java.lang.String");
    }

    private static void assertHibernateEntityProperties(Page page) {
        hibernateRowAction(page, "Entities", "example.HibernateBook", "Properties");
        var propertiesModal = page.locator("[id^=\"hibernateEntityPropertiesModal\"].show");
        assertThat(propertiesModal).isVisible();
        assertThat(propertiesModal).containsText("Property definitions");
        assertThat(propertiesModal).containsText("example.HibernateBook");
        assertThat(propertiesModal).containsText("publishedYear");
        assertThat(propertiesModal).containsText("java.lang.String");
        assertThat(propertiesModal).containsText("MANY_TO_ONE");
        propertiesModal.locator(".modal-footer [data-dismiss='modal']").click();
        assertThat(propertiesModal).isHidden();
    }

    private static void assertHibernateEntityQueryActions(Page page) {
        hibernateRowAction(page, "Entities", "example.HibernateBook", "Query");
        assertHibernateTabSelected(page, "HQL Console");
        assertThat(hibernateCard(page, "HQL results")).containsText("example.HibernateBook#");
        openHibernateTab(page, "Entity types");
        hibernateRowAction(page, "Collections", "example.HibernateAuthor.books", "Query");
        assertHibernateTabSelected(page, "HQL Console");
        assertThat(hibernateCard(page, "HQL results")).containsText("example.HibernateAuthor#");
        openHibernateTab(page, "Entity types");
    }

    private static void assertHibernateNamedQueriesTab(Page page) {
        openHibernateTab(page, "Named queries");
        assertThat(hibernateCard(page, "Named queries")).containsText("Shows named HQL");
        assertThat(hibernateCard(page, "Named queries")).containsText("HibernateBook.listTitles");
        assertThat(hibernateCard(page, "Named queries")).containsText("HibernateBook.recentBooks");
        assertThat(hibernateCard(page, "Named queries")).containsText("HibernateBook.nativeBookSummary");
        assertThat(hibernateTableRow(page, "Named queries", "HibernateBook.nativeBookSummary")).containsText("Native SQL");
        assertThat(hibernateTableRow(page, "Named queries", "HibernateBook.nativeBookSummary")).containsText("select title, pages from hibernatebook order by title");
    }

    private static void assertHibernateHqlConsole(Page page) {
        openHibernateTab(page, "HQL Console");
        page.locator("#hqlQueryText").fill("select b.title, b.publishedYear from HibernateBook b order by b.title");
        executeQueryShortcut(page);
        assertThat(hibernateCard(page, "HQL results")).containsText("Beloved Hibernate");
        assertThat(hibernateCard(page, "HQL results")).containsText("Column 1");
        assertThat(hibernateCard(page, "HQL results")).containsText("Column 2");
        page.locator("#hqlQueryText").fill("from HibernateBook b order by b.title");
        page.locator("#executeHqlQuery").click();
        var entityCell = hibernateCard(page, "HQL results").locator("[data-hover-card]").first();
        assertThat(entityCell).containsText("example.HibernateBook#");
        entityCell.hover();
        assertThat(entityCell.locator(".cp-hover-card-content")).isVisible();
        assertThat(entityCell.locator(".cp-hover-card-content")).containsText("title");
        assertThat(entityCell.locator(".cp-hover-card-content")).containsText("publishedYear");
    }

    private static void assertHibernateQueryStatisticsTab(Page page) {
        openHibernateTab(page, "Query statistics");
        assertThat(body(page)).containsText("select count(b) from HibernateBook b");
        assertThat(body(page)).containsText("hibernatebook");
        assertThat(body(page)).containsText("hibernateDemo.recentBooks");
        assertThat(body(page)).containsText("hibernateDemo.countrySummary");
        assertThat(hibernateCard(page, "Query statistics")).containsText("Shows recorded query statistics");
        assertThat(hibernateCard(page, "Query statistics").locator("[data-filter-table-page]")).isVisible();
        assertHibernateTableFitsPage(page, "Query statistics", true);
        assertThat(hibernateTableRow(page, "Query statistics", "from HibernateBook b").first()).containsText(Pattern.compile("from HibernateBook b[\\s\\S]*[1-9][0-9]*"));
        assertThat(hibernateTableRow(page, "Query statistics", "from HibernateBook b").first().locator("code.language-sql")).containsText("from HibernateBook b");
        openHibernateTab(page, "Session statistics");
        assertHibernateStatisticsToggle(page, true);
    }

    private static void assertHibernateEntityEvictionActions(Page page) {
        openHibernateTab(page, "Entity types");
        hibernateRowAction(page, "Entities", "example.HibernateBook", "Evict");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).isVisible();
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("Evict entity cache");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("It does not delete database rows.");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("example.HibernateBook");
        page.locator("#hibernateDeleteConfirm").click();
        assertGlobalAlert(page, "Entity cache evicted");
        openHibernateTab(page, "Entity types");
        assertThat(body(page)).containsText("example.HibernateBook");

        hibernateRowAction(page, "Collections", "example.HibernateAuthor.books", "Evict");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).isVisible();
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("Evict collection cache");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("It does not delete related entities or rows.");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("example.HibernateAuthor.books");
        page.locator("#hibernateDeleteConfirm").click();
        assertGlobalAlert(page, "Collection cache evicted");
        openHibernateTab(page, "Entity types");
        assertThat(body(page)).containsText("example.HibernateAuthor.books");
    }

    private static void assertHibernateCacheEvictionActions(Page page) {
        openHibernateTab(page, "Cache");
        assertHibernateCacheAction(page, "Evict all regions", "Evict all cache regions", "Cache evicted");
        assertHibernateCacheAction(page, "Evict default query region", "Evict default query region", "Default query region evicted");
        assertHibernateCacheAction(page, "Evict query regions", "Evict query regions", "Query regions evicted");

        hibernateRowAction(page, "Cache", "example.HibernateBook", "Evict");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).isVisible();
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("Evict cache region");
        page.locator("#hibernateDeleteConfirm").click();
        assertGlobalAlert(page, "Region evicted");
        assertHibernateTabSelected(page, "Cache");
    }

    private static void assertHibernateCacheAction(Page page, String action, String modalText, String alertText) {
        hibernateAction(page, "Cache", action);
        assertThat(page.locator("#hibernateDeleteConfirmModal")).isVisible();
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText(modalText);
        page.locator("#hibernateDeleteConfirm").click();
        assertGlobalAlert(page, alertText);
        assertHibernateTabSelected(page, "Cache");
    }

    private static void assertHibernateStatisticsClearAndDisable(Page page) {
        openHibernateTab(page, "Session statistics");
        hibernateAction(page, "Session statistics", "Clear statistics");
        assertThat(page.locator("#hibernateDeleteConfirmModal")).isVisible();
        assertThat(page.locator("#hibernateDeleteConfirmModal")).containsText("Clear statistics");
        page.locator("#hibernateDeleteConfirm").click();
        assertGlobalAlert(page, "Statistics cleared");
        openHibernateTab(page, "Query statistics");
        assertThat(body(page)).containsText("No query statistics recorded.");

        openHibernateTab(page, "Session statistics");
        hibernateStatisticsToggle(page).click();
        page.waitForFunction("() => document.querySelector('[data-hibernate-statistics-toggle]')?.getAttribute('aria-checked') === 'false'");
        assertThat(page.locator("#globalAlert")).isHidden();
        assertHibernateStatisticsToggle(page, false);
        openHibernateTab(page, "Performance");
        assertThat(hibernateCard(page, "Common performance metrics")).containsText("Enable statistics to display common performance metrics to monitor.");
        openHibernateTab(page, "Session details");
        assertThat(hibernateCard(page, "Session details")).containsText("Shows the selected Hibernate persistence unit");
    }

    @Test
    @DisabledInNativeImage
    void testHibernateReportingWithoutRecordedStatistics(Page page) {
        page.navigate(baseUrl() + "/hibernate-hibernate-reporting");
        page.waitForLoadState();

        openHibernateTab(page, "Session statistics");
        assertHibernateStatisticsToggle(page, false);
        openHibernateTab(page, "Performance");
        assertThat(hibernateCard(page, "Common performance metrics")).containsText("Enable statistics to display common performance metrics to monitor.");
        openHibernateTab(page, "Session details");
        assertThat(hibernateCard(page, "Session details")).containsText("Shows the selected Hibernate persistence unit");
    }

    private static Locator hibernateCard(Page page, String title) {
        var cardId = hibernateCardId(title);
        if (cardId != null) {
            return page.locator("[data-hibernate-card=\"" + cardId + "\"]");
        }
        return page
            .locator(".card")
            .filter(
                new Locator.FilterOptions()
                    .setHas(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(title).setExact(true)))
            );
    }

    private static String hibernateCardId(String title) {
        return switch (title) {
            case "Session details" -> "session-details";
            case "Statistics collection" -> "statistics-collection";
            case "Common performance metrics" -> "performance";
            case "Session statistics" -> "session-statistics";
            case "Cache" -> "cache";
            case "JDBC Data Source" -> "data-sources";
            case "Entities" -> "entities";
            case "Named queries" -> "named-queries";
            case "HQL Console" -> "hql-console";
            case "HQL results" -> "hql-results";
            case "Query statistics" -> "query-statistics";
            default -> null;
        };
    }

    private static void openHibernateTab(Page page, String tabName) {
        if (isHibernateStatisticsTab(tabName)) {
            page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Statistics").setExact(true)).click();
        }
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabName).setExact(true)).click();
    }

    private static boolean isHibernateStatisticsTab(String tabName) {
        return tabName.equals("Performance") || tabName.equals("Session statistics") || tabName.equals("Query statistics");
    }

    private static void executeQueryShortcut(Page page) {
        page.keyboard().press("Control+Enter");
    }

    private static void assertHibernateTabSelected(Page page, String tabName) {
        var tab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabName).setExact(true));
        assertEquals("true", tab.getAttribute("aria-selected"));
    }

    private static Locator hibernateTableRow(Page page, String cardTitle, String rowText) {
        return hibernateCard(page, cardTitle)
            .locator("tbody tr")
            .filter(new Locator.FilterOptions().setHasText(rowText));
    }

    private static Locator hibernateSessionDetailRow(Page page, String label, String value) {
        return hibernateCard(page, "Session details")
            .getByRole(AriaRole.ROW, new Locator.GetByRoleOptions().setName(label + " " + value).setExact(true));
    }

    private static void openHibernateActions(Page page, String cardTitle) {
        hibernateCard(page, cardTitle)
            .locator(".card-header [data-actions-menu] > summary")
            .click();
    }

    private static void hibernateAction(Page page, String cardTitle, String actionName) {
        openHibernateActions(page, cardTitle);
        button(page, actionName).click();
    }

    private static Locator hibernateStatisticsToggle(Page page) {
        return hibernateCard(page, "Statistics collection").locator("[data-hibernate-statistics-toggle]");
    }

    private static void assertHibernateStatisticsToggle(Page page, boolean enabled) {
        var toggle = hibernateStatisticsToggle(page);
        assertThat(toggle).isVisible();
        assertEquals(Boolean.toString(enabled), toggle.getAttribute("aria-checked"));
    }

    private static void assertHibernateTableFitsPage(Page page, String cardTitle, boolean requireInternalScroll) {
        var fitsPage = (Boolean) hibernateCard(page, cardTitle).evaluate("""
            (card, requireInternalScroll) => {
                const tableContainer = card.querySelector(".cp-data-table-container");
                if (!tableContainer) {
                    return false;
                }
                const cardRect = card.getBoundingClientRect();
                const tableRect = tableContainer.getBoundingClientRect();
                const fits = cardRect.bottom <= window.innerHeight + 1
                    && tableRect.bottom <= cardRect.bottom + 1;
                return requireInternalScroll ? fits && tableContainer.clientHeight < tableContainer.scrollHeight : fits;
            }
            """, requireInternalScroll);
        assertTrue(fitsPage, cardTitle + " table should fit inside the viewport");
    }

    private static void hibernateRowAction(Page page, String cardTitle, String rowText, String actionName) {
        var row = hibernateTableRow(page, cardTitle, rowText);
        row.locator("[data-actions-menu] > summary").click();
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(actionName).setExact(true)).click();
    }

    private static void assertMetricRecorded(Page page, String cardTitle, String metric) {
        assertThat(hibernateTableRow(page, cardTitle, metric)).containsText(Pattern.compile(metric + "[\\s\\S]*[1-9][0-9]*"));
    }

    private static void assertGlobalAlert(Page page, String title) {
        page.waitForFunction("expected => document.querySelector('#globalAlertTitle')?.textContent?.includes(expected)", title);
        assertThat(page.locator("#globalAlertTitle")).containsText(title);
    }

}
