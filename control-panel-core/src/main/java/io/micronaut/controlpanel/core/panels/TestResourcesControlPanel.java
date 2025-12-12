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
package io.micronaut.controlpanel.core.panels;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.testresources.client.TestResourcesClient;
import io.micronaut.testresources.client.TestResourcesClientFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * A control panel to display a link to the Test Resources control panel.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
@Requires(classes = TestResourcesClient.class)
@Requires(property = TestResourcesControlPanel.TEST_RESOURCES_SERVER_URI)
public class TestResourcesControlPanel extends AbstractControlPanel<String> {

    public static final String NAME = "test-resources";
    public static final String TEST_RESOURCES_SERVER_URI = "micronaut.test.resources.server.uri";

    private final TestResourcesClient client;

    protected TestResourcesControlPanel(@Named(NAME) ControlPanelConfiguration configuration,
                                        ApplicationContext applicationContext) {
        super(NAME, configuration);
        this.client = TestResourcesClientFactory.extractFrom(applicationContext);
    }

    @Override
    public String getBody() {
        return client.resolve(TEST_RESOURCES_SERVER_URI, Map.of(), Map.of()).orElse(StringUtils.EMPTY_STRING);
    }

    @Override
    public boolean hasDetails() {
        return false;
    }
}
