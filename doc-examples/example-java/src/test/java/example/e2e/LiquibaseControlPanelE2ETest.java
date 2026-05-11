package example.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@UsePlaywright(LiquibaseControlPanelE2ETest.HeadlessBrowserOptions.class)
@MicronautTest(environments = "liquibase", rebuildContext = true)
@DisabledInNativeImage
@Property(name = "datasources.my-postgres.enabled", value = "false")
@Property(name = "jpa.default.enabled", value = "false")
@Property(name = "jpa.my-postgres.enabled", value = "false")
@Property(name = "jpa.hibernate-reporting.enabled", value = "false")
@Property(name = "jpa.default.properties.hibernate.cache.use_second_level_cache", value = "false")
@Property(name = "jpa.default.properties.hibernate.cache.use_query_cache", value = "false")
@Property(name = "jpa.zz-liquibase-history.enabled", value = "false")
@Property(name = "kafka.enabled", value = "false")
class LiquibaseControlPanelE2ETest extends AbstractE2ETest {

    @Test
    void testLiquibasePanelDesktopAndMobileEvidence(Page page) throws Exception {
        captureLiquibasePanel(page, 1440, 900, "liquibase-panel-1440x900.png");
        captureLiquibasePanel(page, 390, 844, "liquibase-panel-390x844.png");
    }

    private void captureLiquibasePanel(Page page, int width, int height, String fileName) throws Exception {
        page.setViewportSize(width, height);
        page.navigate(baseUrl() + "/liquibase");
        if (width >= 1000) {
            page.evaluate("document.body.classList.add('sidebar-collapse'); document.body.classList.remove('sidebar-open')");
        }

        assertThat(body(page)).containsText("zz-liquibase-history");
        assertThat(body(page)).containsText("Liquibase");
        assertThat(body(page)).containsText("001-create-liquibase-panel-table");
        assertThat(body(page)).containsText("control-panel");
        assertThat(body(page)).containsText("panel-example");
        assertThat(body(page)).containsText("disabled-history");
        assertThat(body(page)).containsText("Liquibase configuration disabled-history is disabled.");

        Path screenshot = Path.of("build", "reports", "tests", "liquibase", fileName);
        Files.createDirectories(screenshot.getParent());
        page.screenshot(new Page.ScreenshotOptions()
            .setPath(screenshot)
            .setFullPage(true));
    }

    public static class HeadlessBrowserOptions implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options().setHeadless(true);
        }
    }
}
