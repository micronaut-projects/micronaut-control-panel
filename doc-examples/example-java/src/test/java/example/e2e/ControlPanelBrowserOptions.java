package example.e2e;

import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;

public final class ControlPanelBrowserOptions implements OptionsFactory {
    static final String HEADLESS_PROPERTY = "controlPanel.ui-tests.headless";
    static final String HEADLESS_ENV = "CONTROL_PANEL_UI_TESTS_HEADLESS";

    @Override
    public Options getOptions() {
        return new Options().setHeadless(isHeadless());
    }

    private static boolean isHeadless() {
        String configured = firstConfigured(System.getProperty(HEADLESS_PROPERTY), System.getenv(HEADLESS_ENV));
        if (configured != null) {
            return Boolean.parseBoolean(configured);
        }
        return System.getenv("CI") != null;
    }

    private static String firstConfigured(String property, String environment) {
        if (hasText(property)) {
            return property;
        }
        if (hasText(environment)) {
            return environment;
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
