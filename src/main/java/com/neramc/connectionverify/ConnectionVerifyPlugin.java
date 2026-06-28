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
package com.neramc.connectionverify;

import com.neramc.connectionverify.command.AdminCommand;
import com.neramc.connectionverify.command.CntCommand;
import com.neramc.connectionverify.config.PluginConfig;
import com.neramc.connectionverify.connection.ConnectionRegistry;
import com.neramc.connectionverify.format.JsonRecordFormatter;
import com.neramc.connectionverify.format.RecordFormatter;
import com.neramc.connectionverify.format.TextRecordFormatter;
import com.neramc.connectionverify.i18n.Messages;
import com.neramc.connectionverify.io.LogWriter;
import com.neramc.connectionverify.listener.ConnectionListener;
import com.neramc.connectionverify.update.UpdateChecker;
import com.neramc.connectionverify.watch.NetworkDropWatcher;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Connection Verify - entry point and service container.
 *
 * <p>On join (or a failed/dropped connection) a connection number is printed to
 * the console; running {@code /cnt <number>} writes the captured details to a
 * file. Behaviour is driven entirely by {@code config.yml} and the messages by
 * the {@code lang/*.yml} files, both reloadable with
 * {@code /connectionverify reload}.</p>
 */
public final class ConnectionVerifyPlugin extends JavaPlugin {

    private static final long PURGE_INTERVAL_TICKS = 1200L; // ~60 seconds

    private final Properties buildInfo = new Properties();

    private volatile PluginConfig config;
    private volatile Messages messages;
    private volatile RecordFormatter formatter;
    private ConnectionRegistry registry;
    private LogWriter logWriter;
    private BukkitTask purgeTask;
    private NetworkDropWatcher networkDropWatcher;

    @Override
    public void onEnable() {
        saveDefaultResources();
        loadBuildInfo();
        this.logWriter = new LogWriter(this);

        load();

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        bindCommand("cnt", new CntCommand(this));
        bindCommand("connectionverify", new AdminCommand(this));

        logStartupBanner();

        if (config.updateCheckerEnabled()) {
            new UpdateChecker(this, messages).checkAsync();
        }
    }

    @Override
    public void onDisable() {
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
        }
        if (networkDropWatcher != null) {
            networkDropWatcher.uninstall();
            networkDropWatcher = null;
        }
        getLogger().info("Connection Verify disabled.");
    }

    /** Re-reads config.yml and the language files and re-applies all settings. */
    public synchronized void reload() {
        reloadConfig();
        load();
        getLogger().info("Configuration reloaded (language=" + config.language()
                + ", format=" + config.fileFormat() + ").");
    }

    // ------------------------------------------------------------------
    //  Service accessors (always reflect the current, reloaded state)
    // ------------------------------------------------------------------

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ConnectionRegistry registry() {
        return registry;
    }

    public RecordFormatter formatter() {
        return formatter;
    }

    public LogWriter logWriter() {
        return logWriter;
    }

    public Properties buildInfo() {
        return buildInfo;
    }

    /** Directory where {@code <number>.<ext>} connection logs are written. */
    public File logDirectory() {
        return new File(getDataFolder(), config.logFolder());
    }

    // ------------------------------------------------------------------
    //  Internal
    // ------------------------------------------------------------------

    private void load() {
        this.config = new PluginConfig(getConfig());
        this.messages = new Messages(this, config.language(), config.useColors());
        this.formatter = config.fileFormat() == PluginConfig.FileFormat.JSON
                ? new JsonRecordFormatter()
                : new TextRecordFormatter();

        if (registry == null) {
            registry = new ConnectionRegistry(config.numberLength(), config.leadingZeros(),
                    config.maxInMemory(), config.expireAfterMillis());
        } else {
            registry.configure(config.numberLength(), config.leadingZeros(),
                    config.maxInMemory(), config.expireAfterMillis());
        }

        File directory = logDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            getLogger().warning("Could not create the log directory: " + directory.getAbsolutePath());
        }

        scheduleExpiryPurge();
        syncNetworkDropWatcher();
    }

    /**
     * Installs or removes the log-based network-drop watcher to match the
     * current {@code logging.network-drops} setting. Safe to call on every
     * reload: it is a no-op when the desired and actual states already agree.
     */
    private void syncNetworkDropWatcher() {
        boolean wanted = config.networkDrops();
        if (wanted && networkDropWatcher == null) {
            networkDropWatcher = NetworkDropWatcher.install(this);
        } else if (!wanted && networkDropWatcher != null) {
            networkDropWatcher.uninstall();
            networkDropWatcher = null;
        }
    }

    private void scheduleExpiryPurge() {
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
        }
        if (config.expireAfterMinutes() > 0L) {
            purgeTask = getServer().getScheduler().runTaskTimerAsynchronously(
                    this, registry::purgeExpired, PURGE_INTERVAL_TICKS, PURGE_INTERVAL_TICKS);
        }
    }

    private void saveDefaultResources() {
        saveDefaultConfig();
        saveResourceIfAbsent("lang/en.yml");
        saveResourceIfAbsent("lang/ko.yml");
    }

    private void saveResourceIfAbsent(String resource) {
        if (!new File(getDataFolder(), resource).exists()) {
            saveResource(resource, false);
        }
    }

    private void loadBuildInfo() {
        try (InputStream in = getResource("build-info.properties")) {
            if (in != null) {
                buildInfo.load(in);
            }
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not load build-info.properties", exception);
        }
    }

    private void bindCommand(String name, Object handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        if (handler instanceof org.bukkit.command.CommandExecutor executor) {
            command.setExecutor(executor);
        }
        if (handler instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void logStartupBanner() {
        getLogger().info("Connection Verify v" + buildInfo.getProperty("version", getPluginMeta().getVersion())
                + " (target " + buildInfo.getProperty("target", "?")
                + ", Minecraft " + buildInfo.getProperty("mc-range", "?") + ") enabled.");
        getLogger().info("  Language    : " + config.language());
        getLogger().info("  Log format  : " + config.fileFormat() + "  ->  " + logDirectory().getAbsolutePath());
        getLogger().info("  Save a log  : cnt <" + config.numberLength() + "-digit connection number>");
    }
}
