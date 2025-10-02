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
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.views.ViewsConfigurationProperties
import spock.lang.Specification

class HandlebarsCacheConfigSpec extends Specification {

    HandlebarsHelperRegistrar registrar

    void setup() {
        def config = new ViewsConfigurationProperties()
        config.folder = "/views"
        registrar = new HandlebarsHelperRegistrar(config)
    }

    void "configures HighConcurrencyTemplateCache on Handlebars instance"() {
        given:
        def handlebars = new Handlebars()
        def event = Mock(BeanCreatedEvent) {
            getBean() >> handlebars
        }

        when:
        registrar.onCreated(event)

        then:
        // Verify we set the high concurrency cache
        handlebars.getCache() instanceof HighConcurrencyTemplateCache
    }
}
