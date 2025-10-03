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
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.views.ViewsConfigurationProperties;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
@Singleton
class HandlebarsHelperRegistrar implements BeanCreatedEventListener<Handlebars> {
    private static final Logger LOG = LoggerFactory.getLogger(HandlebarsHelperRegistrar.class);

    private final ViewsConfigurationProperties viewsConfiguration;

    HandlebarsHelperRegistrar(final ViewsConfigurationProperties viewsConfiguration) {
        this.viewsConfiguration = viewsConfiguration;
    }

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
        handlebars.registerHelper("partialExists", partialExistsHelper(handlebars));

        // Enable a high concurrency template cache and precompile all templates/partials available on the classpath
        enableCache(handlebars);
        precompileAllTemplates(handlebars);

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

    private static void enableCache(Handlebars handlebars) {
        handlebars.with(new HighConcurrencyTemplateCache());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Configured Handlebars with HighConcurrencyTemplateCache");
        }
    }

    private void precompileAllTemplates(Handlebars handlebars) {
        TemplateLoader loader = handlebars.getLoader();
        String prefix = viewsConfiguration.getFolder();
        String suffix = loader.getSuffix() != null ? loader.getSuffix() : ".hbs";

        Set<String> names = discoverTemplateNames(prefix, suffix);
        if (names.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No Handlebars templates discovered under prefix '{}'", prefix);
            }
            return;
        }

        int compiled = 0;
        for (String name : names) {
            try {
                // Compile by logical name (relative to the loader's prefix, without suffix)
                handlebars.compile(name);
                compiled++;
            } catch (Exception e) {
                // Don't fail startup on individual template failures; log at debug level
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to precompile template '{}': {}", name, e.getMessage());
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Precompiled {} Handlebars templates/partials from classpath (prefix='{}')", compiled, prefix);
        }
    }

    private Set<String> discoverTemplateNames(String prefix, String suffix) {
        Set<String> results = new HashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = HandlebarsHelperRegistrar.class.getClassLoader();
        }
        try {
            Enumeration<URL> roots = cl.getResources(prefix);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                String protocol = root.getProtocol();
                if ("file".equals(protocol)) {
                    // File-system resources (e.g., exploded classes)
                    processFileSystem(prefix, suffix, root, results);
                } else if ("jar".equals(protocol)) {
                    // Templates packaged inside JARs
                    processJar(prefix, suffix, root, results);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Unsupported URL protocol '{}' while scanning '{}'", protocol, root);
                    }
                }
            }
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to discover Handlebars templates from classpath: {}", e.getMessage());
            }
        }
        return results;
    }

    private static void processJar(final String prefix, final String suffix, final URL root, final Set<String> results) {
        try {
            JarURLConnection conn = (JarURLConnection) root.openConnection();
            String entryPrefix = conn.getEntryName();
            if (entryPrefix == null) {
                entryPrefix = prefix;
            }
            if (!entryPrefix.endsWith("/")) {
                entryPrefix = entryPrefix + "/";
            }
            try (JarFile jar = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry je = entries.nextElement();
                    String name = je.getName();
                    if (!je.isDirectory() && name.startsWith(entryPrefix) && name.endsWith(suffix)) {
                        String rel = name.substring(entryPrefix.length());
                        rel = rel.replace('\\', '/');
                        rel = stripSuffix(rel, suffix);
                        results.add(prefix + rel);
                    }
                }
            }
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error scanning JAR resources for Handlebars templates: {}", e.getMessage());
            }
        }
    }

    private void processFileSystem(final String prefix, final String suffix, final URL root, final Set<String> results) {
        try {
            Path dir = Paths.get(root.toURI());
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(suffix))
                        .forEach(p -> {
                            Path rel = dir.relativize(p);
                            String logical = rel.toString().replace('\\', '/');
                            logical = stripSuffix(logical, suffix);
                            results.add(prefix + logical);
                        });
                }
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error scanning file resources for Handlebars templates: {}", e.getMessage());
            }
        }
    }

    private static String stripSuffix(String name, String suffix) {
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }
}
