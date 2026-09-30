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

import com.mongodb.MongoClientSettings;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Empty state panel rendered when the MongoDB panel module is present without supported clients.
 */
@Singleton
@Requires(classes = MongoClientSettings.class)
@Requires(missingBeans = MongoDbDiagnosticService.class)
final class NoMongoDbClientsControlPanel extends AbstractControlPanel<MongoDbModels.Body> {

    NoMongoDbClientsControlPanel(@Named(MongoDbControlPanel.NAME) ControlPanelConfiguration configuration) {
        super(MongoDbControlPanel.NAME, configuration);
    }

    @Override
    public MongoDbModels.Body getBody() {
        return MongoDbModels.Body.empty();
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public boolean hasDetails() {
        return false;
    }

    @Override
    public Category getCategory() {
        return new Category(MongoDbControlPanel.NAME, "MongoDB", MongoDbControlPanel.DEFAULT_ICON_CLASS);
    }
}
