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
import com.neramc.connectionverify.proxy.ConnectionRecord.Section;
import com.neramc.connectionverify.proxy.ConnectionRecord.Status;
import com.neramc.connectionverify.proxy.ProxyConfig;
import com.neramc.connectionverify.proxy.SystemCapture;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Builds richly detailed {@link ConnectionRecord}s from what a Velocity proxy
 * knows about a connection - the peer's identity and network details, and the
 * proxy's own network-wide view (the "control tower"): every registered backend
 * server and its player count. Every field is captured defensively.
 */
public final class ProxySnapshot {

    private ProxySnapshot() {
    }

    /** Builds a record for a player who has just logged in to the proxy. */
    public static ConnectionRecord forConnect(Player player, ProxyServer proxy, ProxyConfig config, String event) {
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

    /** Builds a record for a failed / denied / dropped proxy connection. */
    public static ConnectionRecord forFailure(String username, InetSocketAddress address, String reason,
                                              ProxyServer proxy, ProxyConfig config, String event) {
        ConnectionRecord record = new ConnectionRecord(Status.FAILED);
        captureContext(record, event);

        Section identity = record.section("Identity");
        identity.add("Name", username);

        Section network = record.section("Network");
        network.add("IP address", () -> config.maskIpAddress(host(address)));
        network.add("Port", () -> address == null ? null : address.getPort());

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
        context.add("Edition", "Velocity proxy (control tower)");
    }

    private static void captureIdentity(ConnectionRecord record, Player player) {
        Section identity = record.section("Identity");
        identity.add("Name", player::getUsername);
        identity.add("UUID", () -> player.getUniqueId().toString());
        identity.add("UUID version", () -> player.getUniqueId().version());
        identity.add("Online mode", player::isOnlineMode);
    }

    private static void captureNetwork(ConnectionRecord record, Player player, ProxyConfig config) {
        Section network = record.section("Network");
        network.add("IP address", () -> config.maskIpAddress(host(player.getRemoteAddress())));
        network.add("Port", () -> {
            InetSocketAddress address = player.getRemoteAddress();
            return address == null ? null : address.getPort();
        });
        network.add("Virtual host", () -> player.getVirtualHost()
                .map(vh -> vh.getHostString() + ":" + vh.getPort()).orElse(null));
        network.add("Protocol version", () -> player.getProtocolVersion().getProtocol());
        network.add("Protocol name", () -> player.getProtocolVersion().getVersionIntroducedIn());
        network.add("Client brand", player::getClientBrand);
        network.add("Ping (ms)", player::getPing);
    }

    private static void captureProxyNetwork(ConnectionRecord record, ProxyServer proxy) {
        Section section = record.section("Proxy / Network (control tower)");
        section.add("Proxy software", () -> proxy.getVersion().getName() + " " + proxy.getVersion().getVersion());
        section.add("Proxy vendor", () -> proxy.getVersion().getVendor());
        section.add("Bind address", () -> {
            InetSocketAddress bound = proxy.getBoundAddress();
            return bound == null ? null : bound.getHostString() + ":" + bound.getPort();
        });
        section.add("Players online", proxy::getPlayerCount);
        Collection<RegisteredServer> servers = proxy.getAllServers();
        section.add("Registered servers", servers::size);
        section.add("Network map", () -> servers.stream()
                .map(s -> s.getServerInfo().getName() + " ("
                        + s.getServerInfo().getAddress().getHostString() + ":"
                        + s.getServerInfo().getAddress().getPort()
                        + ", players=" + s.getPlayersConnected().size() + ")")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", ")));
    }

    // ------------------------------------------------------------------

    private static String host(InetSocketAddress address) {
        if (address == null) {
            return null;
        }
        return address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
    }
}
