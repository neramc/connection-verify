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
package com.neramc.connectionverify.velocity;

import com.neramc.connectionverify.proxy.ConnectionRecord;
import com.neramc.connectionverify.proxy.ProxyConfig;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.UUID;

/**
 * Captures the life-cycle of every connection through the proxy and drives the
 * control-tower console output:
 *
 * <ul>
 *   <li>{@link PostLoginEvent} - a player logs in to the proxy (numbered).</li>
 *   <li>{@link ServerConnectedEvent} - routed to (or switched between) backends.</li>
 *   <li>{@link KickedFromServerEvent} - kicked by a backend.</li>
 *   <li>{@link DisconnectEvent} - left the proxy, or failed before logging in.</li>
 * </ul>
 */
public final class ProxyConnectionListener {

    private final ConnectionVerifyVelocity plugin;

    public ProxyConnectionListener(ConnectionVerifyVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        ProxyConfig config = plugin.config();
        ConnectionRecord record = ProxySnapshot.forConnect(player, plugin.proxy(), config, "PostLoginEvent");
        String number = plugin.registry().register(record);
        plugin.activeConnections().put(player.getUniqueId(), number);
        if (config.announceSuccessful()) {
            plugin.announceJoin(player.getUsername(), number);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String number = plugin.activeConnections().get(player.getUniqueId());
        if (number == null) {
            return;
        }
        String to = event.getServer().getServerInfo().getName();
        String from = event.getPreviousServer().map(s -> s.getServerInfo().getName()).orElse(null);
        plugin.registry().addRouting(number,
                (from == null ? "Connected to" : "Switched to") + " @ "
                        + ConnectionRecord.formatTimestamp(java.time.Instant.now()),
                from == null ? to : from + " -> " + to);
        if (plugin.config().announceRouting()) {
            plugin.announceRouting(player.getUsername(), from, to, number);
        }
    }

    @Subscribe
    public void onKicked(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        String number = plugin.activeConnections().get(player.getUniqueId());
        if (number == null) {
            return;
        }
        String server = event.getServer().getServerInfo().getName();
        plugin.registry().addRouting(number,
                "Kicked from @ " + ConnectionRecord.formatTimestamp(java.time.Instant.now()), server);
        if (plugin.config().announceRouting()) {
            plugin.announceKick(player.getUsername(), server, null, number);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String number = plugin.activeConnections().remove(uuid);
        ProxyConfig config = plugin.config();

        if (number != null) {
            // A player who successfully logged in has now left the network.
            if (config.announceRouting()) {
                plugin.announceLeft(player.getUsername(), status(event), number);
            }
            return;
        }
        // No number means the connection failed before it ever completed login.
        if (!config.logFailed()) {
            return;
        }
        ConnectionRecord record = ProxySnapshot.forFailure(player.getUsername(), player.getRemoteAddress(),
                status(event), plugin.proxy(), config, "DisconnectEvent");
        String failureNumber = plugin.registry().register(record);
        if (config.announceFailed()) {
            plugin.announceFailure(player.getUsername(), status(event), failureNumber);
        }
    }

    private static String status(DisconnectEvent event) {
        try {
            return event.getLoginStatus().name();
        } catch (Throwable throwable) {
            return "DISCONNECTED";
        }
    }
}
