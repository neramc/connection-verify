package com.neramc.connectionverify.command;

import com.neramc.connectionverify.ConnectionRecord;
import com.neramc.connectionverify.ConnectionRegistry;
import com.neramc.connectionverify.ConnectionVerifyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Handles {@code cnt <number>}: writes the connection details and metadata held
 * for {@code <number>} to {@code <log-folder>/<number>.txt}.
 */
public final class CntCommand implements CommandExecutor, TabCompleter {

    private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");

    private final ConnectionVerifyPlugin plugin;
    private final ConnectionRegistry registry;

    public CntCommand(ConnectionVerifyPlugin plugin, ConnectionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /cnt <4-digit connection number>", NamedTextColor.YELLOW));
            return true;
        }

        String number = args[0];
        if (!FOUR_DIGITS.matcher(number).matches()) {
            sender.sendMessage(Component.text(
                    "The connection number must be exactly 4 digits (0000-9999).", NamedTextColor.RED));
            return true;
        }

        ConnectionRecord record = registry.get(number);
        if (record == null) {
            sender.sendMessage(Component.text(
                    "No connection found for number " + number
                            + ". It may never have been issued, or it was recorded before the last restart.",
                    NamedTextColor.RED));
            return true;
        }

        File directory = plugin.logDirectory();
        File file = new File(directory, number + ".txt");
        byte[] content = record.render(Instant.now()).getBytes(StandardCharsets.UTF_8);
        try {
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }
            Files.write(file.toPath(), content);
        } catch (IOException exception) {
            sender.sendMessage(Component.text(
                    "Failed to write the connection log: " + exception.getMessage(), NamedTextColor.RED));
            plugin.getLogger().log(Level.SEVERE, "Failed to write connection log for " + number, exception);
            return true;
        }

        sender.sendMessage(Component.text("Connection log ", NamedTextColor.GREEN)
                .append(Component.text(number, NamedTextColor.YELLOW))
                .append(Component.text(" [" + record.status() + ", " + content.length + " bytes] saved to ",
                        NamedTextColor.GREEN))
                .append(Component.text(file.getAbsolutePath(), NamedTextColor.AQUA)));
        plugin.getLogger().info("Saved connection log " + number
                + " [" + record.status() + "] -> " + file.getAbsolutePath());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0];
        List<String> suggestions = new ArrayList<>();
        for (String knownNumber : registry.numbers()) {
            if (knownNumber.startsWith(prefix)) {
                suggestions.add(knownNumber);
            }
        }
        return suggestions;
    }
}
