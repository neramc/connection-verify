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
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.neramc.connectionverify.config.CaptureSettings;
import com.neramc.connectionverify.connection.ConnectionRecord;
import com.neramc.connectionverify.connection.ConnectionRecord.Section;
import com.neramc.connectionverify.connection.ConnectionRecord.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds richly detailed {@link ConnectionRecord}s from connection events -
 * the kind of low-level, log-style detail an operator could want.
 *
 * <p>Every field is captured through a defensive value supplier, so an API
 * absent on the running server build is recorded as {@code (unavailable: ...)}
 * rather than aborting the capture. Which sections are captured, and whether
 * IP addresses / UUIDs are masked, is governed by {@link CaptureSettings}. Only
 * APIs available across the whole supported range (1.21.4+) are referenced.</p>
 */
public final class ConnectionSnapshot {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final double BYTES_PER_MB = 1024.0D * 1024.0D;

    private static final Pattern SKIN_URL = Pattern.compile("\"SKIN\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CAPE_URL = Pattern.compile("\"CAPE\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SKIN_MODEL = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"");

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
            captureIdentity(record, player, settings);
        }
        if (settings.network()) {
            captureNetwork(record, player, settings);
        }
        if (settings.clientOptions()) {
            captureClientOptions(record, player);
        }
        if (settings.session()) {
            captureSession(record, player);
        }
        if (settings.playerState()) {
            capturePlayerState(record, player);
            captureAttributes(record, player);
            captureEquipment(record, player);
        }
        if (settings.location()) {
            captureLocation(record, player);
        }
        if (settings.world()) {
            captureWorld(record, player.getWorld());
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
            identity.add("UUID version", () -> event.getUniqueId().version());
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
    //  Player sections (successful join)
    // ------------------------------------------------------------------

    private static void captureIdentity(ConnectionRecord record, Player player, CaptureSettings settings) {
        Section identity = record.section("Identity");
        identity.add("Name", player.getName());
        identity.add("Display name", () -> plain(player.displayName()));
        identity.add("Player list name", () -> plain(player.playerListName()));
        identity.add("UUID", () -> uuid(player.getUniqueId(), settings));
        identity.add("UUID version", () -> player.getUniqueId().version());
        identity.add("Entity id", player::getEntityId);

        Textures textures = textures(player);
        identity.add("Profile properties", textures.propertyCount());
        identity.add("Skin model", textures.model());
        identity.add("Skin URL", textures.skinUrl());
        identity.add("Cape URL", textures.capeUrl());
        identity.add("Textures signed", textures.signed());
    }

    private static void captureNetwork(ConnectionRecord record, Player player, CaptureSettings settings) {
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

    private static void captureClientOptions(ConnectionRecord record, Player player) {
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

    private static void captureSession(ConnectionRecord record, Player player) {
        Section session = record.section("Session");
        session.add("First time on server", () -> !player.hasPlayedBefore());
        session.add("First played", () -> millis(player.getFirstPlayed()));
        session.add("Last login", () -> millis(player.getLastLogin()));
        session.add("Last seen", () -> millis(player.getLastSeen()));
        session.add("Operator", player::isOp);
        session.add("Whitelisted", player::isWhitelisted);
        session.add("Banned", player::isBanned);
        session.add("Game mode", player::getGameMode);
        session.add("Previous game mode", player::getPreviousGameMode);
    }

    private static void capturePlayerState(ConnectionRecord record, Player player) {
        Section state = record.section("Player state");
        state.add("Health", () -> String.format(Locale.ROOT, "%.1f", player.getHealth()));
        state.add("Health scale", () -> String.format(Locale.ROOT, "%.1f", player.getHealthScale()));
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
        state.add("Swimming", player::isSwimming);
        state.add("Climbing", player::isClimbing);
        state.add("Gliding", player::isGliding);
        state.add("Sleeping", player::isSleeping);
        state.add("Sleep ticks", player::getSleepTicks);
        state.add("Glowing", player::isGlowing);
        state.add("Invulnerable", player::isInvulnerable);
        state.add("Silent", player::isSilent);
        state.add("Leashed", player::isLeashed);
        state.add("On ground", player::isOnGround);
        state.add("In water", player::isInWater);
        state.add("Pose", player::getPose);
        state.add("Remaining air", player::getRemainingAir);
        state.add("Maximum air", player::getMaximumAir);
        state.add("Fire ticks", player::getFireTicks);
        state.add("Freeze ticks", player::getFreezeTicks);
        state.add("Arrows in body", player::getArrowsInBody);
        state.add("No-damage ticks", player::getNoDamageTicks);
        state.add("Max no-damage ticks", player::getMaximumNoDamageTicks);
        state.add("Last damage", () -> String.format(Locale.ROOT, "%.2f", player.getLastDamage()));
        state.add("Portal cooldown", player::getPortalCooldown);
        state.add("Fall distance", () -> String.format(Locale.ROOT, "%.2f", player.getFallDistance()));
        state.add("Velocity", () -> vector(player.getVelocity()));
        state.add("Eye height", () -> String.format(Locale.ROOT, "%.2f", player.getEyeHeight()));
        state.add("Bounding box (w x h)",
                () -> String.format(Locale.ROOT, "%.2f x %.2f", player.getWidth(), player.getHeight()));
        state.add("Hand raised", player::isHandRaised);
        state.add("Active item", () -> describeItem(player.getActiveItem()));
        state.add("Active item time left", player::getActiveItemRemainingTime);
        state.add("Inside vehicle", player::isInsideVehicle);
        state.add("Vehicle", () -> {
            var vehicle = player.getVehicle();
            return vehicle == null ? null : vehicle.getType().getKey().getKey();
        });
        state.add("Current input", player::getCurrentInput);
        state.add("Spawn reason", player::getEntitySpawnReason);
        state.add("Scoreboard tags", player::getScoreboardTags);
        state.add("Active potion effects", () -> potions(player.getActivePotionEffects()));
    }

    private static void captureAttributes(ConnectionRecord record, Player player) {
        Section attributes = record.section("Attributes");
        try {
            for (Attribute attribute : Registry.ATTRIBUTE) {
                AttributeInstance instance;
                try {
                    instance = player.getAttribute(attribute);
                } catch (Throwable ignored) {
                    continue;
                }
                if (instance == null) {
                    continue;
                }
                attributes.add(attribute.getKey().getKey(), String.format(Locale.ROOT,
                        "%.3f (base %.3f)", instance.getValue(), instance.getBaseValue()));
            }
        } catch (Throwable throwable) {
            attributes.add("attributes", "(unavailable: " + throwable.getClass().getSimpleName() + ")");
        }
    }

    private static void captureEquipment(ConnectionRecord record, Player player) {
        Section equipment = record.section("Equipment & inventory");
        PlayerInventory inventory = player.getInventory();
        equipment.add("Held slot", inventory::getHeldItemSlot);
        equipment.add("Main hand", () -> describeItem(inventory.getItemInMainHand()));
        equipment.add("Off hand", () -> describeItem(inventory.getItemInOffHand()));
        equipment.add("Helmet", () -> describeItem(inventory.getHelmet()));
        equipment.add("Chestplate", () -> describeItem(inventory.getChestplate()));
        equipment.add("Leggings", () -> describeItem(inventory.getLeggings()));
        equipment.add("Boots", () -> describeItem(inventory.getBoots()));
        equipment.add("Inventory slots used", () -> usedSlots(inventory.getContents()));
        equipment.add("Ender chest slots used", () -> usedSlots(player.getEnderChest().getContents()));
    }

    private static void captureLocation(ConnectionRecord record, Player player) {
        Section location = record.section("Location");
        location.add("Current", () -> location(player.getLocation()));
        location.add("Block coordinates", () -> {
            Location loc = player.getLocation();
            return "x=" + loc.getBlockX() + ", y=" + loc.getBlockY() + ", z=" + loc.getBlockZ();
        });
        location.add("Direction (yaw/pitch)", () -> {
            Location loc = player.getLocation();
            return String.format(Locale.ROOT, "%.1f / %.1f", loc.getYaw(), loc.getPitch());
        });
        location.add("Facing", player::getFacing);
        location.add("Eye location", () -> location(player.getEyeLocation()));
        location.add("Biome", () -> blockAtFeet(player).getBiome().getKey().getKey());
        location.add("Block at feet", () -> blockAtFeet(player).getType().getKey().getKey());
        location.add("Block below", () -> player.getLocation().subtract(0, 1, 0).getBlock().getType().getKey().getKey());
        location.add("Light level", () -> blockAtFeet(player).getLightLevel());
        location.add("Sky light", () -> blockAtFeet(player).getLightFromSky());
        location.add("Block light", () -> blockAtFeet(player).getLightFromBlocks());
        location.add("Chunk", () -> {
            Chunk chunk = player.getLocation().getChunk();
            return "x=" + chunk.getX() + ", z=" + chunk.getZ()
                    + ", inhabited=" + chunk.getInhabitedTime() + "t, forceLoaded=" + chunk.isForceLoaded();
        });
        location.add("Respawn location", () -> location(player.getRespawnLocation()));
        location.add("Last death location", () -> location(player.getLastDeathLocation()));
    }

    private static void captureWorld(ConnectionRecord record, World world) {
        Section section = record.section("World");
        section.add("Name", world::getName);
        section.add("Environment", world::getEnvironment);
        section.add("Difficulty", world::getDifficulty);
        section.add("Hardcore", world::isHardcore);
        section.add("PVP", world::getPVP);
        section.add("Time", world::getTime);
        section.add("Full time", world::getFullTime);
        section.add("Game time", world::getGameTime);
        section.add("Moon phase", () -> (world.getFullTime() / 24_000L) % 8L);
        section.add("Storm", world::hasStorm);
        section.add("Thundering", world::isThundering);
        section.add("Players in world", world::getPlayerCount);
        section.add("Entities in world", world::getEntityCount);
        section.add("Loaded chunks", () -> world.getLoadedChunks().length);
        section.add("View distance", world::getViewDistance);
        section.add("Simulation distance", world::getSimulationDistance);
        section.add("Min height", world::getMinHeight);
        section.add("Max height", world::getMaxHeight);
        section.add("Sea level", world::getSeaLevel);
        section.add("Keep spawn loaded", world::getKeepSpawnInMemory);
        section.add("Allow monsters", world::getAllowMonsters);
        section.add("Allow animals", world::getAllowAnimals);
        section.add("Spawn location", () -> location(world.getSpawnLocation()));
        section.add("Border size", () -> world.getWorldBorder().getSize());
        section.add("Border center", () -> {
            Location center = world.getWorldBorder().getCenter();
            return String.format(Locale.ROOT, "x=%.1f, z=%.1f", center.getX(), center.getZ());
        });
        section.add("Border damage amount", () -> world.getWorldBorder().getDamageAmount());
        section.add("Border warning distance", () -> world.getWorldBorder().getWarningDistance());
    }

    // ------------------------------------------------------------------
    //  Shared sections (server + runtime)
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
        section.add("Allow nether", server::getAllowNether);
        section.add("Allow end", server::getAllowEnd);
        section.add("Default game mode", server::getDefaultGameMode);
        section.add("View distance", server::getViewDistance);
        section.add("Simulation distance", server::getSimulationDistance);
        section.add("Spawn radius", server::getSpawnRadius);
        section.add("Idle timeout (min)", server::getIdleTimeout);
        section.add("Max world size", server::getMaxWorldSize);
        section.add("Connection throttle (ms)", server::getConnectionThrottle);
        section.add("Max players", server::getMaxPlayers);
        section.add("Operators", () -> server.getOperators().size());
        section.add("Whitelisted players", () -> server.getWhitelistedPlayers().size());
        section.add("Banned players", () -> server.getBannedPlayers().size());
        section.add("IP bans", () -> server.getIPBans().size());
        if (server.isPrimaryThread()) {
            section.add("Players online", () -> server.getOnlinePlayers().size() + " / " + server.getMaxPlayers());
            section.add("Online players", () -> server.getOnlinePlayers().stream()
                    .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", ")));
            section.add("Worlds", () -> server.getWorlds().stream()
                    .map(world -> world.getName() + " (" + world.getEnvironment()
                            + ", players=" + world.getPlayerCount()
                            + ", chunks=" + world.getLoadedChunks().length
                            + ", entities=" + world.getEntityCount() + ")")
                    .collect(Collectors.joining(", ")));
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
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        section.add("JVM", () -> runtimeBean.getVmName() + " " + runtimeBean.getVmVersion());
        section.add("JVM vendor", runtimeBean::getVmVendor);
        section.add("JVM spec version", runtimeBean::getSpecVersion);
        section.add("JVM flags", () -> runtimeBean.getInputArguments().size() + " arguments");
        section.add("Operating system", () -> System.getProperty("os.name")
                + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        section.add("Available processors", runtime::availableProcessors);

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        section.add("System load average", () -> {
            double load = os.getSystemLoadAverage();
            return load < 0 ? "(unavailable)" : String.format(Locale.ROOT, "%.2f", load);
        });

        section.add("Max memory", () -> bytes(runtime.maxMemory()));
        section.add("Total memory", () -> bytes(runtime.totalMemory()));
        section.add("Free memory", () -> bytes(runtime.freeMemory()));
        section.add("Used memory", () -> bytes(runtime.totalMemory() - runtime.freeMemory()));
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        section.add("Heap memory", () -> usage(memory.getHeapMemoryUsage()));
        section.add("Non-heap memory", () -> usage(memory.getNonHeapMemoryUsage()));
        section.add("Garbage collectors", () -> ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(gc -> gc.getName() + " (count=" + gc.getCollectionCount() + ", time=" + gc.getCollectionTime() + "ms)")
                .collect(Collectors.joining(", ")));

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        section.add("Live threads", threads::getThreadCount);
        section.add("Peak threads", threads::getPeakThreadCount);
        section.add("Daemon threads", threads::getDaemonThreadCount);
        section.add("Total started threads", threads::getTotalStartedThreadCount);

        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        section.add("Loaded classes", classes::getLoadedClassCount);
        section.add("Total loaded classes", classes::getTotalLoadedClassCount);
        section.add("Unloaded classes", classes::getUnloadedClassCount);

        section.add("Server uptime", () -> duration(runtimeBean.getUptime()));
        section.add("JVM started", () -> ConnectionRecord.formatTimestamp(Instant.ofEpochMilli(runtimeBean.getStartTime())));
        section.add("File encoding", () -> System.getProperty("file.encoding"));
        section.add("Default charset", () -> Charset.defaultCharset().name());
        section.add("Default locale", () -> Locale.getDefault().toString());
        section.add("Timezone", () -> TimeZone.getDefault().getID());
        section.add("Working directory", () -> System.getProperty("user.dir"));
        section.add("Disk (server dir)", () -> {
            File dir = new File(".").getAbsoluteFile();
            return bytes(dir.getUsableSpace()) + " free / " + bytes(dir.getTotalSpace()) + " total";
        });
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private record Textures(Integer propertyCount, String model, String skinUrl, String capeUrl, Boolean signed) {
    }

    private static Textures textures(Player player) {
        try {
            PlayerProfile profile = player.getPlayerProfile();
            if (profile == null) {
                return new Textures(null, null, null, null, null);
            }
            int count = profile.getProperties().size();
            for (ProfileProperty property : profile.getProperties()) {
                if (!"textures".equals(property.getName())) {
                    continue;
                }
                String json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
                String model = group(SKIN_MODEL, json);
                return new Textures(count, model == null ? "classic" : model,
                        group(SKIN_URL, json), group(CAPE_URL, json), property.isSigned());
            }
            return new Textures(count, null, null, null, null);
        } catch (Throwable throwable) {
            return new Textures(null, null, null, null, null);
        }
    }

    private static String group(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Block blockAtFeet(Player player) {
        return player.getWorld().getBlockAt(player.getLocation());
    }

    private static String describeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "empty";
        }
        return item.getType().getKey().getKey() + " x" + item.getAmount();
    }

    private static String usedSlots(ItemStack[] contents) {
        if (contents == null) {
            return null;
        }
        int used = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                used++;
            }
        }
        return used + " / " + contents.length;
    }

    private static String usage(MemoryUsage usage) {
        if (usage == null) {
            return null;
        }
        return bytes(usage.getUsed()) + " used / " + bytes(usage.getCommitted()) + " committed / "
                + (usage.getMax() < 0 ? "no max" : bytes(usage.getMax()));
    }

    private static String plain(Component component) {
        return component == null ? null : PLAIN.serialize(component);
    }

    private static String uuid(java.util.UUID id, CaptureSettings settings) {
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
