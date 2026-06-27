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
package com.neramc.connectionverify.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.neramc.connectionverify.ConnectionVerifyPlugin;
import com.neramc.connectionverify.capture.ConnectionSnapshot;
import com.neramc.connectionverify.config.PluginConfig;
import com.neramc.connectionverify.connection.ConnectionRecord;
import com.neramc.connectionverify.i18n.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Captures every connection attempt, stores it under a connection number, and
 * announces that number on the console.
 *
 * <ul>
 *   <li>{@link PlayerJoinEvent} - successful joins.</li>
 *   <li>{@link AsyncPlayerPreLoginEvent} - pre-login denials (ban, whitelist,
 *       server full).</li>
 *   <li>{@link PlayerLoginEvent} - login denials (plugin disallows).</li>
 *   <li>{@link PlayerConnectionCloseEvent} - connections dropped without an
 *       explicit deny (network errors, timeouts, generic disconnects) after
 *       authentication but before joining the world.</li>
 * </ul>
 *
 * <p>Each attempt yields exactly one number: a connection is rejected at a
 * single stage, the connection-close catch-all skips players who actually
 * joined (normal quits), and it de-duplicates against failures already reported
 * by the login stage.</p>
 */
public final class ConnectionListener implements Listener {

    private final ConnectionVerifyPlugin plugin;

    /** UUIDs of players who joined the world (used to ignore normal quits). */
    private final Set<UUID> joinedConnections = ConcurrentHashMap.newKeySet();
    /** UUIDs already reported as failed by the login stage (close de-duplication). */
    private final Set<UUID> reportedFailures = ConcurrentHashMap.newKeySet();

    public ConnectionListener(ConnectionVerifyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        joinedConnections.add(event.getPlayer().getUniqueId());
        PluginConfig config = plugin.config();
        if (!config.logSuccessful()) {
            return;
        }
        ConnectionRecord record = ConnectionSnapshot.forJoin(event, plugin, config.captureSettings());
        String number = plugin.registry().register(record);
        if (config.announceSuccessful()) {
            announceSuccess(event.getPlayer().getName(), number);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        PluginConfig config = plugin.config();
        if (!config.logFailed() || event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        // Pre-login denials never produce a PlayerConnectionCloseEvent, so report
        // directly without the de-duplication set.
        ConnectionRecord record = ConnectionSnapshot.forPreLogin(event, plugin, config.captureSettings());
        String number = plugin.registry().register(record);
        if (config.announceFailed()) {
            announceFailure(event.getName(), event.getLoginResult().name(), number);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        PluginConfig config = plugin.config();
        if (!config.logFailed() || event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        reportFailure(event.getPlayer().getUniqueId(), config,
                () -> ConnectionSnapshot.forLogin(event, plugin, config.captureSettings()),
                event.getPlayer().getName(), event.getResult().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        UUID uuid = event.getPlayerUniqueId();
        boolean hadJoined = joinedConnections.remove(uuid);
        boolean alreadyReported = reportedFailures.remove(uuid);
        PluginConfig config = plugin.config();
        if (hadJoined || alreadyReported || !config.logFailed()) {
            return;
        }
        ConnectionRecord record = ConnectionSnapshot.forConnectionClose(event, plugin, config.captureSettings());
        String number = plugin.registry().register(record);
        if (config.announceFailed()) {
            announceFailure(event.getPlayerName(), "CONNECTION_LOST", number);
        }
    }

    private void reportFailure(UUID uuid, PluginConfig config, Supplier<ConnectionRecord> recordSupplier,
                               String name, String reason) {
        if (uuid != null && !reportedFailures.add(uuid)) {
            return;
        }
        ConnectionRecord record = recordSupplier.get();
        String number = plugin.registry().register(record);
        if (config.announceFailed()) {
            announceFailure(name, reason, number);
        }
    }

    private void announceSuccess(String name, String number) {
        Messages messages = plugin.messages();
        messages.console("console.join", Messages.placeholder("name", safe(name)));
        messages.console("console.connection-number", Messages.placeholder("number", number));
    }

    private void announceFailure(String name, String reason, String number) {
        Messages messages = plugin.messages();
        messages.console("console.failed",
                Messages.placeholder("name", safe(name)),
                Messages.placeholder("reason", reason));
        messages.console("console.connection-number", Messages.placeholder("number", number));
    }

    private static String safe(String name) {
        return (name == null || name.isEmpty()) ? "?" : name;
    }
}
