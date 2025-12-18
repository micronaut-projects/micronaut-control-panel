package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.util.List;

@EachBean(DataSource.class)
public class DataSourceControlPanel extends AbstractEachBeanControlPanel<DataSourceControlPanel.Body> {

    public static final String NAME = "datasource";
    public static final String ICON_CLASS = "fa-database";

    private final DataSource dataSource;
    private final String beanName;
    private final List<String> tables;

    public DataSourceControlPanel(@Parameter DataSource dataSource,
                                  @Parameter String beanName,
                                  @Parameter DataSourceExplorer dataSourceExplorer,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.dataSource = dataSource;
        this.beanName = beanName;
        this.tables = dataSourceExplorer.findTables();
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
        return new Body(dataSource, tables);
    }

    @Override
    public String getBadge() {
        return String.valueOf(tables.size());
    }

    @Override
    public String getIcon() {
        return ICON_CLASS;
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Data Sources", ICON_CLASS);
    }

    @ReflectiveAccess
    public record Body(DataSource dataSource, List<String> tables) { }
}
