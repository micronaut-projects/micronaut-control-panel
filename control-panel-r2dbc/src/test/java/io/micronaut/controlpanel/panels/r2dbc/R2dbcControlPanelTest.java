package io.micronaut.controlpanel.panels.r2dbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2dbcControlPanelTest {

    @Test
    void listsConnectionFactoriesWithoutSecrets() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "r2dbc.datasources.default.url", "r2dbc:pool:h2:mem:///r2dbcPanelTest?maxSize=4",
            "r2dbc.datasources.default.username", "sa",
            "r2dbc.datasources.default.password", "",
            "r2dbc.datasources.reporting.url", "r2dbc:h2:mem:///r2dbcPanelReporting",
            "r2dbc.datasources.reporting.username", "report_user",
            "r2dbc.datasources.reporting.password", "s3cret"
        ))) {
            ConnectionFactory pooled = context.getBean(ConnectionFactory.class, Qualifiers.byName("default"));
            Connection connection = Mono.from(pooled.create()).block();
            try {
                R2dbcControlPanel panel = context.getBean(R2dbcControlPanel.class);
                assertEquals("R2DBC", panel.getTitle());
                assertEquals("datasource", panel.getCategory().id());

                List<R2dbcFactoryInfo> factories = panel.getBody().factories();
                assertEquals(List.of("default", "reporting"), factories.stream().map(R2dbcFactoryInfo::name).toList());

                R2dbcFactoryInfo pool = factories.getFirst();
                assertEquals("pool", pool.driver());
                assertEquals("h2:mem", pool.protocol());
                assertEquals("sa", pool.user());
                assertNotNull(pool.pool());
                assertEquals(1, pool.pool().acquired());
                assertEquals(4, pool.pool().maxAllocated());
                assertEquals(25, pool.pool().usagePercent());

                R2dbcFactoryInfo reporting = factories.get(1);
                assertEquals("h2", reporting.driver());
                assertEquals("H2", reporting.product());
                assertEquals("report_user", reporting.user());
                assertNull(reporting.pool());
                assertFalse(factories.toString().contains("s3cret"));

                assertTrue(context.getBean(ControlPanelRepository.class).findByName(R2dbcControlPanel.NAME).isPresent());
            } finally {
                Mono.from(connection.close()).block();
            }
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(R2dbcControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(R2dbcControlPanel.class));
        }
    }

    @Test
    void usageIsAPercentageOfTheMaximumSize() {
        assertEquals(0, new R2dbcFactoryInfo.Pool(0, 0, 0, 0, 0, 0).usagePercent());
        assertEquals(50, new R2dbcFactoryInfo.Pool(5, 6, 1, 0, 10, 100).usagePercent());
    }
}
