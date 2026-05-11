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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.reflect.ClassUtils;
import jakarta.inject.Singleton;
import org.crac.management.CRaCMXBean;

import java.util.function.Supplier;

/**
 * Detects CRaC API and restore metric availability without requiring a CRaC-enabled JDK.
 */
@Singleton
@Requires(classes = CRaCMXBean.class)
final class CracSupportDetector {

    private static final String CRAC_RESOURCE_CLASS = "org.crac.Resource";
    private static final String CRAC_MX_BEAN_CLASS = "org.crac.management.CRaCMXBean";
    private static final String MICRONAUT_ORDERED_RESOURCE_CLASS = "io.micronaut.crac.OrderedResource";

    private final ClassLoader classLoader;
    private final Supplier<CRaCMXBean> mxBeanSupplier;

    CracSupportDetector() {
        this(CracSupportDetector.class.getClassLoader(), CRaCMXBean::getCRaCMXBean);
    }

    CracSupportDetector(ClassLoader classLoader, Supplier<CRaCMXBean> mxBeanSupplier) {
        this.classLoader = classLoader;
        this.mxBeanSupplier = mxBeanSupplier;
    }

    CracDiagnostics.Support detect() {
        boolean cracApiPresent = isPresent(CRAC_RESOURCE_CLASS);
        boolean mxBeanPresent = isPresent(CRAC_MX_BEAN_CLASS);
        boolean micronautCracPresent = isPresent(MICRONAUT_ORDERED_RESOURCE_CLASS);
        boolean supported = cracApiPresent && micronautCracPresent;
        String message = supported
            ? "CRaC APIs and Micronaut CRaC resources are available."
            : "CRaC diagnostics are unavailable because CRaC APIs or Micronaut CRaC classes are missing.";
        return new CracDiagnostics.Support(cracApiPresent, mxBeanPresent, micronautCracPresent, supported, message);
    }

    CracDiagnostics.RestoreMetrics restoreMetrics() {
        if (!isPresent(CRAC_MX_BEAN_CLASS)) {
            return unavailableRestoreMetrics("CRaC MXBean class is not available on this JVM.");
        }
        try {
            CRaCMXBean mxBean = mxBeanSupplier.get();
            if (mxBean == null) {
                return unavailableRestoreMetrics("CRaC MXBean is not available on this JVM.");
            }
            long restoreTime = mxBean.getRestoreTime();
            long uptimeSinceRestore = mxBean.getUptimeSinceRestore();
            boolean restored = restoreTime > 0 || uptimeSinceRestore > 0;
            String message = restored ? "Restore metrics were read from the CRaC MXBean." : "No restore has been observed by the CRaC MXBean.";
            return new CracDiagnostics.RestoreMetrics(true, restored, formatMillis(restoreTime), formatMillis(uptimeSinceRestore), message);
        } catch (LinkageError | RuntimeException e) {
            return unavailableRestoreMetrics("CRaC MXBean metrics are not readable on this JVM.");
        }
    }

    private boolean isPresent(String className) {
        return ClassUtils.isPresent(className, classLoader);
    }

    static CracDiagnostics.RestoreMetrics unavailableRestoreMetrics(String message) {
        return new CracDiagnostics.RestoreMetrics(false, false, "Unavailable", "Unavailable", message);
    }

    static String formatMillis(long millis) {
        if (millis < 0) {
            return "Unavailable";
        }
        if (millis < 1000) {
            return millis + " ms";
        }
        long seconds = millis / 1000;
        long remainder = millis % 1000;
        if (seconds < 60) {
            return seconds + "." + String.format("%03d", remainder) + " s";
        }
        long minutes = seconds / 60;
        long secondRemainder = seconds % 60;
        return minutes + "m " + secondRemainder + "s";
    }

    static String formatNanos(long nanos) {
        if (nanos < 0) {
            return "Unavailable";
        }
        if (nanos < 1_000_000) {
            return nanos + " ns";
        }
        return formatMillis(nanos / 1_000_000);
    }
}
