package com.neramc.connectionverify.listener;

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
 *       failures that happen before or instead of the events above.</li>
 * </ul>
 *
 * <p>All handlers run at {@link EventPriority#MONITOR} so the final,
 * post-plugin result is what gets recorded.</p>
 */
public final class ConnectionListener implements Listener {

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
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
        ConnectionRecord record = ConnectionSnapshot.forPreLogin(event, plugin);
        String number = registry.register(record);
        announceFailure(event.getName(), event.getLoginResult().name(), number);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (!logFailed || event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        ConnectionRecord record = ConnectionSnapshot.forLogin(event, plugin);
        String number = registry.register(record);
        announceFailure(event.getPlayer().getName(), event.getResult().name(), number);
    }

    /**
     * Catches login/validation-stage failures that occur before (or instead of)
     * {@link AsyncPlayerPreLoginEvent} / {@link PlayerLoginEvent}. Because a
     * connection is rejected at exactly one stage, this never double-reports a
     * failure already covered by the handlers above.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!logFailed || event.isAllowed()) {
            return;
        }
        ConnectionRecord record = ConnectionSnapshot.forValidateLogin(event, plugin);
        String number = registry.register(record);
        announceFailure(ConnectionSnapshot.nameOf(event), "VALIDATION_FAILED", number);
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
