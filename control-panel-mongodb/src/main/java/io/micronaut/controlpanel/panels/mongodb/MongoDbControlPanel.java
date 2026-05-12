/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.mongodb;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * MongoDB client diagnostics control panel.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
public class MongoDbControlPanel extends AbstractEachBeanControlPanel<MongoDbModels.Body> {

    public static final String NAME = "mongodb";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final String DEFAULT_ICON_CLASS = "si si-mongodb";

    private final MongoDbDiagnosticService diagnosticService;

    MongoDbControlPanel(MongoDbDiagnosticService diagnosticService,
                        ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticService = diagnosticService;
    }

    @Override
    protected String getBeanName() {
        return diagnosticService.mode() + "-" + diagnosticService.beanName();
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public String getTitle() {
        return diagnosticService.mode() + " " + diagnosticService.beanName();
    }

    @Override
    public MongoDbModels.Body getBody() {
        return diagnosticService.getBody();
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getIcon() {
        return DEFAULT_ICON_CLASS;
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "MongoDB", DEFAULT_ICON_CLASS);
    }
}

@EachBean(SyncMongoDbDiagnosticService.class)
final class SyncMongoDbControlPanel extends MongoDbControlPanel {

    SyncMongoDbControlPanel(@Parameter String beanName,
                            @Parameter SyncMongoDbDiagnosticService diagnosticService,
                            @Named(NAME) ControlPanelConfiguration configuration) {
        super(diagnosticService, configuration);
    }
}

@EachBean(ReactiveMongoDbDiagnosticService.class)
final class ReactiveMongoDbControlPanel extends MongoDbControlPanel {

    ReactiveMongoDbControlPanel(@Parameter String beanName,
                                @Parameter ReactiveMongoDbDiagnosticService diagnosticService,
                                @Named(NAME) ControlPanelConfiguration configuration) {
        super(diagnosticService, configuration);
    }
}
