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
package com.neramc.connectionverify.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads localized, MiniMessage-formatted messages from {@code lang/<code>.yml}
 * and renders them to Adventure components.
 *
 * <p>The selected language file is read from the plugin's data folder so server
 * owners can edit it. Any missing key falls back to the bundled English file,
 * then to the key name itself, so a message is always produced.</p>
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String FALLBACK_LANGUAGE = "en";

    private final JavaPlugin plugin;
    private final boolean useColors;
    private final FileConfiguration primary;
    private final FileConfiguration fallback;
    private final String languageCode;

    public Messages(JavaPlugin plugin, String languageCode, boolean useColors) {
        this.plugin = plugin;
        this.useColors = useColors;
        this.languageCode = languageCode;
        this.fallback = loadBundled(FALLBACK_LANGUAGE);
        this.primary = resolvePrimary(languageCode);
    }

    /** Creates an unparsed (injection-safe) placeholder. */
    public static TagResolver placeholder(String name, Object value) {
        return Placeholder.unparsed(name, value == null ? "" : String.valueOf(value));
    }

    public String languageCode() {
        return languageCode;
    }

    /** Renders a key to a component, applying the given placeholders. */
    public Component component(String key, TagResolver... placeholders) {
        String template = string(key);
        return MINI.deserialize(template, placeholders);
    }

    /** Renders the configured message prefix. */
    public Component prefix() {
        return MINI.deserialize(string("prefix"));
    }

    /** Sends a prefixed, color-aware message to a command sender. */
    public void send(CommandSender to, String key, TagResolver... placeholders) {
        to.sendMessage(colorize(prefix().append(component(key, placeholders))));
    }

    /**
     * Sends an unprefixed, color-aware message to the server console. Safe to
     * call from any thread; if the console audience rejects an off-thread send,
     * the line is emitted through the plugin logger so it is never lost.
     */
    public void console(String key, TagResolver... placeholders) {
        Component line = colorize(component(key, placeholders));
        try {
            plugin.getServer().getConsoleSender().sendMessage(line);
        } catch (Throwable throwable) {
            plugin.getLogger().info(PLAIN.serialize(line));
        }
    }

    /** Returns the raw template string for a key, with fallbacks. */
    public String string(String key) {
        String value = primary.getString(key);
        if (value == null) {
            value = fallback.getString(key);
        }
        return value == null ? key : value;
    }

    private Component colorize(Component component) {
        if (useColors) {
            return component;
        }
        // Strip all styling for plain-text log setups.
        return Component.text(PLAIN.serialize(component));
    }

    private FileConfiguration resolvePrimary(String code) {
        File file = new File(new File(plugin.getDataFolder(), "lang"), code + ".yml");
        if (file.isFile()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        FileConfiguration bundled = loadBundled(code);
        if (bundled != null) {
            return bundled;
        }
        plugin.getLogger().warning("Language '" + code + "' not found; falling back to English.");
        return fallback;
    }

    private FileConfiguration loadBundled(String code) {
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) {
                return code.equals(FALLBACK_LANGUAGE) ? new YamlConfiguration() : null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to read bundled language '" + code + "'", exception);
            return code.equals(FALLBACK_LANGUAGE) ? new YamlConfiguration() : null;
        }
    }
}
