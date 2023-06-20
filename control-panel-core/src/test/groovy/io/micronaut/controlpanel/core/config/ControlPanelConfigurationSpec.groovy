package io.micronaut.controlpanel.core.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.controlpanel.core.ControlPanel
import spock.lang.Specification

class ControlPanelConfigurationSpec extends Specification {

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
                .properties([(ControlPanelConfiguration.PROPERTY_ENABLED): false])
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
                .properties([(ControlPanelConfiguration.PROPERTY_ALLOWED_ENVIRONMENTS): "prod"])
                .start()

        expect:
        ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

}
