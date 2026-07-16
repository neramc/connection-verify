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
package com.neramc.connectionverify.update;

import com.neramc.connectionverify.i18n.Messages;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opt-in, privacy-friendly update checker. When enabled it asks the public
 * Modrinth API once whether a newer version exists and logs the result. No
 * information about the server is transmitted; only a standard request is made.
 */
public final class UpdateChecker {

    private static final String PROJECT_SLUG = "connection-verify";
    private static final String VERSIONS_ENDPOINT =
            "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String DOWNLOAD_PAGE = "https://modrinth.com/plugin/" + PROJECT_SLUG;
    private static final Pattern VERSION_NUMBER = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final Messages messages;
    private final String currentVersion;

    public UpdateChecker(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /** Performs the check asynchronously; results are logged on a tick thread. */
    public void checkAsync() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> check());
    }

    private void check() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(VERSIONS_ENDPOINT))
                    .header("User-Agent", "neramc/connection-verify/" + currentVersion + " (update-checker)")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                report(() -> messages.console("update.failed",
                        Messages.placeholder("error", "HTTP " + response.statusCode())));
                return;
            }

            Matcher matcher = VERSION_NUMBER.matcher(response.body());
            if (!matcher.find()) {
                report(() -> messages.console("update.up-to-date",
                        Messages.placeholder("current", currentVersion)));
                return;
            }

            // Modrinth publishes one version per build target, so the version
            // number carries a build-metadata suffix (e.g. 2.0.7+mc26). Compare
            // only the base version so the plain plugin version still matches.
            String latest = baseVersion(matcher.group(1));
            if (latest.equalsIgnoreCase(baseVersion(currentVersion))) {
                report(() -> messages.console("update.up-to-date",
                        Messages.placeholder("current", currentVersion)));
            } else {
                report(() -> messages.console("update.available",
                        Messages.placeholder("latest", latest),
                        Messages.placeholder("current", currentVersion),
                        Messages.placeholder("url", DOWNLOAD_PAGE)));
            }
        } catch (Exception exception) {
            report(() -> messages.console("update.failed",
                    Messages.placeholder("error", exception.getClass().getSimpleName())));
        }
    }

    private void report(Runnable action) {
        if (plugin.isEnabled()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> action.run());
        }
    }

    /** Strips SemVer build metadata (everything from {@code +}) and surrounding space. */
    private static String baseVersion(String version) {
        if (version == null) {
            return "";
        }
        int plus = version.indexOf('+');
        return (plus >= 0 ? version.substring(0, plus) : version).trim();
    }
}
