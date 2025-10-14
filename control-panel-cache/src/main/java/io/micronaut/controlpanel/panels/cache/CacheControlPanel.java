package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.Cache;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.async.publisher.Publishers;
import jakarta.inject.Named;

/**
 * A control panel for managing caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.10.0
 */
@EachBean(Cache.class)
public class CacheControlPanel extends AbstractEachBeanControlPanel<CacheControlPanel.Body> {

    public static final String NAME = "cache";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final Cache<?> cache;

    public CacheControlPanel(@Parameter Cache<?> cache, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.cache = cache;
        SyncCache s;
    }

    @Override
    protected String getBeanName() {
        return cache.getName();
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public Body getBody() {
        return new Body(cache);
    }

    @Override
    public String getBadge() {
        return super.getBadge();
    }

    @Override
    public Category getCategory() {
        return super.getCategory();
    }

    @ReflectiveAccess
    public record Body(Cache<?> cache){}
}
