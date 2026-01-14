package io.micronaut.controlpanel.ui.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.views.ViewsConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class HandlebarsCacheConfigTest {

    @Test
    void configuresHighConcurrencyTemplateCacheOnHandlebarsInstance() {
        ViewsConfigurationProperties config = new ViewsConfigurationProperties();
        config.setFolder("/views");
        HandlebarsHelperRegistrar registrar = new HandlebarsHelperRegistrar(config);

        Handlebars handlebars = new Handlebars();
        @SuppressWarnings("unchecked")
        BeanCreatedEvent<Handlebars> event = (BeanCreatedEvent<Handlebars>) Mockito.mock(BeanCreatedEvent.class);
        Mockito.when(event.getBean()).thenReturn(handlebars);

        registrar.onCreated(event);

        assertTrue(handlebars.getCache() instanceof HighConcurrencyTemplateCache);
    }
}
