package io.micronaut.controlpanel.ui.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.views.ViewsConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class RecordingHandlebars extends Handlebars {
    final java.util.List<String> compiled = new java.util.ArrayList<>();
    RecordingHandlebars() { super(); }
    RecordingHandlebars(TemplateLoader loader) { super(loader); }
    @Override public Template compile(String location) throws java.io.IOException {
        compiled.add(location);
        return super.compile(location);
    }
}

class HandlebarsPrecompileTest {

    @Test
    void precompilesTemplatesUnderViewsPrefix() throws Exception {
        ViewsConfigurationProperties config = new ViewsConfigurationProperties();
        config.setFolder("/views");
        HandlebarsHelperRegistrar registrar = new HandlebarsHelperRegistrar(config);

        ClassPathTemplateLoader loader = new ClassPathTemplateLoader("views", ".hbs");
        RecordingHandlebars handlebars = new RecordingHandlebars(loader);

        @SuppressWarnings("unchecked")
        BeanCreatedEvent<Handlebars> event = (BeanCreatedEvent<Handlebars>) Mockito.mock(BeanCreatedEvent.class);
        Mockito.when(event.getBean()).thenReturn(handlebars);

        registrar.onCreated(event);

        assertTrue(handlebars.compiled.contains("views/index"));
        assertTrue(handlebars.compiled.contains("views/layout"));
        assertTrue(handlebars.compiled.size() >= 2);
    }
}
