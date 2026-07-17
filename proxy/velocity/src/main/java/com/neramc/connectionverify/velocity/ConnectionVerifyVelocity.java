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

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connection Verify - Velocity edition ("control tower").
 *
 * <p>Sits above the network and numbers every connection that flows through the
 * proxy, tracks which backend server each player is routed to, and archives a
 * full report on request with {@code /cnt <number>}.</p>
 */
@Plugin(
        id = "connectionverify",
        name = "Connection Verify",
        version = BuildConstants.VERSION,
        description = "Network control tower: numbers and archives every connection through the proxy.",
        url = "https://github.com/neramc/connection-verify",
        authors = {"neramc"}
)
public final class ConnectionVerifyVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    /** Live map of connected players to their connection number (the control-tower state). */
    private final Map<UUID, String> activeConnections = new ConcurrentHashMap<>();

    private volatile ProxyConfig config;
    private volatile ConnectionNumberRegistry registry;

    @Inject
    public ConnectionVerifyVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.config = ProxyConfig.load(dataDirectory);
        this.registry = new ConnectionNumberRegistry(config.numberLength(), config.leadingZeros(),
                config.maxInMemory());

        proxy.getEventManager().register(this, new ProxyConnectionListener(this));

        CommandManager commands = proxy.getCommandManager();
        CommandMeta meta = commands.metaBuilder("cnt").plugin(this).build();
        commands.register(meta, new CntCommand(this));

        logger.info("Connection Verify (Velocity control tower) v{} enabled - numbering every connection through {}.",
                BuildConstants.VERSION,
                proxy.getVersion().getName() + " " + proxy.getVersion().getVersion());
    }

    // ------------------------------------------------------------------
    //  Services
    // ------------------------------------------------------------------

    public ProxyServer proxy() {
        return proxy;
    }

    public Logger logger() {
        return logger;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

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

    private void console(Component line) {
        proxy.getConsoleCommandSource().sendMessage(line);
    }

    public void announceJoin(String name, String number) {
        console(Component.text("Join ", NamedTextColor.GREEN).append(Component.text(safe(name), NamedTextColor.WHITE)));
        console(Component.text("Connection number ", NamedTextColor.AQUA)
                .append(Component.text(number, NamedTextColor.YELLOW)));
    }

    public void announceRouting(String name, String from, String to, String number) {
        console(Component.text(safe(name), NamedTextColor.WHITE)
                .append(Component.text(" ⇒ ", NamedTextColor.DARK_AQUA))
                .append(Component.text(to, NamedTextColor.AQUA))
                .append(Component.text(from == null ? "" : " (from " + from + ")", NamedTextColor.GRAY))
                .append(Component.text(" [#" + number + "]", NamedTextColor.DARK_GRAY)));
    }

    public void announceFailure(String name, String reason, String number) {
        console(Component.text("Connection failed: ", NamedTextColor.RED)
                .append(Component.text(safe(name), NamedTextColor.WHITE))
                .append(Component.text(" (" + reason + ")", NamedTextColor.GRAY)));
        console(Component.text("Connection number ", NamedTextColor.AQUA)
                .append(Component.text(number, NamedTextColor.YELLOW)));
    }

    public void announceKick(String name, String server, String reason, String number) {
        console(Component.text(safe(name), NamedTextColor.WHITE)
                .append(Component.text(" kicked from ", NamedTextColor.RED))
                .append(Component.text(server, NamedTextColor.AQUA))
                .append(Component.text(reason == null ? "" : " (" + reason + ")", NamedTextColor.GRAY))
                .append(Component.text(" [#" + number + "]", NamedTextColor.DARK_GRAY)));
    }

    public void announceLeft(String name, String status, String number) {
        console(Component.text(safe(name), NamedTextColor.GRAY)
                .append(Component.text(" disconnected (" + status + ")", NamedTextColor.DARK_GRAY))
                .append(Component.text(" [#" + number + "]", NamedTextColor.DARK_GRAY)));
    }

    private static String safe(String name) {
        return (name == null || name.isEmpty()) ? "?" : name;
    }
}
