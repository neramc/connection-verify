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

import com.neramc.connectionverify.proxy.ConnectionNumberRegistry;
import com.neramc.connectionverify.proxy.ProxyConfig;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connection Verify - BungeeCord/Waterfall edition ("control tower").
 *
 * <p>Sits above the network and numbers every connection that flows through the
 * proxy, tracks which backend server each player is routed to, and archives a
 * full report on request with {@code /cnt <number>}.</p>
 */
public final class ConnectionVerifyBungee extends Plugin {

    /** Live map of connected players to their connection number. */
    private final Map<UUID, String> activeConnections = new ConcurrentHashMap<>();

    private volatile ProxyConfig config;
    private volatile ConnectionNumberRegistry registry;

    @Override
    public void onEnable() {
        this.config = ProxyConfig.load(getDataFolder().toPath());
        this.registry = new ConnectionNumberRegistry(config.numberLength(), config.leadingZeros(),
                config.maxInMemory());

        getProxy().getPluginManager().registerListener(this, new BungeeConnectionListener(this));
        getProxy().getPluginManager().registerCommand(this, new CntCommand(this));

        getLogger().info("Connection Verify (BungeeCord control tower) v" + getDescription().getVersion()
                + " enabled - numbering every connection through "
                + getProxy().getName() + " " + getProxy().getVersion() + ".");
    }

    // ------------------------------------------------------------------
    //  Services
    // ------------------------------------------------------------------

    public ProxyConfig config() {
        return config;
    }

    public ConnectionNumberRegistry registry() {
        return registry;
    }

    public Map<UUID, String> activeConnections() {
        return activeConnections;
    }

    // ------------------------------------------------------------------
    //  Control-tower console output
    // ------------------------------------------------------------------

    public void announceJoin(String name, String number) {
        getLogger().info("Join " + safe(name));
        getLogger().info("Connection number " + number);
    }

    public void announceRouting(String name, String from, String to, String number) {
        getLogger().info(safe(name) + " => " + to
                + (from == null ? "" : " (from " + from + ")") + " [#" + number + "]");
    }

    public void announceFailure(String name, String reason, String number) {
        getLogger().info("Connection failed: " + safe(name) + " (" + reason + ")");
        getLogger().info("Connection number " + number);
    }

    public void announceKick(String name, String server, String reason, String number) {
        getLogger().info(safe(name) + " kicked from " + server
                + (reason == null ? "" : " (" + reason + ")") + " [#" + number + "]");
    }

    public void announceLeft(String name, String number) {
        getLogger().info(safe(name) + " disconnected [#" + number + "]");
    }

    private static String safe(String name) {
        return (name == null || name.isEmpty()) ? "?" : name;
    }
}
