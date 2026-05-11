package example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.function.Executable;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledInNativeImage
class LocalControlPanelDependenciesTest {

    @Test
    void usesLocalControlPanelDependencies() {
        String projectRootProperty = System.getProperty("controlPanelProjectRoot");
        assertNotNull(projectRootProperty, "controlPanelProjectRoot system property must be configured");

        Path projectRoot = Path.of(projectRootProperty).toAbsolutePath().normalize();
        assertAll(
            localDependency(projectRoot, "control-panel-cache", "io.micronaut.controlpanel.panels.cache.CacheController"),
            localDependency(projectRoot, "control-panel-core", "io.micronaut.controlpanel.core.ControlPanel"),
            localDependency(projectRoot, "control-panel-datasource", "io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel"),
            localDependency(projectRoot, "control-panel-hibernate", "io.micronaut.controlpanel.panels.hibernate.HibernateControlPanel"),
            localDependency(projectRoot, "control-panel-kafka", "io.micronaut.controlpanel.panels.kafka.KafkaStreamsControlPanel"),
            localDependency(projectRoot, "control-panel-management", "io.micronaut.controlpanel.panels.management.EnvironmentControlPanel"),
            localDependency(projectRoot, "control-panel-object-storage", "io.micronaut.controlpanel.panels.objectstorage.ObjectStorageControlPanel"),
            localDependency(projectRoot, "control-panel-ui", "io.micronaut.controlpanel.ui.ControlPanelController")
        );
    }

    private static Executable localDependency(Path projectRoot, String moduleDirectory, String className) {
        return () -> {
            Path location = classLocation(className);
            Path expectedModuleRoot = projectRoot.resolve(moduleDirectory).normalize();
            assertTrue(
                location.startsWith(expectedModuleRoot),
                () -> className + " loaded from " + location + " instead of " + expectedModuleRoot
            );
        };
    }

    private static Path classLocation(String className) throws ClassNotFoundException, URISyntaxException {
        Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        var codeSource = type.getProtectionDomain().getCodeSource();
        assertNotNull(codeSource, className + " code source must be available");
        return Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
    }
}
