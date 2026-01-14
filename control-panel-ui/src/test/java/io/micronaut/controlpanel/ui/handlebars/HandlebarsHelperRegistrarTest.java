package io.micronaut.controlpanel.ui.handlebars;

import com.github.jknack.handlebars.Handlebars;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.views.ViewsConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class HandlebarsHelperRegistrarTest {

    private HandlebarsHelperRegistrar registrar;
    private Handlebars handlebars;

    @BeforeEach
    void setup() {
        ViewsConfigurationProperties config = new ViewsConfigurationProperties();
        config.setFolder("/views");
        registrar = new HandlebarsHelperRegistrar(config);
        handlebars = new Handlebars();
        @SuppressWarnings("unchecked")
        BeanCreatedEvent<Handlebars> event = (BeanCreatedEvent<Handlebars>) Mockito.mock(BeanCreatedEvent.class);
        Mockito.when(event.getBean()).thenReturn(handlebars);
        registrar.onCreated(event);
    }

    @Test
    void testOnCreatedReturnsSameHandlebarsInstance() {
        Handlebars fresh = new Handlebars();
        @SuppressWarnings("unchecked")
        BeanCreatedEvent<Handlebars> event = (BeanCreatedEvent<Handlebars>) Mockito.mock(BeanCreatedEvent.class);
        Mockito.when(event.getBean()).thenReturn(fresh);
        assertSame(fresh, registrar.onCreated(event));
    }

    @Test
    void testSizeHelperReturnsCollectionSize() throws Exception {
        var template = handlebars.compileInline("{{size}}\n");
        assertEquals("0\n", template.apply(java.util.List.of()));
        assertEquals("1\n", template.apply(java.util.List.of(1)));
        assertEquals("3\n", template.apply(java.util.List.of(1,2,3)));
        assertEquals("5\n", template.apply(java.util.List.of("a","b","c","d","e")));
    }

    @Test
    void testModHelperWorksWithIndex() throws Exception {
        var template = handlebars.compileInline("{{#each items}}{{@index}}{{#mod @index 4 0}}*{{/mod}} {{/each}}\n");
        var ctx = java.util.Map.of("items", java.util.List.of("a","b","c","d","e","f","g","h","i"));
        var result = template.apply(ctx);
        assertTrue(result.contains("3*"));
        assertTrue(result.contains("7*"));
        assertFalse(result.contains("1*"));
        assertFalse(result.contains("2*"));
    }

    @Test
    void testMinusHelper() throws Exception {
        var template = handlebars.compileInline("Used: {{minus base value}}%\n");
        var ctx = java.util.Map.of("base", 100, "value", 25);
        var result = template.apply(ctx);
        assertEquals("Used: 75%\n", result);
    }

    @Test
    void testPercentageHelper() throws Exception {
        var template = handlebars.compileInline("Free: {{percentage free total}}%\n");
        var ctx = java.util.Map.of("free", 25L, "total", 100L);
        var result = template.apply(ctx);
        assertEquals("Free: 25%\n", result);
    }

    @Test
    void testComplexDiskSpaceUsage() throws Exception {
        var template = handlebars.compileInline("Used: {{minus hundred (percentage free total)}}%, Free: {{percentage free total}}%\n");
        var ctx = java.util.Map.of("hundred", 100, "free", 25L, "total", 100L);
        var result = template.apply(ctx);
        assertEquals("Used: 75%, Free: 25%\n", result);
    }

    @Test
    void testIsMapHelper() throws Exception {
        var template = handlebars.compileInline("{{#isMap myParam}}is map{{else}}is not map{{/isMap}}\n");
        var result = template.apply(java.util.Map.of("myParam", java.util.Map.of("a",1, "b",2)));
        assertEquals("is map\n", result);
        result = template.apply(java.util.Map.of("myParam", "something else"));
        assertEquals("is not map\n", result);
    }
}
