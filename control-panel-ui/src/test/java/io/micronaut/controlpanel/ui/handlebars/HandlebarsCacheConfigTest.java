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
