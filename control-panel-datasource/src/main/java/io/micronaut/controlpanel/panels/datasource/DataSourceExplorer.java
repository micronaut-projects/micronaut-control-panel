package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@EachBean(DataSource.class)
public class DataSourceExplorer {

    private final DataSource dataSource;

    public DataSourceExplorer(@Parameter DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    public List<String> findTables() {
        List<String> tables = new ArrayList<>();
        try {
            var connection = dataSource.getConnection();
            DatabaseMetaData dbMetaData = connection.getMetaData();
            try (ResultSet tablesRs = dbMetaData.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] { "TABLE" })) {
                while (tablesRs.next()) {
                    tables.add(tablesRs.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables;
    }
}
