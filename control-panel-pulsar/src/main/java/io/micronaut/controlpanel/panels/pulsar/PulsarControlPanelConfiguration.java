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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration properties for the Pulsar control panel.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@ConfigurationProperties(PulsarControlPanelConfiguration.PREFIX)
public interface PulsarControlPanelConfiguration {

    String PREFIX = "micronaut.control-panel.panels.pulsar";
    String DEFAULT_ALLOW_CONSUMER_ACTIONS = "false";
    String DEFAULT_INCLUDE_FAILURE_EVENTS = "true";
    String DEFAULT_MAX_FAILURE_EVENTS = "25";

    /**
     * Whether pause and resume actions are enabled. Default value: {@value #DEFAULT_ALLOW_CONSUMER_ACTIONS}.
     *
     * @return true if consumer actions are enabled
     */
    @Bindable(defaultValue = DEFAULT_ALLOW_CONSUMER_ACTIONS)
    boolean isAllowConsumerActions();

    /**
     * Whether recent in-process Pulsar failure events are displayed. Default value: {@value #DEFAULT_INCLUDE_FAILURE_EVENTS}.
     *
     * @return true if failure events are displayed
     */
    @Bindable(defaultValue = DEFAULT_INCLUDE_FAILURE_EVENTS)
    boolean isIncludeFailureEvents();

    /**
     * Maximum number of recent failure events to retain. Default value: {@value #DEFAULT_MAX_FAILURE_EVENTS}.
     *
     * @return maximum retained failure events
     */
    @Bindable(defaultValue = DEFAULT_MAX_FAILURE_EVENTS)
    int getMaxFailureEvents();
}
