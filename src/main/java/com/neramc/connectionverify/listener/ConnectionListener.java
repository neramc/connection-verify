package com.neramc.connectionverify.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.neramc.connectionverify.ConnectionRecord;
import com.neramc.connectionverify.ConnectionRegistry;
import com.neramc.connectionverify.ConnectionSnapshot;
import com.neramc.connectionverify.ConnectionVerifyPlugin;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Listens for every connection attempt, hands it to {@link ConnectionSnapshot}
 * for capture, and prints its connection number on the console.
 *
 * <ul>
 *   <li>{@link PlayerJoinEvent} - a successful join.</li>
 *   <li>{@link AsyncPlayerPreLoginEvent} - failures before login (ban,
 *       whitelist, server full, ...).</li>
 *   <li>{@link PlayerLoginEvent} - failures during login (plugin denials,
 *       ...).</li>
 *   <li>{@link PlayerConnectionValidateLoginEvent} - login/validation-stage
 *       failures.</li>
 *   <li>{@link PlayerConnectionCloseEvent} - a connection that is dropped /
 *       lost after authentication but before the player joins the world
 *       (network errors, timeouts, generic "lost connection" disconnects).</li>
 * </ul>
 *
 * <p>All handlers run at {@link EventPriority#MONITOR} so the final,
 * post-plugin result is what gets recorded.</p>
 *
 * <p>A connection that is denied at the pre-login/login/validation stages
 * produces exactly one connection number there. {@link PlayerConnectionCloseEvent}
 * is the catch-all for connections that drop without any explicit deny; it
 * skips players who actually joined (normal quits) and de-duplicates against
 * failures already reported by the richer handlers above.</p>
 */
public final class ConnectionListener implements Listener {

    private final ConnectionVerifyPlugin plugin;
    private final ConnectionRegistry registry;
    private final boolean logSuccessful;
    private final boolean logFailed;

    /** UUIDs of players who successfully joined the world (cleared on disconnect). */
    private final Set<UUID> joinedConnections = ConcurrentHashMap.newKeySet();
    /** UUIDs already reported as failed by a pre-login/login/validation handler. */
    private final Set<UUID> reportedFailures = ConcurrentHashMap.newKeySet();

    public ConnectionListener(ConnectionVerifyPlugin plugin,
                              ConnectionRegistry registry,
                              boolean logSuccessful,
                              boolean logFailed) {
        this.plugin = plugin;
        this.registry = registry;
        this.logSuccessful = logSuccessful;
        this.logFailed = logFailed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Always track the join so the connection-close handler can tell a normal
        // quit apart from a failed connection, regardless of logging settings.
        joinedConnections.add(event.getPlayer().getUniqueId());
        if (!logSuccessful) {
            return;
        }
        ConnectionRecord record = ConnectionSnapshot.forJoin(event, plugin);
        String number = registry.register(record);
        announceSuccess(event.getPlayer().getName(), number);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!logFailed || event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        // A denied pre-login never produces a PlayerConnectionCloseEvent, so this
        // is reported directly without participating in the de-duplication set.
        ConnectionRecord record = ConnectionSnapshot.forPreLogin(event, plugin);
        String number = registry.register(record);
        announceFailure(event.getName(), event.getLoginResult().name(), number);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (!logFailed || event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        reportFailure(event.getPlayer().getUniqueId(),
                () -> ConnectionSnapshot.forLogin(event, plugin),
                event.getPlayer().getName(), event.getResult().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!logFailed || event.isAllowed()) {
            return;
        }
        reportFailure(ConnectionSnapshot.uuidOf(event),
                () -> ConnectionSnapshot.forValidateLogin(event, plugin),
                ConnectionSnapshot.nameOf(event), "VALIDATION_FAILED");
    }

    /**
     * Catch-all for connections that are dropped without an explicit deny - the
     * generic "{@code /ip:port lost connection: ...}" case the server logs.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        UUID uuid = event.getPlayerUniqueId();
        boolean hadJoined = joinedConnections.remove(uuid);
        boolean alreadyReported = reportedFailures.remove(uuid);
        if (hadJoined || alreadyReported || !logFailed) {
            // Normal quit, a failure already reported by an earlier stage, or
            // failure logging disabled - nothing more to do.
            return;
        }
        ConnectionRecord record = ConnectionSnapshot.forConnectionClose(event, plugin);
        String number = registry.register(record);
        announceFailure(event.getPlayerName(), "CONNECTION_LOST", number);
    }

    /**
     * Registers and announces a failure once per connection. The UUID is held in
     * {@link #reportedFailures} so the later {@link PlayerConnectionCloseEvent}
     * for the same connection does not report it a second time.
     */
    private void reportFailure(UUID uuid, Supplier<ConnectionRecord> recordSupplier,
                               String name, String reason) {
        if (uuid != null && !reportedFailures.add(uuid)) {
            return;
        }
        ConnectionRecord record = recordSupplier.get();
        String number = registry.register(record);
        announceFailure(name, reason, number);
    }

    // ------------------------------------------------------------------
    //  Console output (console only, as required)
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
}
