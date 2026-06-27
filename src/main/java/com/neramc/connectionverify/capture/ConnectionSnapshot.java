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
package com.neramc.connectionverify.capture;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.neramc.connectionverify.config.CaptureSettings;
import com.neramc.connectionverify.connection.ConnectionRecord;
import com.neramc.connectionverify.connection.ConnectionRecord.Section;
import com.neramc.connectionverify.connection.ConnectionRecord.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds richly detailed {@link ConnectionRecord}s from connection events.
 *
 * <p>Each field is captured through a defensive value supplier, so an API
 * absent on the running server build is recorded as {@code (unavailable: ...)}
 * rather than aborting the capture. Which sections are captured, and whether
 * IP addresses / UUIDs are masked, is governed by {@link CaptureSettings}.</p>
 */
public final class ConnectionSnapshot {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final double BYTES_PER_MB = 1024.0D * 1024.0D;

    private ConnectionSnapshot() {
    }

    // ------------------------------------------------------------------
    //  Successful join
    // ------------------------------------------------------------------

    public static ConnectionRecord forJoin(PlayerJoinEvent event, JavaPlugin plugin, CaptureSettings settings) {
        Player player = event.getPlayer();
        ConnectionRecord record = new ConnectionRecord(Status.SUCCESS);
        captureContext(record, plugin, "PlayerJoinEvent");

        if (settings.identity()) {
            Section identity = record.section("Identity");
            identity.add("Name", player.getName());
            identity.add("Display name", () -> plain(player.displayName()));
            identity.add("UUID", () -> uuid(player.getUniqueId(), settings));
            identity.add("Entity id", () -> player.getEntityId());
        }

        if (settings.network()) {
            Section network = record.section("Network");
            network.add("IP address", () -> {
                InetSocketAddress address = player.getAddress();
                if (address == null) {
                    return null;
                }
                InetAddress inet = address.getAddress();
                return settings.maskIpAddress(inet != null ? inet.getHostAddress() : address.getHostString());
            });
            network.add("Port", () -> {
                InetSocketAddress address = player.getAddress();
                return address == null ? null : address.getPort();
            });
            network.add("Virtual host", () -> {
                InetSocketAddress virtualHost = player.getVirtualHost();
                return virtualHost == null ? null : virtualHost.getHostString() + ":" + virtualHost.getPort();
            });
            network.add("Client brand", player::getClientBrandName);
            network.add("Protocol version", player::getProtocolVersion);
            network.add("Ping (ms)", player::getPing);
            network.add("Transferred", player::isTransferred);
            network.add("Client view distance", player::getClientViewDistance);
            network.add("Effective view distance", player::getViewDistance);
            network.add("Simulation distance", player::getSimulationDistance);
        }

        if (settings.clientOptions()) {
            Section options = record.section("Client options");
            options.add("Locale", player::locale);
            options.add("Main hand", () -> player.getClientOption(ClientOption.MAIN_HAND));
            options.add("Chat visibility", () -> player.getClientOption(ClientOption.CHAT_VISIBILITY));
            options.add("Chat colors", () -> player.getClientOption(ClientOption.CHAT_COLORS_ENABLED));
            options.add("Particle visibility", () -> player.getClientOption(ClientOption.PARTICLE_VISIBILITY));
            options.add("Allow server listings", () -> player.getClientOption(ClientOption.ALLOW_SERVER_LISTINGS));
            options.add("Text filtering", () -> player.getClientOption(ClientOption.TEXT_FILTERING_ENABLED));
            options.add("View distance (option)", () -> player.getClientOption(ClientOption.VIEW_DISTANCE));
            options.add("Skin parts", () -> player.getClientOption(ClientOption.SKIN_PARTS));
        }

        if (settings.session()) {
            Section session = record.section("Session");
            session.add("First time on server", () -> !player.hasPlayedBefore());
            session.add("First played", () -> millis(player.getFirstPlayed()));
            session.add("Last login", () -> millis(player.getLastLogin()));
            session.add("Last seen", () -> millis(player.getLastSeen()));
            session.add("Operator", player::isOp);
            session.add("Whitelisted", player::isWhitelisted);
            session.add("Banned", player::isBanned);
            session.add("Game mode", player::getGameMode);
        }

        if (settings.playerState()) {
            Section state = record.section("Player state");
            state.add("Health", () -> String.format(Locale.ROOT, "%.1f", player.getHealth()));
            state.add("Max health", () -> {
                AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
                return attribute == null ? null : String.format(Locale.ROOT, "%.1f", attribute.getValue());
            });
            state.add("Food level", player::getFoodLevel);
            state.add("Saturation", () -> String.format(Locale.ROOT, "%.2f", player.getSaturation()));
            state.add("Exhaustion", () -> String.format(Locale.ROOT, "%.2f", player.getExhaustion()));
            state.add("Experience level", player::getLevel);
            state.add("Experience progress", () -> String.format(Locale.ROOT, "%.3f", player.getExp()));
            state.add("Total experience", player::getTotalExperience);
            state.add("Walk speed", () -> String.format(Locale.ROOT, "%.2f", player.getWalkSpeed()));
            state.add("Fly speed", () -> String.format(Locale.ROOT, "%.2f", player.getFlySpeed()));
            state.add("Allowed to fly", player::getAllowFlight);
            state.add("Flying", player::isFlying);
            state.add("Sneaking", player::isSneaking);
            state.add("Sprinting", player::isSprinting);
            state.add("Glowing", player::isGlowing);
            state.add("In water", player::isInWater);
            state.add("Pose", player::getPose);
            state.add("Remaining air", player::getRemainingAir);
            state.add("Maximum air", player::getMaximumAir);
            state.add("Fire ticks", player::getFireTicks);
            state.add("Fall distance", () -> String.format(Locale.ROOT, "%.2f", player.getFallDistance()));
            state.add("Velocity", () -> vector(player.getVelocity()));
            state.add("Current input", player::getCurrentInput);
            state.add("Spawn reason", player::getEntitySpawnReason);
            state.add("Scoreboard tags", player::getScoreboardTags);
            state.add("Active potion effects", () -> potions(player.getActivePotionEffects()));
        }

        if (settings.location()) {
            Section location = record.section("Location");
            location.add("Current", () -> location(player.getLocation()));
            location.add("Block coordinates", () -> {
                Location loc = player.getLocation();
                return "x=" + loc.getBlockX() + ", y=" + loc.getBlockY() + ", z=" + loc.getBlockZ();
            });
            location.add("Respawn location", () -> location(player.getRespawnLocation()));
            location.add("Last death location", () -> location(player.getLastDeathLocation()));
        }

        if (settings.world()) {
            Section world = record.section("World");
            World playerWorld = player.getWorld();
            world.add("Name", playerWorld::getName);
            world.add("Environment", playerWorld::getEnvironment);
            world.add("Difficulty", playerWorld::getDifficulty);
            world.add("PVP", playerWorld::getPVP);
            world.add("Time", playerWorld::getTime);
            world.add("Full time", playerWorld::getFullTime);
            world.add("Game time", playerWorld::getGameTime);
            world.add("Storm", playerWorld::hasStorm);
            world.add("Thundering", playerWorld::isThundering);
            world.add("Players in world", playerWorld::getPlayerCount);
            world.add("Entities in world", playerWorld::getEntityCount);
            world.add("View distance", playerWorld::getViewDistance);
            world.add("Simulation distance", playerWorld::getSimulationDistance);
            world.add("Spawn location", () -> location(playerWorld.getSpawnLocation()));
        }

        Section join = record.section("Join");
        join.add("Join message", () -> plain(event.joinMessage()));

        captureServerAndRuntime(record, plugin, settings);
        return record;
    }

