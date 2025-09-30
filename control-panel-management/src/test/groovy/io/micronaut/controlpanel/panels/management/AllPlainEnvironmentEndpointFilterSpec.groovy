package io.micronaut.controlpanel.panels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.management.endpoint.env.EnvironmentEndpoint
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

    void "environment endpoint filter is applied when show-values is enabled"() {
        given:
        def ctx = ApplicationContext.run([
                'endpoints.env.enabled': true,
                'micronaut.control-panel.env.show-values': true,
                'test.password': 'secret123',
                'test.username': 'john'
        ])

        when:
        def endpoint = ctx.getBean(EnvironmentEndpoint)
        def filters = ctx.getBeansOfType(EnvironmentEndpointFilter)
        def environmentInfo = endpoint.getEnvironmentInfo()

        then:
        filters.size() == 1
        filters[0] instanceof AllPlainEnvironmentEndpointFilter
        environmentInfo != null
        // The filter should be applied by the endpoint itself

        cleanup:
        ctx.stop()
    }

    void "no environment endpoint filter is present when show-values is disabled"() {
        given:
        def ctx = ApplicationContext.run([
                'endpoints.env.enabled': true,
                'micronaut.control-panel.env.show-values': false
        ])

        when:
        def filters = ctx.getBeansOfType(EnvironmentEndpointFilter)

        then:
        filters.isEmpty()

        cleanup:
        ctx.stop()
    }

}
