package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;

@Singleton
@Requires(beans = DataSource.class)
@Requires(env = "hibernate")
@Requires(env = "oracle")
public class SampleDataLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SampleDataLoader.class);
    private final DataSource oracleDs;
    private final DataSource postgresDs;
    private final ResourceResolver resourceResolver;

    public SampleDataLoader(@Named("my-oracle") DataSource oracleDs,
                            @Named("my-postgres") DataSource postgresDs,
                            ResourceResolver resourceResolver) {
        this.oracleDs = DelegatingDataSource.unwrapDataSource(oracleDs);
        this.postgresDs = DelegatingDataSource.unwrapDataSource(postgresDs);
        this.resourceResolver = resourceResolver;
    }

    @EventListener
    public void loadData(StartupEvent event) {
        loadPostgresData();
        loadOracleData();
    }

    @EventListener
    public void cleanupData(ShutdownEvent event) {
        try (var connection = postgresDs.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE team");
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        try (var connection = oracleDs.getConnection(); var statement = connection.createStatement()) {
            statement.addBatch("DROP TABLE EMP");
            statement.addBatch("DROP TABLE DEPT");
            statement.executeBatch();
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void loadPostgresData() {
        try (var connection = postgresDs.getConnection(); var statement = connection.createStatement()) {
            statement.execute(loadSql("my-postgres", "create.sql"));
            statement.execute(loadSql("my-postgres", "data.sql"));
            LOG.info("Loaded sample data into Postgres DB");
        } catch (SQLException | IOException e) {
            LOG.error(e.getMessage(), e);
        }

    }
    private void loadOracleData() {
        try (var connection = oracleDs.getConnection(); var statement = connection.createStatement()) {
            statement.execute(loadSql("my-oracle", "create_1.sql"));
            statement.execute(loadSql("my-oracle", "create_2.sql"));
            statement.execute(loadSql("my-oracle", "data_1.sql"));
            statement.execute(loadSql("my-oracle", "data_2.sql"));
            LOG.info("Loaded sample data into Oracle DB");
        } catch (SQLException | IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private String loadSql(String folder, String fileName) throws IOException {
        Optional<URL> sql = resourceResolver.getResource("classpath:sql/%s/%s".formatted(folder, fileName));
        return IOUtils.readText(new BufferedReader(new InputStreamReader(sql.get().openStream())));
    }
}
