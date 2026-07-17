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
import com.neramc.connectionverify.proxy.ConnectionRecord.Section;
import com.neramc.connectionverify.proxy.ConnectionRecord.Status;
import com.neramc.connectionverify.proxy.ProxyConfig;
import com.neramc.connectionverify.proxy.SystemCapture;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.stream.Collectors;

/**
 * Builds detailed {@link ConnectionRecord}s from what a BungeeCord/Waterfall
 * proxy knows about a connection: the peer's identity and network details, and
 * the proxy's network-wide view (every registered backend + player counts).
 */
public final class BungeeSnapshot {

    private BungeeSnapshot() {
    }

    public static ConnectionRecord forConnect(ProxiedPlayer player, ProxyServer proxy, ProxyConfig config,
                                              String event) {
        ConnectionRecord record = new ConnectionRecord(Status.SUCCESS);
        captureContext(record, event);
        captureIdentity(record, player);
        captureNetwork(record, player, config);
        captureProxyNetwork(record, proxy);
        record.section("Routing"); // populated live as the player is routed to backends
        SystemCapture.runtime(record);
        SystemCapture.system(record);
        return record;
    }

    public static ConnectionRecord forFailure(String username, SocketAddress address, String reason,
                                              ProxyServer proxy, ProxyConfig config, String event) {
        ConnectionRecord record = new ConnectionRecord(Status.FAILED);
        captureContext(record, event);

        Section identity = record.section("Identity");
        identity.add("Name", username);

        Section network = record.section("Network");
        network.add("IP address", () -> config.maskIpAddress(host(address)));

        Section result = record.section("Result");
        result.add("Outcome", "Connection failed / denied at the proxy");
        result.add("Reason", reason);

        captureProxyNetwork(record, proxy);
        SystemCapture.runtime(record);
        SystemCapture.system(record);
        return record;
    }

    private static void captureContext(ConnectionRecord record, String event) {
        Section context = record.section("Capture");
        context.add("Event", event);
        context.add("Captured on thread", () -> Thread.currentThread().getName());
        context.add("Edition", "BungeeCord proxy (control tower)");
    }

    private static void captureIdentity(ConnectionRecord record, ProxiedPlayer player) {
        Section identity = record.section("Identity");
        identity.add("Name", player::getName);
        identity.add("UUID", () -> player.getUniqueId().toString());
        identity.add("UUID version", () -> player.getUniqueId().version());
        identity.add("Online mode", () -> player.getPendingConnection().isOnlineMode());
    }

    private static void captureNetwork(ConnectionRecord record, ProxiedPlayer player, ProxyConfig config) {
        Section network = record.section("Network");
        PendingConnection connection = player.getPendingConnection();
        network.add("IP address", () -> config.maskIpAddress(host(connection.getSocketAddress())));
        network.add("Virtual host", () -> {
            InetSocketAddress virtualHost = connection.getVirtualHost();
            return virtualHost == null ? null : virtualHost.getHostString() + ":" + virtualHost.getPort();
        });
        network.add("Protocol version", connection::getVersion);
        network.add("Ping (ms)", player::getPing);
        network.add("Locale", () -> player.getLocale() == null ? null : player.getLocale().toString());
        network.add("Current server", () -> player.getServer() == null
                ? null : player.getServer().getInfo().getName());
    }

    private static void captureProxyNetwork(ConnectionRecord record, ProxyServer proxy) {
        Section section = record.section("Proxy / Network (control tower)");
        section.add("Proxy software", () -> proxy.getName() + " " + proxy.getVersion());
        section.add("Players online", proxy::getOnlineCount);
        section.add("Registered servers", () -> proxy.getServers().size());
        section.add("Network map", () -> proxy.getServers().values().stream()
                .map(info -> info.getName() + " (" + address(info) + ", players=" + info.getPlayers().size() + ")")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", ")));
    }

    private static String address(ServerInfo info) {
        SocketAddress address = info.getSocketAddress();
        if (address instanceof InetSocketAddress inet) {
            return inet.getHostString() + ":" + inet.getPort();
        }
        return String.valueOf(address);
    }

    private static String host(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
        }
        return address == null ? null : address.toString();
    }
}
