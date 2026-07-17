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
package com.neramc.connectionverify.velocity;

import com.neramc.connectionverify.proxy.ProxyConfig;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /cnt <number>} - saves the stored control-tower report for a connection
 * number to a file. Intended for the console, but usable by anyone with the
 * {@code connectionverify.command.save} permission.
 */
public final class CntCommand implements SimpleCommand {

    private static final String PERMISSION = "connectionverify.command.save";

    private final ConnectionVerifyVelocity plugin;

    public CntCommand(ConnectionVerifyVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        ProxyConfig config = plugin.config();
        int length = config.numberLength();

        if (args.length != 1) {
            source.sendMessage(Component.text("Usage: /cnt <" + length + "-digit connection number>",
                    NamedTextColor.YELLOW));
            return;
        }
        String number = args[0];
        if (number.length() != length || !number.chars().allMatch(Character::isDigit)) {
            source.sendMessage(Component.text("The connection number must be exactly " + length + " digit(s).",
                    NamedTextColor.RED));
            return;
        }

        String report = plugin.registry().renderReport(number);
        if (report == null) {
            source.sendMessage(Component.text("No connection found for number " + number
                    + ". It may never have been issued, or it has been evicted.", NamedTextColor.RED));
            return;
        }

        Path directory = plugin.dataDirectory().resolve(config.logFolder());
        Path file = directory.resolve(number + ".txt");
        try {
            Files.createDirectories(directory);
            if (Files.exists(file) && !config.overwriteExisting()) {
                source.sendMessage(Component.text("A report for " + number
                        + " already exists. Enable file.overwrite-existing to replace it.", NamedTextColor.RED));
                return;
            }
            byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
            Files.write(file, bytes);
            source.sendMessage(Component.text("Saved connection report ", NamedTextColor.GREEN)
                    .append(Component.text(number, NamedTextColor.YELLOW))
                    .append(Component.text(" [" + bytes.length + " bytes] -> ", NamedTextColor.GRAY))
                    .append(Component.text(file.toAbsolutePath().toString(), NamedTextColor.AQUA)));
        } catch (IOException exception) {
            source.sendMessage(Component.text("Could not write the report: " + exception.getMessage(),
                    NamedTextColor.RED));
            plugin.logger().warn("Failed to write connection report {}", number, exception);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        String partial = args.length == 0 ? "" : args[0];
        return plugin.registry().numbers().stream()
                .filter(n -> n.startsWith(partial))
                .limit(50)
                .collect(Collectors.toList());
    }
}
