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
package io.micronaut.controlpanel.ui.handlebars

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.github.jknack.handlebars.io.TemplateLoader
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.views.ViewsConfigurationProperties
import spock.lang.Specification

/**
 * A Handlebars subclass that records calls to {@code compile(String)} so we can assert
 * that the registrar precompiles templates discovered on the classpath.
 */
class RecordingHandlebars extends Handlebars {
    final List<String> compiled = []

    RecordingHandlebars() {
        super()
    }

    RecordingHandlebars(TemplateLoader loader) {
        super(loader)
    }

    @Override
    Template compile(String location) {
        compiled << location
        return super.compile(location)
    }
}

class HandlebarsPrecompileSpec extends Specification {

    HandlebarsHelperRegistrar registrar

    def setup() {
        def config = new ViewsConfigurationProperties()
        config.folder = "/views"
        registrar = new HandlebarsHelperRegistrar(config)
    }

    void "precompiles templates under views prefix"() {
        given:
        // Use the project&#39;s actual templates located under src/main/resources/views/*.hbs
        def loader = new ClassPathTemplateLoader("views", ".hbs")
        def handlebars = new RecordingHandlebars(loader)
        def event = Mock(BeanCreatedEvent) {
            getBean() >> handlebars
        }

        when:
        registrar.onCreated(event)

        then:
        // Expect at least the top-level templates to be compiled (others may also be compiled)
        handlebars.compiled.contains("views/index")
        handlebars.compiled.contains("views/layout")
        handlebars.compiled.size() >= 2
    }
}
