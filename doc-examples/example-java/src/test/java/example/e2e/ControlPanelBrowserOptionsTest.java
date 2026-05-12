package example.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPanelBrowserOptionsTest {
    private final String originalHeadless = System.getProperty(ControlPanelBrowserOptions.HEADLESS_PROPERTY);

    @AfterEach
    void restoreProperty() {
        if (originalHeadless == null) {
            System.clearProperty(ControlPanelBrowserOptions.HEADLESS_PROPERTY);
        } else {
            System.setProperty(ControlPanelBrowserOptions.HEADLESS_PROPERTY, originalHeadless);
        }
    }

    @Test
    void enablesHeadlessBrowserFromSystemProperty() {
        System.setProperty(ControlPanelBrowserOptions.HEADLESS_PROPERTY, "true");

        assertTrue(new ControlPanelBrowserOptions().getOptions().headless);
    }

    @Test
    void disablesHeadlessBrowserFromSystemProperty() {
        System.setProperty(ControlPanelBrowserOptions.HEADLESS_PROPERTY, "false");

        assertFalse(new ControlPanelBrowserOptions().getOptions().headless);
    }
}
