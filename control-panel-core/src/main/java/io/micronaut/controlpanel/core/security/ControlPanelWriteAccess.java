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
package io.micronaut.controlpanel.core.security;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * UI-facing write access state for the current request.
 *
 * @param allowed whether write operations are allowed
 * @param reason reason to show when write operations are disabled
 */
@Internal
@ReflectiveAccess
public record ControlPanelWriteAccess(boolean allowed, String reason) {

    private static final ControlPanelWriteAccess ALLOWED_ACCESS = new ControlPanelWriteAccess(true, "");
    private static final String DEFAULT_DENIED_REASON = "Write operations are disabled for this control panel session.";

    /**
     * @return allowed write access
     */
    public static ControlPanelWriteAccess allowedAccess() {
        return ALLOWED_ACCESS;
    }

    /**
     * @return denied write access with the default UI reason
     */
    public static ControlPanelWriteAccess deniedAccess() {
        return new ControlPanelWriteAccess(false, DEFAULT_DENIED_REASON);
    }
}