    // ------------------------------------------------------------------
    //  Failure before login (ban, whitelist, server full, ...)
    // ------------------------------------------------------------------

    public static ConnectionRecord forPreLogin(AsyncPlayerPreLoginEvent event, JavaPlugin plugin,
                                               CaptureSettings settings) {
        ConnectionRecord record = new ConnectionRecord(Status.FAILED);
        captureContext(record, plugin, "AsyncPlayerPreLoginEvent");

        if (settings.identity()) {
            Section identity = record.section("Identity");
            identity.add("Name", event.getName());
            identity.add("UUID", () -> uuid(event.getUniqueId(), settings));
        }

        if (settings.network()) {
            Section network = record.section("Network");
            network.add("IP address", () -> ip(event.getAddress(), settings));
            network.add("Raw IP address", () -> ip(event.getRawAddress(), settings));
            network.add("Hostname used", event::getHostname);
            network.add("Transferred", event::isTransferred);
        }

        Section result = record.section("Result");
        result.add("Stage", "AsyncPlayerPreLogin");
        result.add("Login result", event::getLoginResult);
        result.add("Kick message", () -> plain(event.kickMessage()));

        captureServerAndRuntime(record, plugin, settings);
        return record;
    }

    // ------------------------------------------------------------------
    //  Failure during login (plugin denials, ...)
    // ------------------------------------------------------------------

