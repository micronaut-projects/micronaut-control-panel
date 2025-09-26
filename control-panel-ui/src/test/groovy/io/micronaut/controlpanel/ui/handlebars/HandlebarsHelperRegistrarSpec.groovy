package io.micronaut.controlpanel.ui.handlebars

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class HandlebarsHelperRegistrarSpec extends Specification {

    @Inject
    Handlebars handlebars

    void "test that Handlebars is configured with HighConcurrencyTemplateCache"() {
        when:
        def cache = handlebars.cache

        then:
        cache instanceof HighConcurrencyTemplateCache
    }

    void "test that custom helpers are registered"() {
        when:
        def hasPercentageHelper = handlebars.helper("percentage") != null
        def hasMinusHelper = handlebars.helper("minus") != null
        def hasModHelper = handlebars.helper("mod") != null
        def hasSizeHelper = handlebars.helper("size") != null
        def hasPartialExistsHelper = handlebars.helper("partialExists") != null

        then:
        hasPercentageHelper
        hasMinusHelper
        hasModHelper
        hasSizeHelper
        hasPartialExistsHelper
    }
}