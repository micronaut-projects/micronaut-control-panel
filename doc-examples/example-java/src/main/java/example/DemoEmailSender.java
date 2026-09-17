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
package example;

import io.micronaut.email.Email;
import io.micronaut.email.EmailException;
import io.micronaut.email.EmailSender;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.function.Consumer;

/**
 * Local no-op email sender used by the example application.
 */
@Singleton
@Named("demo")
public class DemoEmailSender implements EmailSender<Object, Object> {

    @Override
    public Object send(Email.Builder builder, Consumer<Object> requestCustomizer) throws EmailException {
        builder.build();
        return new Object();
    }

    @Override
    public String getName() {
        return "demo";
    }
}
