/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.controlpanel.core;

import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.NonNull;

/**
 * Base class for control panels that can delegate some of its values to a {@link ControlPanelConfiguration}.
 *
 * @param <B> the type of the body of the control panel.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public abstract class AbstractControlPanel<B> implements ControlPanel<B> {

    @NonNull
    protected final String controlPanelName;

    @NonNull
    protected final ControlPanelConfiguration configuration;

    protected AbstractControlPanel(@NonNull String controlPanelName, @NonNull ControlPanelConfiguration configuration) {
        this.controlPanelName = controlPanelName;
        this.configuration = configuration;
    }

    @Override
    public String getTitle() {
        return configuration.getTitle();
    }

    @Override
    public int getOrder() {
        return configuration.getOrder();
    }

    @Override
    public String getIcon() {
        return configuration.getIcon();
    }

    @Override
    public boolean isEnabled() {
        return configuration.isEnabled();
    }

    @Override
    public @NonNull String getName() {
        return controlPanelName;
    }
}
