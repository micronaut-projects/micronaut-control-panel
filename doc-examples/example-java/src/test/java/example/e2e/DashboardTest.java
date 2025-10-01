package example.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@UsePlaywright
@MicronautTest
class DashboardTest {

    @Inject
    EmbeddedServer server;

    @Test
    void testDashboard(Page page) {
        page.navigate(getUrl());
        assertThat(page.locator("body")).containsText("Micronaut Control Panel for my-application");
    }

    private String getUrl() {
        return "%s://%s:%s%s".formatted(server.getScheme(), server.getHost(), server.getPort(), ControlPanelModuleConfiguration.DEFAULT_PATH);
    }
}
