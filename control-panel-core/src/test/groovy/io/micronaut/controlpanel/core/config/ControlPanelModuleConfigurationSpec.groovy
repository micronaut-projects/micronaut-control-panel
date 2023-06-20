package io.micronaut.controlpanel.core.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.controlpanel.core.ControlPanel
import spock.lang.Specification

class ControlPanelModuleConfigurationSpec extends Specification {

    void "it is enabled by default in the environment #env"(String env) {
        given:
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments(env)
                .start()

        expect:
        ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()

        where:
        env << [Environment.DEVELOPMENT, Environment.TEST]
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments(Environment.TEST)
                .properties([(ControlPanelModuleConfiguration.PROPERTY_ENABLED): false])
                .start()

        expect:
        !ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

    void "it is disabled in a non-allowed environment"() {
        given:
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .start()

        expect:
        !ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

    void "an environment can be allowed"() {
        given:
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties([(ControlPanelModuleConfiguration.PROPERTY_ALLOWED_ENVIRONMENTS): "prod"])
                .start()

        expect:
        ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

    void "it is disabled when no environment is activated"() {
        given:
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .start()

        expect:
        !ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

    void "it is disabled when active environments don't match the allowed ones"() {
        given:
        def ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties([(ControlPanelModuleConfiguration.PROPERTY_ALLOWED_ENVIRONMENTS): "staging"])
                .start()

        expect:
        !ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

}
