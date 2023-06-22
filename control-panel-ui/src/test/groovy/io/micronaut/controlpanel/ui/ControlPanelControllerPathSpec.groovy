package io.micronaut.controlpanel.ui

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ControlPanelControllerPathSpec extends Specification {

    void "control panel is accessible in the default path"() {
        given:
        def server = ApplicationContext.run(EmbeddedServer)
        def ctx = server.applicationContext
        def client = ctx.createBean(HttpClient, server.URL).toBlocking()

        when:
        def status = client.exchange(ControlPanelModuleConfiguration.DEFAULT_PATH).status()

        then:
        status == HttpStatus.OK
    }

    void "control panel path is configurable"() {
        given:
        def path = "/cp"
        def server = ApplicationContext.run(EmbeddedServer, [(ControlPanelModuleConfiguration.PROPERTY_PATH): path] as Map)
        def ctx = server.applicationContext
        def client = ctx.createBean(HttpClient, server.URL).toBlocking()

        when:
        def status = client.exchange(path).status()

        then:
        status == HttpStatus.OK
    }

}
