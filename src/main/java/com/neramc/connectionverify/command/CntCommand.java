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
package com.neramc.connectionverify.command;

import com.neramc.connectionverify.ConnectionVerifyPlugin;
import com.neramc.connectionverify.connection.ConnectionRecord;
import com.neramc.connectionverify.i18n.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles {@code /cnt <number>}: writes the stored connection details for a
 * connection number to a file, in the configured format, off the main thread
 * when enabled.
 */
public final class CntCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "connectionverify.command.save";

    private final ConnectionVerifyPlugin plugin;

    public CntCommand(ConnectionVerifyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages messages = plugin.messages();
        int length = plugin.config().numberLength();

        if (args.length != 1) {
            messages.send(sender, "command.cnt.usage", Messages.placeholder("length", length));
            return true;
        }

        String number = args[0];
        if (!number.matches("\\d{" + length + "}")) {
            messages.send(sender, "command.cnt.invalid-number", Messages.placeholder("length", length));
            return true;
        }

        ConnectionRecord record = plugin.registry().get(number);
        if (record == null) {
            messages.send(sender, "command.cnt.not-found", Messages.placeholder("number", number));
            return true;
        }

        File file = new File(plugin.logDirectory(), number + "." + plugin.formatter().extension());
        if (file.exists() && !plugin.config().overwriteExisting()) {
            messages.send(sender, "command.cnt.already-exists", Messages.placeholder("number", number));
            return true;
        }

        byte[] content = plugin.formatter().format(record, Instant.now()).getBytes(StandardCharsets.UTF_8);
        plugin.logWriter().write(file, content, plugin.config().asyncWrite(), result -> {
            if (result.success()) {
                plugin.messages().send(sender, "command.cnt.saved",
                        Messages.placeholder("number", number),
                        Messages.placeholder("status", record.status().name()),
                        Messages.placeholder("bytes", result.bytes()),
                        Messages.placeholder("path", file.getAbsolutePath()));
                plugin.getLogger().info("Saved connection log " + number
                        + " [" + record.status() + "] -> " + file.getAbsolutePath());
            } else {
                String reason = result.error() == null ? "unknown" : result.error().getMessage();
                plugin.messages().send(sender, "command.cnt.write-error", Messages.placeholder("error", reason));
                plugin.getLogger().warning("Failed to write connection log " + number + ": " + reason);
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        String prefix = args[0];
        List<String> suggestions = new ArrayList<>();
        for (String known : plugin.registry().numbers()) {
            if (known.startsWith(prefix)) {
                suggestions.add(known);
            }
        }
        return suggestions;
    }
}
