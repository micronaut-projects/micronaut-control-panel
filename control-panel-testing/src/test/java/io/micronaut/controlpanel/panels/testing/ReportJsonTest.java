package io.micronaut.controlpanel.panels.testing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportJsonTest {

    @Test
    void readsTheValuesOfAReport() {
        Object value = ReportJson.parse("""
            {"s":"a\\"b\\\\c\\n\\u00e9\\/","n":-12,"d":1.5e2,"t":true,"f":false,"z":null,"a":[1,{"x":[]},"y"],"o":{}}
            """);
        Map<Object, Object> map = new LinkedHashMap<>((Map<?, ?>) value);
        assertTrue(map.containsKey("z"));
        assertNull(map.remove("z"));
        assertEquals(Map.of(
            "s", "a\"b\\c\n\u00e9/",
            "n", -12L,
            "d", 150.0,
            "t", true,
            "f", false,
            "a", List.of(1L, Map.of("x", List.of()), "y"),
            "o", Map.of()
        ), map);
        assertEquals(Arrays.asList(null, "x"), ReportJson.parse(" [ null , \"x\" ] "));
    }

    @Test
    void rejectsWhatIsNotJson() {
        List<String> invalid = List.of("", "{", "{\"a\":1,}", "[1 2]", "{\"a\":tru}", "\"open", "{} {}",
            "{\"a\":\"\\u12\"}", "[".repeat(100) + "]".repeat(100));
        for (String text : invalid) {
            assertThrows(IllegalArgumentException.class, () -> ReportJson.parse(text), text);
        }
    }
}
