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
package com.neramc.connectionverify.bungee;

import com.neramc.connectionverify.proxy.ProxyConfig;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code /cnt <number>} - saves the stored control-tower report for a connection
 * number to a file. Intended for the console; players need the
 * {@code connectionverify.command.save} permission.
 */
public final class CntCommand extends Command {

    private static final String PERMISSION = "connectionverify.command.save";

    private final ConnectionVerifyBungee plugin;

    public CntCommand(ConnectionVerifyBungee plugin) {
        super("cnt");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        boolean isPlayer = sender instanceof ProxiedPlayer;
        if (isPlayer && !sender.hasPermission(PERMISSION)) {
            sender.sendMessage(new TextComponent("You do not have permission to use this command."));
            return;
        }
        ProxyConfig config = plugin.config();
        int length = config.numberLength();

        if (args.length != 1) {
            sender.sendMessage(new TextComponent("Usage: /cnt <" + length + "-digit connection number>"));
            return;
        }
        String number = args[0];
        if (number.length() != length || !number.chars().allMatch(Character::isDigit)) {
            sender.sendMessage(new TextComponent("The connection number must be exactly " + length + " digit(s)."));
            return;
        }

        String report = plugin.registry().renderReport(number);
        if (report == null) {
            sender.sendMessage(new TextComponent("No connection found for number " + number
                    + ". It may never have been issued, or it has been evicted."));
            return;
        }

        Path directory = plugin.getDataFolder().toPath().resolve(config.logFolder());
        Path file = directory.resolve(number + ".txt");
        try {
            Files.createDirectories(directory);
            if (Files.exists(file) && !config.overwriteExisting()) {
                sender.sendMessage(new TextComponent("A report for " + number
                        + " already exists. Enable file.overwrite-existing to replace it."));
                return;
            }
            byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
            Files.write(file, bytes);
            sender.sendMessage(new TextComponent("Saved connection report " + number
                    + " [" + bytes.length + " bytes] -> " + file.toAbsolutePath()));
        } catch (IOException exception) {
            sender.sendMessage(new TextComponent("Could not write the report: " + exception.getMessage()));
            plugin.getLogger().warning("Failed to write connection report " + number + ": " + exception.getMessage());
        }
    }
}