    public static ConnectionRecord forLogin(PlayerLoginEvent event, JavaPlugin plugin, CaptureSettings settings) {
        Player player = event.getPlayer();
        ConnectionRecord record = new ConnectionRecord(Status.FAILED);
        captureContext(record, plugin, "PlayerLoginEvent");

        if (settings.identity()) {
            Section identity = record.section("Identity");
            identity.add("Name", player.getName());
            identity.add("UUID", () -> uuid(player.getUniqueId(), settings));
        }

        if (settings.network()) {
            Section network = record.section("Network");
            network.add("IP address", () -> ip(event.getAddress(), settings));
            network.add("Real IP address", () -> ip(event.getRealAddress(), settings));
            network.add("Hostname used", event::getHostname);
        }

        Section result = record.section("Result");
        result.add("Stage", "PlayerLogin");
        result.add("Login result", event::getResult);
        result.add("Kick message", () -> plain(event.kickMessage()));

        captureServerAndRuntime(record, plugin, settings);
        return record;
    }

    // ------------------------------------------------------------------
    //  Connection dropped / lost after authentication, before joining
    // ------------------------------------------------------------------

    public static ConnectionRecord forConnectionClose(PlayerConnectionCloseEvent event, JavaPlugin plugin,
                                                      CaptureSettings settings) {
        ConnectionRecord record = new ConnectionRecord(Status.FAILED);
        captureContext(record, plugin, "PlayerConnectionCloseEvent");

        if (settings.identity()) {
            Section identity = record.section("Identity");
            identity.add("Name", event.getPlayerName());
            identity.add("UUID", () -> uuid(event.getPlayerUniqueId(), settings));
        }

        if (settings.network()) {
            Section network = record.section("Network");
            network.add("IP address", () -> ip(event.getIpAddress(), settings));
        }

        Section result = record.section("Result");
        result.add("Stage", "PlayerConnectionClose");
        result.add("Outcome", "Connection lost/dropped before the player joined the world");
        result.add("Disconnect reason", "(not exposed by the Bukkit/Paper API at this stage)");

        captureServerAndRuntime(record, plugin, settings);
        return record;
    }

    // ------------------------------------------------------------------
    //  Shared sections
    // ------------------------------------------------------------------

    private static void captureContext(ConnectionRecord record, JavaPlugin plugin, String event) {
        Section context = record.section("Capture");
        context.add("Event", event);
        context.add("Captured on thread", () -> Thread.currentThread().getName());
        context.add("Primary thread", () -> plugin.getServer().isPrimaryThread());
    }

    private static void captureServerAndRuntime(ConnectionRecord record, JavaPlugin plugin, CaptureSettings settings) {
        if (settings.server()) {
            captureServer(record, plugin);
        }
        if (settings.runtime()) {
            captureRuntime(record);
        }
    }

    private static void captureServer(ConnectionRecord record, JavaPlugin plugin) {
        Server server = plugin.getServer();
        Section section = record.section("Server");
        section.add("Software", server::getName);
        section.add("Server version", server::getVersion);
        section.add("API version", server::getBukkitVersion);
        section.add("Minecraft version", server::getMinecraftVersion);
        section.add("MOTD", () -> plain(server.motd()));
        section.add("Bind address", () -> {
            String ip = server.getIp();
            return (ip == null || ip.isEmpty() ? "*" : ip) + ":" + server.getPort();
        });
        section.add("Online mode", server::getOnlineMode);
        section.add("Whitelist enabled", server::hasWhitelist);
        section.add("Whitelist enforced", server::isWhitelistEnforced);
        section.add("Hardcore", server::isHardcore);
        section.add("Default game mode", server::getDefaultGameMode);
        section.add("View distance", server::getViewDistance);
        section.add("Simulation distance", server::getSimulationDistance);
        section.add("Connection throttle (ms)", server::getConnectionThrottle);
        section.add("Max players", server::getMaxPlayers);
        if (server.isPrimaryThread()) {
            section.add("Players online", () -> server.getOnlinePlayers().size());
        }
        section.add("TPS (1m, 5m, 15m)", () -> tps(server.getTPS()));
        section.add("MSPT (avg tick)", () -> String.format(Locale.ROOT, "%.2f ms", server.getAverageTickTime()));
        section.add("Tick rate", () -> server.getServerTickManager().getTickRate());
        section.add("Tick frozen", () -> server.getServerTickManager().isFrozen());
        section.add("Running normally", () -> server.getServerTickManager().isRunningNormally());
        section.add("Loaded plugins", () -> plugins(server));
        section.add("Connection Verify version", () -> plugin.getPluginMeta().getVersion());
    }

