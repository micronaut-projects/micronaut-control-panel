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
package io.micronaut.controlpanel.panels.email;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The email senders and configuration of the application. Internal to the Email panel views.
 *
 * @param from the default sender address, or {@code null} if none is configured
 * @param senders the email senders, sorted by name, each listed once
 * @param templates whether Micronaut Email templates are available
 * @param javaMail the JavaMail settings, or {@code null} if JavaMail is not used
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record EmailInfo(@Nullable String from, List<Sender> senders, boolean templates, @Nullable JavaMail javaMail) {

    /**
     * An email sender.
     *
     * @param name the sender name
     * @param provider the provider, such as {@code JavaMail (SMTP)} or {@code Amazon SES}
     * @param type the sender class
     * @param sync whether the sender can send synchronously
     * @param async whether the sender can send asynchronously
     */
    @ReflectiveAccess
    public record Sender(String name, String provider, String type, boolean sync, boolean async) {

        /**
         * @return {@code sync}, {@code async} or {@code sync and async}
         */
        public String modes() {
            if (sync && async) {
                return "sync and async";
            }
            return async ? "async" : "sync";
        }
    }

    /**
     * The JavaMail settings.
     *
     * @param properties the {@code mail.*} properties, with the values of properties that are not known to be safe hidden
     * @param authentication whether a username and password are configured, never the credentials themselves
     */
    @ReflectiveAccess
    public record JavaMail(List<Property> properties, String authentication) {
    }

    /**
     * A JavaMail property.
     *
     * @param name the property name
     * @param value the value, or {@code null} if it is hidden
     */
    @ReflectiveAccess
    public record Property(String name, @Nullable String value) {
    }
}
