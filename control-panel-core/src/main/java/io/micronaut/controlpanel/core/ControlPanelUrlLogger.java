package io.micronaut.controlpanel.core;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.controlpanel.util.ControlPanelUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Singleton
@Requires(property = ControlPanelModuleConfiguration.PREFIX + ".log-url", notEquals = StringUtils.FALSE)
public class ControlPanelUrlLogger {

    private static final Logger LOG = LoggerFactory.getLogger(ControlPanelUrlLogger.class);

    private final String controlPanelUrl;

    public ControlPanelUrlLogger(HttpServerConfiguration serverConfiguration,
                                 ControlPanelModuleConfiguration controlPanelConfiguration,
                                 HttpHostResolver hostResolver) {
        var applicationPath = Optional.ofNullable(serverConfiguration.getContextPath()).orElse("");
        var controlPanelPath = controlPanelConfiguration.getPath();
        var baseUrl = hostResolver.resolve(null);
        this.controlPanelUrl = baseUrl + ControlPanelUtils.computeControlPanelPath(applicationPath, controlPanelPath);

    }

    @EventListener
    public void logUrl(ServiceReadyEvent event) {
        LOG.info("Control Panel availabe at {}", controlPanelUrl);
    }

}
