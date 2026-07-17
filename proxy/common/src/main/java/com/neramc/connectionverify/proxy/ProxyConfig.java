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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Small, self-contained configuration for the Velocity edition, stored as
 * {@code config.properties} in the plugin's data directory. A commented default
 * file is written on first run.
 */
public final class ProxyConfig {

    private final int numberLength;
    private final boolean leadingZeros;
    private final boolean announceSuccessful;
    private final boolean announceFailed;
    private final boolean announceRouting;
    private final boolean logFailed;
    private final String logFolder;
    private final boolean overwriteExisting;
    private final boolean maskIp;
    private final int maskIpSegments;
    private final int maxInMemory;

    private ProxyConfig(Properties p) {
        this.numberLength = clamp(intValue(p, "connection-number.length", 4), 1, 9);
        this.leadingZeros = boolValue(p, "connection-number.allow-leading-zeros", true);
        this.announceSuccessful = boolValue(p, "console.announce-successful", true);
        this.announceFailed = boolValue(p, "console.announce-failed", true);
        this.announceRouting = boolValue(p, "console.announce-routing", true);
        this.logFailed = boolValue(p, "logging.failed-connections", true);
        this.logFolder = sanitizeFolder(p.getProperty("file.folder", "connection"));
        this.overwriteExisting = boolValue(p, "file.overwrite-existing", true);
        this.maskIp = boolValue(p, "privacy.mask-ip", false);
        this.maskIpSegments = clamp(intValue(p, "privacy.mask-ip-segments", 2), 1, 4);
        this.maxInMemory = Math.max(0, intValue(p, "records.max-in-memory", 1000));
    }

    /** Loads the config from the data directory, writing defaults if absent. */
    public static ProxyConfig load(Path dataDirectory) {
        Properties props = new Properties();
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.properties");
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                }
            } else {
                writeDefault(file);
            }
        } catch (IOException ignored) {
            // Fall back to built-in defaults.
        }
        return new ProxyConfig(props);
    }

    private static void writeDefault(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("connection-number.length", "4");
        p.setProperty("connection-number.allow-leading-zeros", "true");
        p.setProperty("console.announce-successful", "true");
        p.setProperty("console.announce-failed", "true");
        p.setProperty("console.announce-routing", "true");
        p.setProperty("logging.failed-connections", "true");
        p.setProperty("file.folder", "connection");
        p.setProperty("file.overwrite-existing", "true");
        p.setProperty("privacy.mask-ip", "false");
        p.setProperty("privacy.mask-ip-segments", "2");
        p.setProperty("records.max-in-memory", "1000");
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Connection Verify (proxy) - control-tower configuration");
        }
    }

    public int numberLength() {
        return numberLength;
    }

    public boolean leadingZeros() {
        return leadingZeros;
    }

    public boolean announceSuccessful() {
        return announceSuccessful;
    }

    public boolean announceFailed() {
        return announceFailed;
    }

    public boolean announceRouting() {
        return announceRouting;
    }

    public boolean logFailed() {
        return logFailed;
    }

    public String logFolder() {
        return logFolder;
    }

    public boolean overwriteExisting() {
        return overwriteExisting;
    }

    public int maxInMemory() {
        return maxInMemory;
    }

    /** Masks an IPv4 address by replacing the configured number of trailing segments. */
    public String maskIpAddress(String ip) {
        if (ip == null || !maskIp) {
            return ip;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return "(redacted)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(i >= 4 - maskIpSegments ? "x" : parts[i]);
        }
        return sb.toString();
    }

    private static int intValue(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean boolValue(Properties p, String key, boolean def) {
        String raw = p.getProperty(key);
        return raw == null ? def : Boolean.parseBoolean(raw.trim());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizeFolder(String raw) {
        if (raw == null || raw.isBlank()) {
            return "connection";
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isEmpty() ? "connection" : cleaned;
    }
}