    private static void captureRuntime(ConnectionRecord record) {
        Runtime runtime = Runtime.getRuntime();
        Section section = record.section("Runtime / Environment");
        section.add("Java version", () -> System.getProperty("java.version"));
        section.add("Java vendor", () -> System.getProperty("java.vendor"));
        section.add("JVM", () -> System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        section.add("Operating system", () -> System.getProperty("os.name")
                + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        section.add("Available processors", runtime::availableProcessors);
        section.add("Max memory", () -> bytes(runtime.maxMemory()));
        section.add("Total memory", () -> bytes(runtime.totalMemory()));
        section.add("Free memory", () -> bytes(runtime.freeMemory()));
        section.add("Used memory", () -> bytes(runtime.totalMemory() - runtime.freeMemory()));
        section.add("Server uptime", () -> duration(ManagementFactory.getRuntimeMXBean().getUptime()));
        section.add("Working directory", () -> System.getProperty("user.dir"));
    }

    // ------------------------------------------------------------------
    //  Formatting helpers
    // ------------------------------------------------------------------

    private static String plain(Component component) {
        return component == null ? null : PLAIN.serialize(component);
    }

    private static String uuid(UUID id, CaptureSettings settings) {
        if (id == null) {
            return null;
        }
        return settings.hideUuid() ? CaptureSettings.REDACTED : id.toString();
    }

    private static String ip(InetAddress address, CaptureSettings settings) {
        return address == null ? null : settings.maskIpAddress(address.getHostAddress());
    }

    private static String millis(long epochMillis) {
        return epochMillis <= 0L ? "(never)" : ConnectionRecord.formatTimestamp(Instant.ofEpochMilli(epochMillis));
    }

    private static String location(Location location) {
        if (location == null) {
            return null;
        }
        String world = location.getWorld() == null ? "?" : location.getWorld().getName();
        return String.format(Locale.ROOT, "%s @ x=%.2f, y=%.2f, z=%.2f (yaw=%.1f, pitch=%.1f)",
                world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private static String vector(Vector vector) {
        if (vector == null) {
            return null;
        }
        return String.format(Locale.ROOT, "x=%.3f, y=%.3f, z=%.3f", vector.getX(), vector.getY(), vector.getZ());
    }

    private static String potions(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return "none";
        }
        return effects.stream()
                .map(effect -> effect.getType().getKey().getKey()
                        + " x" + (effect.getAmplifier() + 1)
                        + " (" + effect.getDuration() + "t)")
                .collect(Collectors.joining(", "));
    }

    private static String tps(double[] tps) {
        if (tps == null || tps.length == 0) {
            return null;
        }
        List<String> parts = new ArrayList<>(tps.length);
        for (double value : tps) {
            parts.add(String.format(Locale.ROOT, "%.2f", value));
        }
        return String.join(", ", parts);
    }

    private static String plugins(Server server) {
        Plugin[] all = server.getPluginManager().getPlugins();
        if (all.length == 0) {
            return "none";
        }
        List<String> names = new ArrayList<>(all.length);
        for (Plugin plugin : all) {
            names.add(plugin.getName() + " v" + plugin.getPluginMeta().getVersion());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return all.length + " -> " + String.join(", ", names);
    }

    private static String bytes(long value) {
        return String.format(Locale.ROOT, "%.1f MB (%d bytes)", value / BYTES_PER_MB, value);
    }

    private static String duration(long millis) {
        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append("h ");
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        return sb + " (" + millis + " ms)";
    }
}
