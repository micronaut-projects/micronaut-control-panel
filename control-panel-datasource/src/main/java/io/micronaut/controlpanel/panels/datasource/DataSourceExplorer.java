/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        try (var connection = dataSource.getConnection()){
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
