package example.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@UsePlaywright
@MicronautTest
class ControlPanelShutdownE2ETest extends AbstractE2ETest {

    @Test
    void testStop(Page page) {
        page.navigate(baseUrl());
        id(page,"stopButton").click();

        assertThat(page.locator("#stopRefreshModalLabel")).containsText("Confirm application shutdown");
        button(page, "Shutdown").click();

        assertThat(page.locator("#globalAlertTitle")).containsText("Application is shutting down");
        assertThat(page.locator("#globalAlertMessage")).containsText("The application is shutting down. Please wait for the server to stop.");
    }

}
