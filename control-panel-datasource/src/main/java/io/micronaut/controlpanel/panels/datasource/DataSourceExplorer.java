package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class DataSourceExplorer {

    private final Connection connection;

    public DataSourceExplorer(final Connection connection) {
        this.connection = connection;
    }

    @Connectable
    public List<String> findTables() {
        List<String> tables = new ArrayList<>();
        try {
            DatabaseMetaData dbMetaData = connection.getMetaData();
            try (ResultSet tablesRs = dbMetaData.getTables(null, null, "%", new String[] { "TABLE" })) {
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
