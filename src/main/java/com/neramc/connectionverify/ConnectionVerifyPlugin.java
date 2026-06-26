package com.neramc.connectionverify;

import com.neramc.connectionverify.command.CntCommand;
import com.neramc.connectionverify.listener.ConnectionListener;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Connection Verify - main plugin entry point.
 *
 * <p>When a player connects, a random 4-digit "connection number" is printed to
 * the console below the join message (failed connections get one too). Running
 * {@code cnt <number>} in the console writes the full connection details and
 * metadata for that number to
 * {@code plugins/connection-verify/connection/<number>.txt}.</p>
 */
public final class ConnectionVerifyPlugin extends JavaPlugin {

    private final ConnectionRegistry registry = new ConnectionRegistry();
    private File logDirectory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String folderName = getConfig().getString("log-folder", "connection");
        logDirectory = new File(getDataFolder(), folderName);
        if (!logDirectory.exists() && !logDirectory.mkdirs()) {
            getLogger().warning("Could not create the connection log directory: "
                    + logDirectory.getAbsolutePath());
        }

        boolean logSuccessful = getConfig().getBoolean("log-successful-connections", true);
        boolean logFailed = getConfig().getBoolean("log-failed-connections", true);

        getServer().getPluginManager().registerEvents(
                new ConnectionListener(this, registry, logSuccessful, logFailed), this);

        PluginCommand command = getCommand("cnt");
        if (command != null) {
            CntCommand cnt = new CntCommand(this, registry);
            command.setExecutor(cnt);
            command.setTabCompleter(cnt);
        } else {
            getLogger().severe("Command 'cnt' is missing from plugin.yml - "
                    + "connection logs cannot be saved!");
        }

        getLogger().info("Connection Verify v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("  Log directory       : " + logDirectory.getAbsolutePath());
        getLogger().info("  Log successful joins : " + logSuccessful);
        getLogger().info("  Log failed attempts  : " + logFailed);
        getLogger().info("  Save a log with      : cnt <4-digit connection number>");
    }

    @Override
    public void onDisable() {
        getLogger().info("Connection Verify disabled.");
    }

    /** Directory where {@code <number>.txt} connection logs are written. */
    public File logDirectory() {
        return logDirectory;
    }

    /**
     * Sends one or more lines to the server console, hopping to the main thread
     * first when called from an asynchronous login event so the Bukkit API is
     * always touched from a safe thread.
     *
     * @param lines the console lines to print, in order
     */
    public void console(Component... lines) {
        Runnable task = () -> {
            for (Component line : lines) {
                getServer().getConsoleSender().sendMessage(line);
            }
        };
        if (getServer().isPrimaryThread()) {
            task.run();
        } else {
            getServer().getScheduler().runTask(this, task);
        }
    }
}
