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
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel.View;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.datasource.model.Body;
import io.micronaut.controlpanel.panels.datasource.model.DataSourceInfo;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel for DataSource metadata, displaying tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceControlPanel extends AbstractEachBeanControlPanel<Body> {

    public static final String NAME = "datasource";
    public static final String DEFAULT_ICON_CLASS = "fas fa-database";
    private static final Logger LOG = LoggerFactory.getLogger(DataSourceControlPanel.class);

    private final String beanName;
    private final List<Table> tables;
    private final Body body;
    private final DataSourceInfo dataSourceInfo;

    public DataSourceControlPanel(@Parameter String beanName,
                                  @Parameter DataSourceService dataSourceService,
                                  Environment environment,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initializing DataSourceControlPanel for bean='{}'", beanName);
        }
        this.tables = dataSourceService.getTables();
        this.dataSourceInfo = createDataSourceInfo(environment, beanName, dataSourceService);
        this.body = new Body(dataSourceInfo, tables, dataSourceService.generateMermaidER(tables), dataSourceService.getPoolInfo().orElse(null));
        if (LOG.isDebugEnabled()) {
            LOG.debug("DataSourceControlPanel initialized: bean='{}', tables={}, dbType={} URL='{}' user='{}'", beanName, tables.size(), dataSourceInfo.type(), safeUrl(dataSourceInfo.jdbcUrl()), safeUser(dataSourceInfo.username()));
        }
    }

    private DataSourceInfo createDataSourceInfo(Environment env, String beanName, DataSourceService dataSourceService) {
        var jdbUrl = env.getProperty("datasources.%s.url".formatted(beanName), String.class, "");
        var username = env.getProperty("datasources.%s.username".formatted(beanName), String.class, "");
        var password = env.getProperty("datasources.%s.password".formatted(beanName), String.class, "");
        var dialect = env.getProperty("datasources.%s.dialect".formatted(beanName), String.class, "");
        var dbType = env.getProperty("datasources.%s.db-type".formatted(beanName), String.class, "");
        var jdbcInfo = dataSourceService.getJdbcInfo();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolved datasource props for bean='{}': url='{}', user='{}', dialect='{}', dbType='{}'", beanName, safeUrl(jdbUrl), safeUser(username), dialect, dbType);
        }
        return new DataSourceInfo(beanName, jdbUrl, username, password, DatabaseType.of(dialect, dbType), jdbcInfo);
    }

    @Override
    protected String getBeanName() {
        return beanName;
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public Body getBody() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getBody called for bean='{}' -> tables={}", beanName, tables.size());
        }
        return body;
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getDetailLinkName() {
        return "Detail";
    }

    @Override
    public View getBodyView() {
        return new View("/controlpanelviews/datasource/body");
    }

    @Override
    public View getDetailedView() {
        return new View("/controlpanelviews/datasource/detail");
    }

    private static String safeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        // Avoid logging credentials in URL
        int at = url.indexOf('@');
        int colonSlash = url.indexOf("://");
        if (at > -1 && colonSlash > -1 && at > colonSlash) {
            return url.substring(0, colonSlash + 3) + "***@" + url.substring(at + 1);
        }
        return url;
    }

    private static String safeUser(String user) {
        return user == null ? "" : user;
    }

    @Override
    public String getIcon() {
        return switch (dataSourceInfo.type()) {
            case SQL_SERVER -> "fa-brands fa-microsoft";
            case POSTGRES -> "si si-postgresql";
            case MYSQL -> "si si-mysql";
            case MARIADB -> "si si-mariadb";
            default -> DEFAULT_ICON_CLASS;
        };
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Data Sources", DEFAULT_ICON_CLASS);
    }
}
