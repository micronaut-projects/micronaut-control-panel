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

    void "test modHelper - returns content when condition is true"() {
        given:
        def template = handlebars.compileInline("{{#mod 2 0}}match{{else}}no match{{/mod}}")
        def context = [value: 1] // ctx=1, so (1+1) % 2 == 0

        when:
        def result = template.apply(context)

        then:
        result == "match"
    }

    void "test modHelper - returns inverse when condition is false"() {
        given:
        def template = handlebars.compileInline("{{#mod 2 0}}match{{else}}no match{{/mod}}")
        def context = [value: 0] // ctx=0, so (0+1) % 2 == 1, not 0

        when:
        def result = template.apply(context)

        then:
        result == "no match"
    }

    void "test modHelper - various modulo conditions"() {
        expect:
        handlebars.compileInline("{{#mod " + b + " " + c + "}}match{{else}}no match{{/mod}}").apply(ctx) == expectedResult

        where:
        ctx | b | c | expectedResult
        3   | 2 | 0 | "match"      // (3+1) % 2 = 0
        4   | 3 | 2 | "match"      // (4+1) % 3 = 2
        5   | 3 | 1 | "no match"   // (5+1) % 3 = 0, not 1
        0   | 4 | 1 | "match"      // (0+1) % 4 = 1
    }

    void "test minusHelper - performs subtraction correctly"() {
        expect:
        handlebars.compileInline("{{minus " + subtractValue + "}}").apply(minuend) == (minuend - subtractValue).toString()

        where:
        minuend | subtractValue
        10      | 3
        5       | 2
        0       | 5
        100     | 25
    }

    void "test minusHelper - with context variables"() {
        given:
        def template = handlebars.compileInline("{{minus b}}")
        def context = [a: 10, b: 3]

        when:
        def result = template.apply(10, context) // 10 as root context, context as additional params

        then:
        result == "7" // 10 - 3 = 7
    }

    void "test percentageHelper - calculates percentage correctly with context variables"() {
        given:
        def template = handlebars.compileInline("{{percentage total}}")
        def context = [total: totalValue, "this": valueToCalculate] // valueToCalculate is the context value (this)

        when:
        def result = template.apply(context)

        then:
        result == expectedPercentage.toString()

        where:
        valueToCalculate | totalValue | expectedPercentage
        25L              | 100L       | 25   // 25/100 * 100 = 25%
        50L              | 200L       | 25   // 50/200 * 100 = 25%
        33L              | 100L       | 33   // 33/100 * 100 = 33%
        1L               | 3L         | 34   // 1/3 * 100 = 33.33%, ceil = 34%
        2L               | 3L         | 67   // 2/3 * 100 = 66.66%, ceil = 67%
        0L               | 100L       | 0    // 0/100 * 100 = 0%
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

    void "test complex example like diskSpace helper usage"() {
        given:
        // Simulate diskSpace template pattern: {{minus 100 (percentage free total)}}
        def template = handlebars.compileInline("Used: {{minus 100 (percentage free total)}}%, Free: {{percentage free total}}%")
        def context = [free: 25L, total: 100L] // 25GB free out of 100GB total

        when:
        def result = template.apply(context)

        then:
        result == "Used: 75%, Free: 25%" // Used = 100 - 25 = 75%, Free = 25%
    }

    void "test mod helper like index usage pattern"() {
        given:
        // Simulate pattern: {{#mod @index 4 0}} - used for layout (every 4th item)
        def template = handlebars.compileInline("{{#each items}}Item {{@index}}{{#mod @index 4 0}} (break line){{/mod}} {{/each}}")
        def context = [items: ["a", "b", "c", "d", "e", "f", "g", "h", "i"]]

        when:
        def result = template.apply(context)

        then:
        result.contains("Item 3 (break line)") // index 3: (3+1) % 4 = 0
        result.contains("Item 7 (break line)") // index 7: (7+1) % 4 = 0
        !result.contains("Item 1 (break line)") // index 1: (1+1) % 4 = 2, not 0
    }
}