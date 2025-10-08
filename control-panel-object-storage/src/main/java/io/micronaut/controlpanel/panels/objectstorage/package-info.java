@Configuration
@Requires(property = ObjectStorageControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
@Requires(condition = ControlPanelEnabledCondition.class)

package io.micronaut.controlpanel.panels.objectstorage;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelEnabledCondition;
import io.micronaut.core.util.StringUtils;
