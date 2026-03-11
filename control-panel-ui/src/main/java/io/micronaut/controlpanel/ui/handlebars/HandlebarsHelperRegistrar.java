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
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.views.ViewsConfigurationProperties;
import jakarta.inject.Singleton;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
@Singleton
class HandlebarsHelperRegistrar implements BeanCreatedEventListener<Handlebars> {
    private static final Logger LOG = LoggerFactory.getLogger(HandlebarsHelperRegistrar.class);
    private static final int BINARY_PREFIX_BASE = 1024;
    private static final Locale BINARY_PREFIX_LOCALE = Locale.ROOT;
    private static final Pattern SPLIT_CAMEL = Pattern.compile("(?<=[A-Z])(?=[A-Z][a-z])|(?<=[^A-Z])(?=[A-Z])|(?<=[A-Za-z])(?=[^A-Za-z])");
    private static final Pattern WHITESPACE_OR_UNDERSCORE = Pattern.compile("[\\s_]+");
    private static final String SPACE = " ";
    private static final Map<Long, String> BINARY_PREFIXES = binaryPrefixes();
    private static final List<String> TITLE_IGNORED_WORDS = List.of(
        "a", "an", "and", "but", "nor", "it", "the", "to", "with", "in", "on", "of",
        "up", "or", "at", "into", "onto", "by", "from", "then", "for", "via", "versus"
    );

    private final ViewsConfigurationProperties viewsConfiguration;

    HandlebarsHelperRegistrar(final ViewsConfigurationProperties viewsConfiguration) {
        this.viewsConfiguration = viewsConfiguration;
    }

    @Override
    public Handlebars onCreated(@NonNull BeanCreatedEvent<Handlebars> event) {
        Handlebars handlebars = event.getBean();
        handlebars.registerHelper("binaryPrefix", binaryPrefixHelper());
        handlebars.registerHelper("decamelize", decamelizeHelper());
        handlebars.registerHelper("titleize", titleizeHelper());
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelpers(StringHelpers.class);
        handlebars.registerHelper("percentage", percentageHelper());
        handlebars.registerHelper("minus", minusHelper());
        handlebars.registerHelper("mod", modHelper());
        handlebars.registerHelper("size", (ctx, opts) -> ((Collection<?>) ctx).size());
        handlebars.registerHelper("isMap", isMapHelper());
        handlebars.registerHelper("partialExists", partialExistsHelper(handlebars));
        handlebars.registerHelper("unwrapOptional", unwrapOptional());
        handlebars.registerHelper("formatReason", formatReason());

        // Enable a high concurrency template cache and precompile all templates/partials available on the classpath
        enableCache(handlebars);
        precompileAllTemplates(handlebars);

        return handlebars;
    }

    private static Helper<String> formatReason() {
        return (context, options) -> context
            .replace("[", "<code>")
            .replace("]", "</code>");
    }

    private static Helper<Object> binaryPrefixHelper() {
        return (context, options) -> {
            if (!(context instanceof final Number value)) {
                return context == null ? "" : context.toString();
            }
            long numeric = value.longValue();
            if (numeric < 0) {
                return value.toString();
            }
            if (numeric == 1) {
                return "1 byte";
            }
            DecimalFormat formatter = new DecimalFormat();
            formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(BINARY_PREFIX_LOCALE));
            for (Map.Entry<Long, String> entry : BINARY_PREFIXES.entrySet()) {
                long threshold = entry.getKey();
                if (threshold <= numeric) {
                    formatter.applyPattern(entry.getValue());
                    double result = numeric >= BINARY_PREFIX_BASE ? (double) numeric / threshold : numeric;
                    return stripTrailingZeros(formatter, formatter.format(result));
                }
            }
            return stripTrailingZeros(formatter, formatter.format(value));
        };
    }

    private static Helper<Object> decamelizeHelper() {
        return (context, options) -> {
            if (context == null) {
                return "";
            }
            String replacement = options.hash("replacement", SPACE);
            if (replacement == null) {
                replacement = SPACE;
            }
            return SPLIT_CAMEL.matcher(context.toString()).replaceAll(Matcher.quoteReplacement(replacement));
        };
    }

    private static Helper<Object> titleizeHelper() {
        return (context, options) -> {
            if (context == null) {
                return "";
            }
            String normalized = WHITESPACE_OR_UNDERSCORE.matcher(context.toString().toLowerCase(Locale.ENGLISH))
                .replaceAll(SPACE)
                .trim();
            if (normalized.isEmpty()) {
                return "";
            }
            String[] words = normalized.split(SPACE);
            StringBuilder out = new StringBuilder(normalized.length());
            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                if (i > 0 && i < words.length - 1 && TITLE_IGNORED_WORDS.contains(word)) {
                    out.append(word);
                } else {
                    out.append(capitalizeWord(word));
                }
                if (i < words.length - 1) {
                    out.append(SPACE);
                }
            }
            return out.toString();
        };
    }

    private static String capitalizeWord(String word) {
        if (word.isEmpty()) {
            return word;
        }
        if (word.length() == 1) {
            return word.toUpperCase(Locale.ENGLISH);
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static String stripTrailingZeros(DecimalFormat decimalFormat, String formatted) {
        String trailingZeros = decimalFormat.getDecimalFormatSymbols().getDecimalSeparator() + "00";
        return formatted.replace(trailingZeros, "");
    }

    private static Map<Long, String> binaryPrefixes() {
        Map<Long, String> prefixes = new LinkedHashMap<>();
        prefixes.put(1125899906842624L, "#.## PB");
        prefixes.put(1099511627776L, "#.## TB");
        prefixes.put(1073741824L, "#.## GB");
        prefixes.put(1048576L, "#.## MB");
        prefixes.put(1024L, "#.# KB");
        prefixes.put(0L, "# bytes");
        return prefixes;
    }

    private static Helper<Object> unwrapOptional() {
        return (context, options) -> {
            if (context instanceof final Optional<?> opt) {
                return opt.orElse(null);
            }
            return context != null ? context : "";
        };
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

    private static Helper<Object> isMapHelper() {
        return (ctx, opts) -> {
            if (ctx instanceof Map<?, ?>) {
                return opts.fn();
            } else {
                return opts.inverse();
            }
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
        if (NativeImageUtils.inImageCode()) {
            //Skip pre-compilation in a native image
            return;
        }
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
