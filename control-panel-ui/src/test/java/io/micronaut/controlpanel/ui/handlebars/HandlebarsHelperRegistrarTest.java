/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.ui.handlebars;

import com.github.jknack.handlebars.Handlebars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class HandlebarsHelperRegistrarTest {

    private HandlebarsHelperRegistrar registrar;
    private Handlebars handlebars;

    @BeforeEach
    void setup() {
        registrar = new HandlebarsHelperRegistrar();
        handlebars = new Handlebars();
        registrar.configure(handlebars);
    }

    @Test
    void configureReturnsSameHandlebarsInstance() {
        Handlebars fresh = new Handlebars();
        assertSame(fresh, registrar.configure(fresh));
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
    void testBinaryPrefixHelper() throws Exception {
        var template = handlebars.compileInline("{{binaryPrefix value}}\n");
        assertEquals("1 byte\n", template.apply(java.util.Map.of("value", 1L)));
        assertEquals("100 bytes\n", template.apply(java.util.Map.of("value", 100L)));
        assertEquals("1 KB\n", template.apply(java.util.Map.of("value", 1024L)));
        assertEquals("1 MB\n", template.apply(java.util.Map.of("value", 1048576L)));
    }

    @Test
    void testBinaryPrefixIsLocaleStable() throws Exception {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            var template = handlebars.compileInline("{{binaryPrefix value}}\n");
            assertEquals("1.5 KB\n", template.apply(java.util.Map.of("value", 1536L)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void testDecamelizeAndTitleizeHelpers() throws Exception {
        var template = handlebars.compileInline("{{titleize (decamelize value)}}\n");
        assertEquals("Implementation Class\n", template.apply(java.util.Map.of("value", "implementationClass")));
        assertEquals("Disk Space\n", template.apply(java.util.Map.of("value", "diskSpace")));
    }

    @Test
    void testDecamelizeReplacementHash() throws Exception {
        var template = handlebars.compileInline("{{decamelize value replacement='-'}}\n");
        assertEquals("GL-11-Version\n", template.apply(java.util.Map.of("value", "GL11Version")));
    }

    @Test
    void testDecamelizeReplacementEscapesRegexSpecials() throws Exception {
        var template = handlebars.compileInline("{{decamelize value replacement='$'}}\n");
        assertEquals("GL$11$Version\n", template.apply(java.util.Map.of("value", "GL11Version")));
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
