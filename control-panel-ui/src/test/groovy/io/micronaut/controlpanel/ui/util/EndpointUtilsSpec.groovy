package io.micronaut.controlpanel.ui.util

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.refresh.RefreshEndpoint
import io.micronaut.management.endpoint.stop.ServerStopEndpoint
import spock.lang.Specification

class EndpointUtilsSpec extends Specification {

    void "it can check if an endpoint is sensitive"(Class endpointClass, Map configuration, boolean expectedSensitive) {
        given:
        configuration.put("endpoints.all.enabled", true) //Assume that endpoints are enabled
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .propertySources(PropertySource.of(configuration))
                .run(ApplicationContext)

        def endpoint = ctx.getBean(endpointClass)

        when:
        def sensitive = EndpointUtils.isSensitive(endpoint, ctx)

        then:
        sensitive == expectedSensitive

        cleanup:
        ctx.stop()

        where:
        endpointClass       | configuration                             | expectedSensitive
        RefreshEndpoint     | [:]                                       | Endpoint.SENSITIVE
        RefreshEndpoint     | ['endpoints.all.sensitive': true]         | true
        RefreshEndpoint     | ['endpoints.refresh.sensitive': true]     | true
        RefreshEndpoint     | ['endpoints.all.sensitive': false]        | false
        RefreshEndpoint     | ['endpoints.refresh.sensitive': false]    | false
        ServerStopEndpoint  | [:]                                       | Endpoint.SENSITIVE
        ServerStopEndpoint  | ['endpoints.all.sensitive': true]         | true
        ServerStopEndpoint  | ['endpoints.stop.sensitive': true]        | true
        ServerStopEndpoint  | ['endpoints.all.sensitive': false]        | false
        ServerStopEndpoint  | ['endpoints.stop.sensitive': false]       | false
    }

}
