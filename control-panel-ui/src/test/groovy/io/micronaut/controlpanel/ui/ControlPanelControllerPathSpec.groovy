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

    void "control panel HTML templates use the custom path in links"() {
        given:
        def customPath = "/admin"
        def server = ApplicationContext.run(EmbeddedServer, [(ControlPanelModuleConfiguration.PROPERTY_PATH): customPath] as Map)
        def ctx = server.applicationContext
        def client = ctx.createBean(HttpClient, server.URL).toBlocking()

        when:
        def response = client.exchange(customPath, String)

        then:
        response.status() == HttpStatus.OK
        def html = response.body()
        
        // Check that the brand logo link uses the custom path
        html.contains('href="/admin"')
        
        // Check that category links use the custom path
        html.contains('href="/admin/categories/')
        
        // Check that control panel detail links use the custom path (if present in index view)
        html.contains('"/admin/') || !html.contains('"/control-panel/')
    }

}
