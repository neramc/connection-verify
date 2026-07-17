/*
 * Copyright 2026 neramc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neramc.connectionverify.fabric;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Locale;

/** Platform-neutral JVM/OS/system detail for the server side of a report. */
public final class SystemInfo {

    private SystemInfo() {
    }

    public static String text() {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        StringBuilder sb = new StringBuilder();
        sb.append("Java: ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("JVM: ").append(runtimeBean.getVmName()).append(' ').append(runtimeBean.getVmVersion()).append('\n');
        sb.append("OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("Processors: ").append(runtime.availableProcessors()).append('\n');
        sb.append("Max memory: ").append(mb(runtime.maxMemory()))
                .append(", used: ").append(mb(runtime.totalMemory() - runtime.freeMemory())).append('\n');
        sb.append("Uptime: ").append(runtimeBean.getUptime() / 1000L).append("s\n");
        return sb.toString();
    }

    private static String mb(long value) {
        return String.format(Locale.ROOT, "%.1f MB", value / 1048576.0D);
    }
}
