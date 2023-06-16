package io.micronaut.controlpanel.ui.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.HumanizeHelper;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.Collection;

@Internal
@Singleton
class HandlebarsHelperRegistrar implements BeanCreatedEventListener<Handlebars> {

    @Override
    public Handlebars onCreated(@NonNull BeanCreatedEvent<Handlebars> event) {
        Handlebars handlebars = event.getBean();
        HumanizeHelper.register(handlebars);
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelpers(StringHelpers.class);
        handlebars.registerHelper("percentage", percentageHelper());
        handlebars.registerHelper("minus", minusHelper());
        handlebars.registerHelper("mod", modHelper());
        handlebars.registerHelper("size", (ctx, opts) -> ((Collection<?>) ctx).size());
        return handlebars;
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
            int a = ctx;
            int b = opts.param(0);
            return a - b;
        };
    }

    private static Helper<Long> percentageHelper() {
        return (ctx, opts) -> {
            Double value = ctx.doubleValue();
            Double total = ((Long) opts.param(0)).doubleValue();
            return (int) Math.ceil((value / total) * 100);
        };
    }
}
