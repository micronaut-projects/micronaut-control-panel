/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.crac.CracConfiguration;
import io.micronaut.crac.OrderedResource;
import io.micronaut.crac.resources.redis.CracRedisConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.crac.management.CRaCMXBean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads the CRaC state of the JVM and the application.
 *
 * <p>It never triggers a checkpoint or reads checkpoint files, and it only shows the class and bean name of each
 * resource, not its state, which can hold connection URLs or credentials.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
public class CracInspector {

    private static final Logger LOG = LoggerFactory.getLogger(CracInspector.class);

    private final BeanContext beanContext;
    private final CracEventRecorder recorder;

    /**
     * @param beanContext the bean context
     * @param recorder the checkpoint and restore event recorder
     */
    public CracInspector(BeanContext beanContext, CracEventRecorder recorder) {
        this.beanContext = beanContext;
        this.recorder = recorder;
    }

    /**
     * @return the CRaC state
     */
    public CracInfo inspect() {
        // Micronaut CRaC only creates its configuration, and registers resources, when crac.enabled is true
        Optional<CracConfiguration> configuration = beanContext.findBean(CracConfiguration.class).filter(CracConfiguration::isEnabled);
        long restoreTime = -1;
        long uptimeSinceRestore = -1;
        try {
            CRaCMXBean bean = CRaCMXBean.getCRaCMXBean();
            restoreTime = bean.getRestoreTime();
            uptimeSinceRestore = bean.getUptimeSinceRestore();
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("Unable to read the CRaC MXBean", e);
        }
        return new CracInfo(
            configuration.map(CracConfiguration::isEnabled).orElse(false),
            configuration.map(CracConfiguration::isRefreshBeans).orElse(false),
            configuration.map(c -> format(c.getDatasourcePauseTimeout())).orElse(null),
            jvmSupport(),
            checkpointConfigured(),
            restoreTime >= 0 ? Instant.ofEpochMilli(restoreTime).toString() : null,
            uptimeSinceRestore >= 0 ? format(Duration.ofMillis(uptimeSinceRestore)) : null,
            configuration.isPresent() ? resources() : List.of(),
            redis(),
            recorder.events()
        );
    }

    private static boolean jvmSupport() {
        ClassLoader classLoader = CracInspector.class.getClassLoader();
        return ClassUtils.isPresent("jdk.crac.Core", classLoader) || ClassUtils.isPresent("javax.crac.Core", classLoader);
    }

    private static boolean checkpointConfigured() {
        try {
            // Only checks whether the option is set: the checkpoint path is not read
            return ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(argument -> argument.startsWith("-XX:CRaCCheckpointTo"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private List<CracInfo.Resource> resources() {
        List<CracInfo.Resource> result = new ArrayList<>();
        try {
            for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(Qualifiers.byType(OrderedResource.class))) {
                Object bean = registration.getBean();
                int order = bean instanceof Ordered ordered ? ordered.getOrder() : Ordered.LOWEST_PRECEDENCE;
                // Beans without a name, such as the HTTP server resource, report "Primary"
                String name = registration.getIdentifier().getName();
                result.add(new CracInfo.Resource(
                    registration.getBeanDefinition().getBeanType().getName(),
                    name == null || name.isBlank() || name.equals("Primary") ? null : name,
                    order
                ));
            }
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the CRaC resources", e);
        }
        result.sort(Comparator.comparingInt(CracInfo.Resource::order).thenComparing(CracInfo.Resource::type));
        return result;
    }

    private @Nullable List<String> redis() {
        if (!ClassUtils.isPresent("io.micronaut.configuration.lettuce.AbstractRedisConfiguration", CracInspector.class.getClassLoader())) {
            return null;
        }
        return beanContext.findBean(CracRedisConfiguration.class).map(redis -> {
            List<String> enabled = new ArrayList<>();
            if (redis.isEnabled()) {
                if (redis.isConnectionEnabled()) {
                    enabled.add("connections");
                }
                if (redis.isClientEnabled()) {
                    enabled.add("clients");
                }
                if (redis.isCacheEnabled()) {
                    enabled.add("caches");
                }
            }
            return enabled;
        }).orElse(null);
    }

    static String format(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1) {
            return duration.toNanos() / 1000 + "µs";
        }
        if (millis < 1000) {
            return millis + "ms";
        }
        long seconds = duration.toSeconds();
        if (seconds < 120) {
            return millis % 1000 == 0 ? seconds + "s" : String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
        }
        return duration.toMinutes() + "m " + duration.toSecondsPart() + "s";
    }
}
