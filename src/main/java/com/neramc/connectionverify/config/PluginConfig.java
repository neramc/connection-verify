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
package com.neramc.connectionverify.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Typed, validated view over {@code config.yml}. All access to configuration
 * values goes through this class so defaults and bounds are applied in one
 * place. Instances are immutable; a fresh one is created on every reload.
 */
public final class PluginConfig {

    /** Output format for saved logs. */
    public enum FileFormat {
        TEXT("txt"),
        JSON("json");

        private final String extension;

        FileFormat(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        static FileFormat fromString(String raw) {
            if (raw != null && raw.trim().equalsIgnoreCase("json")) {
                return JSON;
            }
            return TEXT;
        }
    }

    private final int configVersion;
    private final String language;
    private final boolean debug;

    private final int numberLength;
    private final boolean leadingZeros;

    private final boolean logSuccessful;
    private final boolean logFailed;
    private final boolean networkDrops;

    private final boolean announceSuccessful;
    private final boolean announceFailed;
    private final boolean useColors;

    private final String logFolder;
    private final FileFormat fileFormat;
    private final boolean overwriteExisting;
    private final boolean asyncWrite;

    private final CaptureSettings captureSettings;

    private final int maxInMemory;
    private final long expireAfterMinutes;

    private final boolean updateCheckerEnabled;

    public PluginConfig(FileConfiguration config) {
        this.configVersion = config.getInt("config-version", 1);
        this.language = sanitizeLanguage(config.getString("language", "en"));
        this.debug = config.getBoolean("debug", false);

        this.numberLength = clamp(config.getInt("connection-number.length", 4), 1, 9);
        this.leadingZeros = config.getBoolean("connection-number.allow-leading-zeros", true);

        this.logSuccessful = config.getBoolean("logging.successful-connections", true);
        this.logFailed = config.getBoolean("logging.failed-connections", true);
        this.networkDrops = config.getBoolean("logging.network-drops", true);

        this.announceSuccessful = config.getBoolean("console.announce-successful", true);
        this.announceFailed = config.getBoolean("console.announce-failed", true);
        this.useColors = config.getBoolean("console.use-colors", true);

        this.logFolder = sanitizeFolder(config.getString("file.folder", "connection"));
        this.fileFormat = FileFormat.fromString(config.getString("file.format", "text"));
        this.overwriteExisting = config.getBoolean("file.overwrite-existing", true);
        this.asyncWrite = config.getBoolean("file.async-write", true);

        this.captureSettings = new CaptureSettings(
                config.getBoolean("capture.identity", true),
                config.getBoolean("capture.network", true),
                config.getBoolean("capture.client-options", true),
                config.getBoolean("capture.session", true),
                config.getBoolean("capture.player-state", true),
                config.getBoolean("capture.location", true),
                config.getBoolean("capture.world", true),
                config.getBoolean("capture.server", true),
                config.getBoolean("capture.runtime", true),
                config.getBoolean("capture.system", true),
                config.getBoolean("privacy.mask-ip", false),
                clamp(config.getInt("privacy.mask-ip-segments", 2), 1, 4),
                config.getBoolean("privacy.hide-uuid", false));

        this.maxInMemory = Math.max(0, config.getInt("records.max-in-memory", 1000));
        this.expireAfterMinutes = Math.max(0L, config.getLong("records.expire-after-minutes", 0L));

        this.updateCheckerEnabled = config.getBoolean("update-checker.enabled", false);
    }

    public int configVersion() {
        return configVersion;
    }

    public String language() {
        return language;
    }

    public boolean debug() {
        return debug;
    }

    public int numberLength() {
        return numberLength;
    }

    public boolean leadingZeros() {
        return leadingZeros;
    }

    public boolean logSuccessful() {
        return logSuccessful;
    }

    public boolean logFailed() {
        return logFailed;
    }

    /**
     * Whether to catch raw connection drops from the server log (the nameless
     * {@code /<ip>:<port> lost connection: ...} lines that never reach a Bukkit
     * event). Also gated by {@link #logFailed()}.
     */
    public boolean networkDrops() {
        return networkDrops;
    }

    public boolean announceSuccessful() {
        return announceSuccessful;
    }

    public boolean announceFailed() {
        return announceFailed;
    }

    public boolean useColors() {
        return useColors;
    }

    public String logFolder() {
        return logFolder;
    }

    public FileFormat fileFormat() {
        return fileFormat;
    }

    public boolean overwriteExisting() {
        return overwriteExisting;
    }

    public boolean asyncWrite() {
        return asyncWrite;
    }

    public CaptureSettings captureSettings() {
        return captureSettings;
    }

    public int maxInMemory() {
        return maxInMemory;
    }

    public long expireAfterMinutes() {
        return expireAfterMinutes;
    }

    public long expireAfterMillis() {
        return expireAfterMinutes * 60_000L;
    }

    public boolean updateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "en";
        }
        // Restrict to a safe file-name token (prevents path traversal via config).
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isEmpty() ? "en" : cleaned;
    }

    private static String sanitizeFolder(String raw) {
        if (raw == null || raw.isBlank()) {
            return "connection";
        }
        // Keep the folder inside the data directory: no separators or traversal.
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.isEmpty() ? "connection" : cleaned;
    }
}
