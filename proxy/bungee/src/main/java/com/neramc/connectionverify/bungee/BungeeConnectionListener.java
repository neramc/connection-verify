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
package com.neramc.connectionverify.bungee;

import com.neramc.connectionverify.proxy.ConnectionRecord;
import com.neramc.connectionverify.proxy.ProxyConfig;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.time.Instant;

/**
 * Drives the control-tower flow on BungeeCord/Waterfall:
 *
 * <ul>
 *   <li>{@link PostLoginEvent} - a player logs in to the proxy (numbered).</li>
 *   <li>{@link ServerSwitchEvent} - routed to (or switched between) backends.</li>
 *   <li>{@link ServerKickEvent} - kicked by a backend.</li>
 *   <li>{@link PlayerDisconnectEvent} - left the proxy.</li>
 *   <li>{@link LoginEvent} - a login denied by another plugin (numbered failure).</li>
 * </ul>
 */
public final class BungeeConnectionListener implements Listener {

    private final ConnectionVerifyBungee plugin;

    public BungeeConnectionListener(ConnectionVerifyBungee plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        ProxyConfig config = plugin.config();
        ConnectionRecord record = BungeeSnapshot.forConnect(player, plugin.getProxy(), config, "PostLoginEvent");
        String number = plugin.registry().register(record);
        plugin.activeConnections().put(player.getUniqueId(), number);
        if (config.announceSuccessful()) {
            plugin.announceJoin(player.getName(), number);
        }
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String number = plugin.activeConnections().get(player.getUniqueId());
        if (number == null) {
            return;
        }
        String to = player.getServer() == null ? "?" : player.getServer().getInfo().getName();
        String from = event.getFrom() == null ? null : event.getFrom().getName();
        plugin.registry().addRouting(number,
                (from == null ? "Connected to" : "Switched to") + " @ "
                        + ConnectionRecord.formatTimestamp(Instant.now()),
                from == null ? to : from + " -> " + to);
        if (plugin.config().announceRouting()) {
            plugin.announceRouting(player.getName(), from, to, number);
        }
    }

    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String number = plugin.activeConnections().get(player.getUniqueId());
        if (number == null) {
            return;
        }
        String server = event.getKickedFrom() == null ? "?" : event.getKickedFrom().getName();
        plugin.registry().addRouting(number,
                "Kicked from @ " + ConnectionRecord.formatTimestamp(Instant.now()), server);
        if (plugin.config().announceRouting()) {
            plugin.announceKick(player.getName(), server, null, number);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String number = plugin.activeConnections().remove(player.getUniqueId());
        if (number != null && plugin.config().announceRouting()) {
            plugin.announceLeft(player.getName(), number);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        ProxyConfig config = plugin.config();
        if (!config.logFailed()) {
            return;
        }
        PendingConnection connection = event.getConnection();
        String name = connection == null ? null : connection.getName();
        String reason = "Login denied (cancelled by another plugin)";
        ConnectionRecord record = BungeeSnapshot.forFailure(name,
                connection == null ? null : connection.getSocketAddress(),
                reason, plugin.getProxy(), config, "LoginEvent (cancelled)");
        String number = plugin.registry().register(record);
        if (config.announceFailed()) {
            plugin.announceFailure(name, reason, number);
        }
    }
}
