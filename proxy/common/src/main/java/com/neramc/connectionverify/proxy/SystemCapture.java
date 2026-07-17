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
package com.neramc.connectionverify.proxy;

import com.neramc.connectionverify.proxy.ConnectionRecord.Section;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.Locale;

/**
 * Platform-neutral capture of JVM runtime and host/system details, shared by
 * every proxy edition. Every field is captured defensively via the record's
 * value supplier, so a metric unavailable on a given JVM degrades gracefully.
 */
public final class SystemCapture {

    private static final double BYTES_PER_MB = 1024.0D * 1024.0D;

    private SystemCapture() {
    }

    /** Adds a "Runtime / Environment" section (Java, OS, CPU, memory, threads, uptime). */
    public static void runtime(ConnectionRecord record) {
        Runtime runtime = Runtime.getRuntime();
        Section section = record.section("Runtime / Environment");
        section.add("Java version", () -> System.getProperty("java.version"));
        section.add("Java vendor", () -> System.getProperty("java.vendor"));
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        section.add("JVM", () -> runtimeBean.getVmName() + " " + runtimeBean.getVmVersion());
        section.add("JVM vendor", runtimeBean::getVmVendor);
        section.add("Operating system", () -> System.getProperty("os.name")
                + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        section.add("Available processors", runtime::availableProcessors);
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        section.add("System load average", () -> {
            double load = os.getSystemLoadAverage();
            return load < 0 ? "(unavailable)" : String.format(Locale.ROOT, "%.2f", load);
        });
        section.add("Max memory", () -> bytes(runtime.maxMemory()));
        section.add("Used memory", () -> bytes(runtime.totalMemory() - runtime.freeMemory()));
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        section.add("Heap used", () -> bytes(memory.getHeapMemoryUsage().getUsed()));
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        section.add("Live threads", threads::getThreadCount);
        section.add("Server uptime", () -> duration(runtimeBean.getUptime()));
        section.add("JVM started", () -> ConnectionRecord.formatTimestamp(
                Instant.ofEpochMilli(runtimeBean.getStartTime())));
    }

    /** Adds a "System / host" section (data model, process, extended OS metrics). */
    public static void system(ConnectionRecord record) {
        Section section = record.section("System / host");
        section.add("Data model", () -> System.getProperty("sun.arch.data.model") + "-bit");
        section.add("Java home", () -> System.getProperty("java.home"));
        section.add("Working directory", () -> System.getProperty("user.dir"));
        section.add("Default locale", () -> Locale.getDefault().toString());
        ProcessHandle current = ProcessHandle.current();
        section.add("Process id", current::pid);
        try {
            com.sun.management.OperatingSystemMXBean ext =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            section.add("CPU load (system)", () -> percent(ext.getCpuLoad()));
            section.add("CPU load (process)", () -> percent(ext.getProcessCpuLoad()));
            section.add("Physical memory total", () -> bytes(ext.getTotalMemorySize()));
            section.add("Physical memory free", () -> bytes(ext.getFreeMemorySize()));
            section.add("Swap total", () -> bytes(ext.getTotalSwapSpaceSize()));
        } catch (Throwable ignored) {
            // Non-HotSpot JVM - extended metrics simply won't be captured.
        }
    }

    private static String percent(double fraction) {
        return fraction < 0 ? "(unavailable)" : String.format(Locale.ROOT, "%.1f%%", fraction * 100.0D);
    }

    private static String bytes(long value) {
        return String.format(Locale.ROOT, "%.1f MB (%d bytes)", value / BYTES_PER_MB, value);
    }

    private static String duration(long millis) {
        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m ").append(seconds).append('s');
        return sb + " (" + millis + " ms)";
    }
}
