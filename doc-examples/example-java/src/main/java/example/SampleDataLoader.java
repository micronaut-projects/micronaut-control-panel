package example;


import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

@Singleton
@Requires(notEnv = Environment.TEST)
public class SampleDataLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SampleDataLoader.class);
    private final Connection connection;
    private final ResourceResolver resourceResolver;

    public SampleDataLoader(Connection connection, ResourceResolver resourceResolver) throws IOException {
        this.connection = connection;
        this.resourceResolver = resourceResolver;
    }

    @EventListener
    @Async(TaskExecutors.BLOCKING)
    @Connectable
    public void loadData(StartupEvent event) {
        try (var statement = connection.createStatement()) {
            var createSql = loadSql("create.sql");
            statement.execute(createSql);
            LOG.info("Created database schema");

            var dataSql = loadSql("data.sql");
            statement.execute(dataSql);
            LOG.info("Loaded sample data");
        } catch (SQLException | IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private String loadSql(String name) throws IOException {
        Optional<URL> sql = resourceResolver.getResource("classpath:sql/" + name);
        return IOUtils.readText(new BufferedReader(new InputStreamReader(sql.get().openStream())));
    }
}
