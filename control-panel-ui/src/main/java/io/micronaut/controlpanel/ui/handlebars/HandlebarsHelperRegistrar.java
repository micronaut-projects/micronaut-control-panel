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
import java.util.jar.JarFile;

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
                // Try to compile the template - this will use the cache if available
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
        LOG.debug("Pre-compiling Handlebars templates and partials");
        
        Set<String> templatePaths = findAllTemplates();
        LOG.debug("Found {} template paths for pre-compilation", templatePaths.size());
        
        // Actually pre-compile the templates to populate the cache
        int compiledCount = 0;
        for (String templatePath : templatePaths) {
            try {
                handlebars.compile(templatePath);
                compiledCount++;
                LOG.debug("Pre-compiled template: {}", templatePath);
            } catch (Exception e) {
                LOG.debug("Could not pre-compile template: {} - {}", templatePath, e.getMessage());
            }
        }
        
        LOG.debug("Pre-compiled {} templates successfully. Templates cached by HighConcurrencyTemplateCache", compiledCount);
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
            String path = baseUrl.getPath();
            
            if (path.contains("!")) {
                // Handle JAR file case - scan JAR entries
                scanJarForTemplates(baseUrl, templates);
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
            LOG.debug("Error scanning directory for templates: {}", e.getMessage());
        }
    }

    private void scanJarForTemplates(URL baseUrl, Set<String> templates) {
        try {
            // For JAR files, we need to scan the entries
            // This is a simplified implementation that uses the JAR file path
            String jarPath = baseUrl.toString();
            if (jarPath.startsWith("jar:file:") && jarPath.contains("!/views")) {
                // Extract jar file path and scan it
                String jarFilePath = jarPath.substring("jar:file:".length(), jarPath.indexOf("!/"));
                try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarFilePath)) {
                    jarFile.stream()
                        .filter(entry -> entry.getName().startsWith("views/") && entry.getName().endsWith(".hbs"))
                        .forEach(entry -> {
                            String templatePath = entry.getName()
                                .substring("views/".length())
                                .replace(".hbs", "");
                            templates.add(templatePath);
                        });
                }
            }
        } catch (Exception e) {
            LOG.debug("Error scanning JAR for templates: {}", e.getMessage());
        }
    }
}
