package com.neramc.connectionverify.listener;

import com.neramc.connectionverify.ConnectionRecord;
import com.neramc.connectionverify.ConnectionRecord.Section;
import com.neramc.connectionverify.ConnectionRecord.Status;
import com.neramc.connectionverify.ConnectionRegistry;
import com.neramc.connectionverify.ConnectionVerifyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Locale;

/**
 * Captures every connection attempt and announces its connection number on the
 * console.
 *
 * <ul>
 *   <li>{@link PlayerJoinEvent} - a successful join.</li>
 *   <li>{@link AsyncPlayerPreLoginEvent} - failures before login (ban, whitelist,
 *       server full, ...).</li>
 *   <li>{@link PlayerLoginEvent} - failures during login (plugin denials, ...).</li>
 * </ul>
 *
 * All handlers run at {@link EventPriority#MONITOR} so the final, post-plugin
 * result is recorded.
 */
public final class ConnectionListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ConnectionVerifyPlugin plugin;
    private final ConnectionRegistry registry;
    private final boolean logSuccessful;
    private final boolean logFailed;

    public ConnectionListener(ConnectionVerifyPlugin plugin,
                              ConnectionRegistry registry,
                              boolean logSuccessful,
                              boolean logFailed) {
        this.plugin = plugin;
        this.registry = registry;
        this.logSuccessful = logSuccessful;
        this.logFailed = logFailed;
    }

    // ------------------------------------------------------------------
    //  Successful joins
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!logSuccessful) {
            return;
        }

        Player player = event.getPlayer();
        ConnectionRecord record = new ConnectionRecord(Status.SUCCESS);

        Section identity = record.section("Identity");
        identity.add("Name", player.getName());
        identity.add("Display name", plain(player.displayName()));
        identity.add("UUID", player.getUniqueId());
        identity.add("Entity id", player.getEntityId());

        Section network = record.section("Network");
        InetSocketAddress address = player.getAddress();
        if (address != null) {
            InetAddress inet = address.getAddress();
            network.add("IP address", inet != null ? inet.getHostAddress() : address.getHostString());
            network.add("Port", address.getPort());
        } else {
            network.add("IP address", null);
            network.add("Port", null);
        }
        InetSocketAddress virtualHost = player.getVirtualHost();
        if (virtualHost != null) {
            network.add("Virtual host", virtualHost.getHostString());
            network.add("Virtual host port", virtualHost.getPort());
        }
        network.add("Client brand", player.getClientBrandName());
        network.add("Protocol version", player.getProtocolVersion());
        network.add("Ping (ms)", player.getPing());
        network.add("Client locale", player.locale());

        Section session = record.section("Session");
        session.add("First time on server", !player.hasPlayedBefore());
        session.add("First played", formatMillis(player.getFirstPlayed()));
        session.add("Last login", formatMillis(player.getLastLogin()));
        session.add("Operator", player.isOp());
        session.add("Whitelisted", player.isWhitelisted());
        session.add("Game mode", player.getGameMode());

        Section state = record.section("Player state");
        state.add("Health", String.format(Locale.ROOT, "%.1f", player.getHealth()));
        state.add("Food level", player.getFoodLevel());
        state.add("Experience level", player.getLevel());
        state.add("Allowed to fly", player.getAllowFlight());
        state.add("Flying", player.isFlying());

        Section location = record.section("Location");
        World world = player.getWorld();
        location.add("World", world.getName());
        location.add("Dimension", world.getEnvironment());
        Location loc = player.getLocation();
        location.add("Coordinates",
                String.format(Locale.ROOT, "x=%.2f, y=%.2f, z=%.2f", loc.getX(), loc.getY(), loc.getZ()));
        location.add("Orientation",
                String.format(Locale.ROOT, "yaw=%.1f, pitch=%.1f", loc.getYaw(), loc.getPitch()));

        Section join = record.section("Join");
        join.add("Join message", plain(event.joinMessage()));

        addServerContext(record);

        String number = registry.register(record);
        announceSuccess(player.getName(), number);
    }

    // ------------------------------------------------------------------
    //  Failed connections (pre-login stage: ban, whitelist, full, ...)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!logFailed || event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        ConnectionRecord record = new ConnectionRecord(Status.FAILED);

        Section identity = record.section("Identity");
        identity.add("Name", event.getName());
        identity.add("UUID", event.getUniqueId());

        Section network = record.section("Network");
        network.add("IP address", hostAddress(event.getAddress()));
        network.add("Raw IP address", hostAddress(event.getRawAddress()));
        network.add("Hostname used", event.getHostname());

        Section result = record.section("Result");
        result.add("Stage", "AsyncPlayerPreLogin");
        result.add("Login result", event.getLoginResult());
        result.add("Kick message", plain(event.kickMessage()));

        addServerContext(record);

        String number = registry.register(record);
        announceFailure(event.getName(), event.getLoginResult().name(), number);
    }

    // ------------------------------------------------------------------
    //  Failed connections (login stage: plugin denials, ...)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (!logFailed || event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        Player player = event.getPlayer();
        ConnectionRecord record = new ConnectionRecord(Status.FAILED);

        Section identity = record.section("Identity");
        identity.add("Name", player.getName());
        identity.add("UUID", player.getUniqueId());

        Section network = record.section("Network");
        network.add("IP address", hostAddress(event.getAddress()));
        network.add("Real IP address", hostAddress(event.getRealAddress()));
        network.add("Hostname used", event.getHostname());

        Section result = record.section("Result");
        result.add("Stage", "PlayerLogin");
        result.add("Login result", event.getResult());
        result.add("Kick message", plain(event.kickMessage()));

        addServerContext(record);

        String number = registry.register(record);
        announceFailure(player.getName(), event.getResult().name(), number);
    }

    // ------------------------------------------------------------------
    //  Console output
    // ------------------------------------------------------------------

    private void announceSuccess(String name, String number) {
        plugin.console(
                Component.text("Join ", NamedTextColor.GREEN)
                        .append(Component.text(name, NamedTextColor.WHITE)),
                connectionNumberLine(number));
    }

    private void announceFailure(String name, String reason, String number) {
        plugin.console(
                Component.text("Connection failed: ", NamedTextColor.RED)
                        .append(Component.text(name == null ? "(unknown)" : name, NamedTextColor.WHITE))
                        .append(Component.text(" (" + reason + ")", NamedTextColor.GRAY)),
                connectionNumberLine(number));
    }

    private Component connectionNumberLine(String number) {
        return Component.text("Connection number ", NamedTextColor.AQUA)
                .append(Component.text(number, NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void addServerContext(ConnectionRecord record) {
        Section server = record.section("Server");
        server.add("Server version", plugin.getServer().getVersion());
        server.add("Bukkit version", plugin.getServer().getBukkitVersion());
        server.add("Online mode", plugin.getServer().getOnlineMode());
        if (plugin.getServer().isPrimaryThread()) {
            server.add("Players online",
                    plugin.getServer().getOnlinePlayers().size() + " / " + plugin.getServer().getMaxPlayers());
        }
        server.add("Plugin version", plugin.getPluginMeta().getVersion());
    }

    private static String hostAddress(InetAddress address) {
        return address == null ? null : address.getHostAddress();
    }

    private static String plain(Component component) {
        return component == null ? null : PLAIN.serialize(component);
    }

    private static String formatMillis(long millis) {
        if (millis <= 0L) {
            return "(never)";
        }
        return ConnectionRecord.formatTimestamp(Instant.ofEpochMilli(millis));
    }
}
