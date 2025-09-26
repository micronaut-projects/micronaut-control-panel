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
import io.micronaut.context.event.BeanCreatedEvent
import spock.lang.Specification

class HandlebarsHelperRegistrarSpec extends Specification {

    HandlebarsHelperRegistrar registrar
    Handlebars handlebars

    def setup() {
        registrar = new HandlebarsHelperRegistrar()
        handlebars = new Handlebars()
        // Register the helpers using a mocked event
        def event = Mock(BeanCreatedEvent) {
            getBean() >> handlebars
        }
        registrar.onCreated(event)
    }

    void "test onCreated returns same handlebars instance"() {
        given:
        def freshHandlebars = new Handlebars()
        def event = Mock(BeanCreatedEvent) {
            getBean() >> freshHandlebars
        }

        when:
        def result = registrar.onCreated(event)

        then:
        result == freshHandlebars
    }

    void "test sizeHelper - returns collection size"() {
        given:
        def template = handlebars.compileInline("{{size}}")

        expect:
        template.apply(collection) == collection.size().toString()

        where:
        collection << [
            [],
            [1],
            [1, 2, 3],
            ["a", "b", "c", "d", "e"],
            (1..10).toList()
        ]
    }

    void "test modHelper - works with @index like real usage"() {
        given:
        // Simulate pattern: {{#each items}}{{#mod @index 4 0}} (break line){{/mod}}{{/each}}
        def template = handlebars.compileInline("{{#each items}}{{@index}}{{#mod @index 4 0}}*{{/mod}} {{/each}}")
        def context = [items: ["a", "b", "c", "d", "e", "f", "g", "h", "i"]]

        when:
        def result = template.apply(context)

        then:
        result.contains("3*") // index 3: (3+1) % 4 = 0
        result.contains("7*") // index 7: (7+1) % 4 = 0
        !result.contains("1*") // index 1: (1+1) % 4 = 2, not 0
        !result.contains("2*") // index 2: (2+1) % 4 = 3, not 0
    }

    void "test minusHelper - works with context variables like diskSpace template"() {
        given:
        // This will test the usage similar to: {{minus baseValue someVariable}}
        def template = handlebars.compileInline("Used: {{minus base value}}%")
        def context = [base: 100, value: 25]

        when:
        def result = template.apply(context)

        then:
        result == "Used: 75%" // 100 - 25 = 75
    }

    void "test percentageHelper - works with context variables like diskSpace template"() {
        given:
        // This tests the usage: {{percentage free total}}
        def template = handlebars.compileInline("Free: {{percentage free total}}%")
        def context = [free: 25L, total: 100L]

        when:
        def result = template.apply(context)

        then:
        result == "Free: 25%" // 25/100 * 100 = 25%
    }

    void "test complex example like diskSpace helper usage"() {
        given:
        // Simulate diskSpace template pattern: {{minus 100 (percentage free total)}}
        def template = handlebars.compileInline("Used: {{minus hundred (percentage free total)}}%, Free: {{percentage free total}}%")
        def context = [hundred: 100, free: 25L, total: 100L] // 25GB free out of 100GB total

        when:
        def result = template.apply(context)

        then:
        result == "Used: 75%, Free: 25%" // Used = 100 - 25 = 75%, Free = 25%
    }
}