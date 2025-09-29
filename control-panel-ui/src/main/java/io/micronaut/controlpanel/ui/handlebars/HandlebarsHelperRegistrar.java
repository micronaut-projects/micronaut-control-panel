/*
 * Copyright 2017-2023 original authors
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
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.HumanizeHelper;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.stream.Collectors;

@Internal
@Singleton
class HandlebarsHelperRegistrar implements BeanCreatedEventListener<Handlebars> {

    @Override
    public Handlebars onCreated(@NonNull BeanCreatedEvent<Handlebars> event) {
        Handlebars handlebars = event.getBean();
        HumanizeHelper.register(handlebars);
        
        // Register specific helpers manually instead of using reflection-based registerHelpers
        // This is necessary for native image compatibility
        registerConditionalHelpers(handlebars);
        registerStringHelpers(handlebars);
        
        handlebars.registerHelper("percentage", percentageHelper());
        handlebars.registerHelper("minus", minusHelper());
        handlebars.registerHelper("mod", modHelper());
        handlebars.registerHelper("size", (ctx, opts) -> ((Collection<?>) ctx).size());
        handlebars.registerHelper("partialExists", partialExistsHelper(handlebars));
        return handlebars;
    }

    private void registerConditionalHelpers(Handlebars handlebars) {
        // Register the most commonly used conditional helpers manually
        handlebars.registerHelper("eq", (ctx, opts) -> {
            Object param = opts.param(0);
            return ctx != null && ctx.equals(param) ? opts.fn() : opts.inverse();
        });
        
        handlebars.registerHelper("ne", (ctx, opts) -> {
            Object param = opts.param(0);
            return ctx == null || !ctx.equals(param) ? opts.fn() : opts.inverse();
        });
        
        handlebars.registerHelper("gt", (ctx, opts) -> {
            Object param = opts.param(0);
            if (ctx instanceof Number && param instanceof Number) {
                return ((Number) ctx).doubleValue() > ((Number) param).doubleValue() ? opts.fn() : opts.inverse();
            }
            return opts.inverse();
        });
        
        handlebars.registerHelper("lt", (ctx, opts) -> {
            Object param = opts.param(0);
            if (ctx instanceof Number && param instanceof Number) {
                return ((Number) ctx).doubleValue() < ((Number) param).doubleValue() ? opts.fn() : opts.inverse();
            }
            return opts.inverse();
        });
    }

    private void registerStringHelpers(Handlebars handlebars) {
        // Register the most commonly used string helpers manually
        handlebars.registerHelper("join", (ctx, opts) -> {
            if (ctx instanceof Collection) {
                String separator = opts.param(0, ", ");
                return ((Collection<?>) ctx).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(separator));
            }
            return ctx != null ? ctx.toString() : "";
        });
        
        handlebars.registerHelper("defaultIfEmpty", (ctx, opts) -> {
            String str = ctx != null ? ctx.toString() : "";
            return str.isEmpty() ? opts.param(0, "") : str;
        });
        
        handlebars.registerHelper("lower", (ctx, opts) -> {
            return ctx != null ? ctx.toString().toLowerCase() : "";
        });
        
        handlebars.registerHelper("upper", (ctx, opts) -> {
            return ctx != null ? ctx.toString().toUpperCase() : "";
        });
        
        handlebars.registerHelper("stringFormat", (ctx, opts) -> {
            if (ctx != null && opts.params.length > 0) {
                return String.format(ctx.toString(), opts.params);
            }
            return ctx != null ? ctx.toString() : "";
        });
    }

    private static Helper<Integer> modHelper() {
        return (ctx, opts) -> {
            int a = ctx + 1;
            int b = opts.param(0);
            int c = opts.param(1);

            if ((a % b) == c) {
                return opts.fn();
            } else {
                return opts.inverse();
            }
        };
    }

    private static Helper<Integer> minusHelper() {
        return (ctx, opts) -> {
            if (ctx == null) {
                return 0;
            }
            int a = ctx;
            int b = opts.param(0);
            return a - b;
        };
    }

    private static Helper<Long> percentageHelper() {
        return (ctx, opts) -> {
            if (ctx == null) {
                return 0;
            }
            Object totalParam = opts.param(0);
            if (totalParam == null) {
                return 0;
            }
            Double value = ctx.doubleValue();
            Double total = ((Long) totalParam).doubleValue();
            if (total == 0) {
                return 0;
            }
            return (int) Math.ceil((value / total) * 100);
        };
    }

    private static Helper<String> partialExistsHelper(Handlebars handlebars) {
        return (partialName, opts) -> {
            try {
                handlebars.compile(partialName);
                return opts.fn(); // Partial exists, render the block
            } catch (Exception e) {
                return opts.inverse(); // Partial doesn't exist, render the inverse block
            }
        };
    }
}
