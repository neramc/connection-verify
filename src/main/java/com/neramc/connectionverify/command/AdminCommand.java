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
import com.neramc.connectionverify.i18n.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Handles {@code /connectionverify <reload|info|version>}. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "connectionverify.command.admin";
    private static final List<String> SUBCOMMANDS = List.of("reload", "info", "version");

    private final ConnectionVerifyPlugin plugin;

    public AdminCommand(ConnectionVerifyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages messages = plugin.messages();
        if (args.length == 0) {
            messages.send(sender, "command.admin.usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                plugin.messages().send(sender, "command.admin.reloaded");
            }
            case "info" -> messages.send(sender, "command.admin.info",
                    Messages.placeholder("records", plugin.registry().size()),
                    Messages.placeholder("language", plugin.config().language()),
                    Messages.placeholder("folder", plugin.config().logFolder()));
            case "version" -> messages.send(sender, "command.admin.version",
                    Messages.placeholder("version", plugin.buildInfo().getProperty("version", "?")),
                    Messages.placeholder("target", plugin.buildInfo().getProperty("target", "?")),
                    Messages.placeholder("api", plugin.buildInfo().getProperty("api", "?")));
            default -> messages.send(sender, "command.unknown-subcommand");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Stream.of("reload", "info", "version").filter(s -> s.startsWith(prefix)).toList();
    }
}
