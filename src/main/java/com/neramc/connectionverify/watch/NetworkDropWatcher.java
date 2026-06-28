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
package com.neramc.connectionverify.watch;

import com.neramc.connectionverify.ConnectionVerifyPlugin;
import com.neramc.connectionverify.capture.ConnectionSnapshot;
import com.neramc.connectionverify.config.CaptureSettings;
import com.neramc.connectionverify.config.PluginConfig;
import com.neramc.connectionverify.connection.ConnectionRecord;
import com.neramc.connectionverify.i18n.Messages;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches raw connection drops that never reach a Bukkit/Paper event and gives
 * them a connection number all the same.
 *
 * <p>When a socket is closed before a profile is negotiated, the server logs a
 * line such as:</p>
 *
 * <pre>/203.0.113.7:51234 lost connection: Connection failed. Please try again ...</pre>
 *
 * <p>No {@code AsyncPlayerPreLoginEvent} (which needs a name) or
 * {@code PlayerConnectionCloseEvent} (which needs a validated profile) ever
 * fires for these, so the only place they surface is the server log. This
 * lightweight Log4j2 appender is attached to the root logger, matches exactly
 * those nameless lines (the leading {@code /} distinguishes them from named
 * connections, which are already handled by the listener and would otherwise be
 * counted twice), and registers a {@link ConnectionRecord} for each.</p>
 *
 * <p>Log4j calls {@link #append(LogEvent)} on whichever thread emitted the line
 * (typically a Netty I/O thread), so all Bukkit work is bounced to the main
 * thread. The plugin's own "Connection number" output does not contain
 * {@code lost connection}, so it cannot feed back into this appender.</p>
 */
public final class NetworkDropWatcher extends AbstractAppender {

    /** Unique appender name used when attaching to / detaching from the root logger. */
    private static final String APPENDER_NAME = "ConnectionVerify-NetworkDropWatcher";

    /** Cheap pre-filter so the regex only runs on candidate lines. */
    private static final String NEEDLE = "lost connection";

    /**
     * Matches only nameless raw drops: {@code /<address> lost connection: <reason>}.
     * Named connections are logged as {@code Name (/ip) lost connection: ...} and
     * therefore never match the leading {@code /}.
     */
    private static final Pattern DROP_PATTERN =
            Pattern.compile("^/(\\S+) lost connection: (.+)$");

    private final ConnectionVerifyPlugin plugin;

    private NetworkDropWatcher(ConnectionVerifyPlugin plugin) {
        super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
        this.plugin = plugin;
    }

    /**
     * Builds, starts and attaches a watcher to the root logger.
     *
     * @return the attached watcher, or {@code null} if log4j-core is unavailable
     *         on this server build (the plugin keeps working without it).
     */
    public static NetworkDropWatcher install(ConnectionVerifyPlugin plugin) {
        try {
            NetworkDropWatcher watcher = new NetworkDropWatcher(plugin);
            watcher.start();
            rootLogger().addAppender(watcher);
            return watcher;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not install the network-drop watcher; raw connection drops "
                            + "(\"/<ip>:<port> lost connection: ...\") will not be numbered.", throwable);
            return null;
        }
    }

    /** Detaches and stops this watcher. Safe to call more than once. */
    public void uninstall() {
        try {
            rootLogger().removeAppender(this);
        } catch (Throwable ignored) {
            // Logging subsystem already torn down - nothing more to do.
        } finally {
            stop();
        }
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) {
            return;
        }
        String message;
        try {
            message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
        } catch (Throwable throwable) {
            return;
        }
        if (message == null || message.indexOf(NEEDLE) < 0) {
            return;
        }
        Matcher matcher = DROP_PATTERN.matcher(message.trim());
        if (!matcher.matches()) {
            return;
        }
        schedule(matcher.group(1), matcher.group(2));
    }

    /** Bounces capture + registration onto the main server thread. */
    private void schedule(String address, String reason) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> report(address, reason));
        } catch (Throwable ignored) {
            // Server shutting down or scheduler unavailable; drop it silently.
        }
    }

    private void report(String address, String reason) {
        PluginConfig config = plugin.config();
        if (!config.logFailed() || !config.networkDrops()) {
            return;
        }
        CaptureSettings settings = config.captureSettings();
        ConnectionRecord record = ConnectionSnapshot.forNetworkDrop(address, reason, plugin, settings);
        String number = plugin.registry().register(record);
        if (config.announceFailed()) {
            Messages messages = plugin.messages();
            messages.console("console.network-drop",
                    Messages.placeholder("address", displayAddress(settings, address)),
                    Messages.placeholder("reason", reason));
            messages.console("console.connection-number", Messages.placeholder("number", number));
        }
    }

    /** Masks the host (per privacy settings) but keeps the port for the console line. */
    private static String displayAddress(CaptureSettings settings, String rawAddress) {
        String[] hostPort = ConnectionSnapshot.splitHostPort(rawAddress);
        String host = settings.maskIpAddress(hostPort[0]);
        if (host == null) {
            host = "(unknown)";
        }
        return hostPort[1] == null ? host : host + ":" + hostPort[1];
    }

    private static org.apache.logging.log4j.core.Logger rootLogger() {
        return (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
    }
}
