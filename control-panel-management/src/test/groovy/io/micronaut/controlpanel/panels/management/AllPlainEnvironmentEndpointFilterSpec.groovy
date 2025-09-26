package io.micronaut.controlpanel.panels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.management.endpoint.env.EnvironmentEndpointFilter
import spock.lang.Specification

class AllPlainEnvironmentEndpointFilterSpec extends Specification {

    void "filter is not enabled by default"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def filters = ctx.getBeansOfType(EnvironmentEndpointFilter)

        then:
        filters.isEmpty()

        cleanup:
        ctx.stop()
    }

    void "filter is enabled when property is set to true"() {
        given:
        def ctx = ApplicationContext.run([
            (AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY): true
        ])

        when:
        def filter = ctx.getBean(AllPlainEnvironmentEndpointFilter)

        then:
        filter instanceof AllPlainEnvironmentEndpointFilter

        cleanup:
        ctx.stop()
    }

    void "filter is not enabled when property is set to false"() {
        given:
        def ctx = ApplicationContext.run([
            (AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY): false
        ])

        when:
        def filters = ctx.getBeansOfType(EnvironmentEndpointFilter)

        then:
        filters.isEmpty()

        cleanup:
        ctx.stop()
    }

    void "filter can be instantiated and implements EnvironmentEndpointFilter"() {
        given:
        def ctx = ApplicationContext.run([
            (AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY): true
        ])

        when:
        def filter = ctx.getBean(AllPlainEnvironmentEndpointFilter)

        then:
        filter instanceof EnvironmentEndpointFilter
        filter.class == AllPlainEnvironmentEndpointFilter

        cleanup:
        ctx.stop()
    }
}