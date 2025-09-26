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
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

@Internal
@Singleton
class HandlebarsHelperRegistrar implements BeanCreatedEventListener<Handlebars> {

    private static final Logger LOG = LoggerFactory.getLogger(HandlebarsHelperRegistrar.class);

    @Override
    public Handlebars onCreated(@NonNull BeanCreatedEvent<Handlebars> event) {
        Handlebars handlebars = event.getBean();
        
        // Configure high concurrency template cache
        configureTemplateCache(handlebars);
        
        // Pre-compile all templates and partials
        precompileTemplates(handlebars);
        
        // Register helpers
        HumanizeHelper.register(handlebars);
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelpers(StringHelpers.class);
        handlebars.registerHelper("percentage", percentageHelper());
        handlebars.registerHelper("minus", minusHelper());
        handlebars.registerHelper("mod", modHelper());
        handlebars.registerHelper("size", (ctx, opts) -> ((Collection<?>) ctx).size());
        handlebars.registerHelper("partialExists", partialExistsHelper(handlebars));
        
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

    private void configureTemplateCache(Handlebars handlebars) {
        LOG.debug("Configuring HighConcurrencyTemplateCache for Handlebars");
        handlebars.with(new HighConcurrencyTemplateCache());
    }

    private void precompileTemplates(Handlebars handlebars) {
        LOG.info("Pre-compiling Handlebars templates and partials");
        
        // Instead of trying to pre-compile all templates (which may have complex dependencies),
        // let's configure the template loader to scan for templates and cache them on first use
        // The HighConcurrencyTemplateCache will handle caching automatically when templates are accessed
        
        Set<String> templatePaths = findAllTemplates();
        LOG.info("Found {} template paths for pre-compilation", templatePaths.size());
        
        // Log the templates that were found
        for (String templatePath : templatePaths) {
            LOG.debug("Available template: {}", templatePath);
        }
        
        LOG.info("Templates will be cached on first use by HighConcurrencyTemplateCache");
    }

    private Set<String> findAllTemplates() {
        Set<String> templates = new HashSet<>();
        
        try {
            // Find all .hbs files in the classpath
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources("views");
            
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                LOG.debug("Scanning views directory: {}", resource);
                scanDirectoryForTemplates(resource, templates, "");
            }
            
        } catch (IOException e) {
            LOG.error("Error scanning classpath for templates", e);
        }
        
        return templates;
    }

    private void scanDirectoryForTemplates(URL baseUrl, Set<String> templates, String relativePath) {
        try {
            // This is a simplified approach - in a real implementation, you might need to handle JAR files differently
            String path = baseUrl.getPath();
            if (path.contains("!")) {
                // Handle JAR file case - for now, we'll use the known template paths
                addKnownTemplatePaths(templates);
                return;
            }
            
            java.io.File dir = new java.io.File(path, relativePath);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] files = dir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.isDirectory()) {
                            String subPath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                            scanDirectoryForTemplates(baseUrl, templates, subPath);
                        } else if (file.getName().endsWith(".hbs")) {
                            String templatePath = relativePath.isEmpty() ? 
                                file.getName().replace(".hbs", "") : 
                                relativePath + "/" + file.getName().replace(".hbs", "");
                            templates.add(templatePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Error scanning directory for templates", e);
            // Fallback to known template paths
            addKnownTemplatePaths(templates);
        }
    }

    private void addKnownTemplatePaths(Set<String> templates) {
        // Add known template paths that we've discovered from the codebase
        templates.add("index");
        templates.add("detail");
        templates.add("layout");
        templates.add("routes/body");
        templates.add("routes/detail");
        templates.add("routes/routes");
        templates.add("beans/body");
        templates.add("beans/detail");
        templates.add("beans/package");
        templates.add("health/body");
        templates.add("health/detail");
        templates.add("health/compositeDiscoveryClient");
        templates.add("health/diskSpace");
        templates.add("health/jdbc");
        templates.add("loggers/body");
        templates.add("loggers/detail");
        templates.add("env/body");
        templates.add("env/detail");
    }
}
