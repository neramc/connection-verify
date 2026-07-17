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
package com.neramc.connectionverify.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server/common entry point for the NeoForge edition.
 *
 * <p>Numbers every connection on join, logs the number to the console, and
 * writes a full report with {@code /cnt <number>}. The report includes the
 * advanced client details sent by the {@code ConnectionVerifyNeoForgeClient}
 * companion over a custom payload channel.</p>
 */
@Mod(ConnectionVerifyNeoForge.MOD_ID)
public final class ConnectionVerifyNeoForge {

    public static final String MOD_ID = "connectionverify";
    public static final Logger LOGGER = LoggerFactory.getLogger("ConnectionVerify");
    public static final ConnectionReports REPORTS = new ConnectionReports();

    public ConnectionVerifyNeoForge(IEventBus modBus) {
        // Payload registration is a mod-bus event; gameplay events are on the game bus.
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        LOGGER.info("Connection Verify (NeoForge) loaded - numbering every connection.");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Optional so a modded client can still join servers that do not have it,
        // and this mod stays silent toward clients that never registered it.
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ClientInfoPayload.TYPE, ClientInfoPayload.STREAM_CODEC, this::onClientInfo);
    }

    private void onClientInfo(ClientInfoPayload payload, IPayloadContext context) {
        UUID uuid = context.player().getUUID();
        REPORTS.attachClientInfo(uuid, payload.data());
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String name = player.getGameProfile().getName();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Name", name);
        fields.put("UUID", player.getUUID().toString());
        fields.put("Entity id", String.valueOf(player.getId()));
        String number = REPORTS.open(player.getUUID(), fields);
        LOGGER.info("Join {}", name);
        LOGGER.info("Connection number {}", number);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cnt")
                .requires(source -> source.hasPermission(3))
                .then(Commands.argument("number", StringArgumentType.word())
                        .executes(ctx -> saveReport(ctx.getSource(),
                                StringArgumentType.getString(ctx, "number")))));
    }

    private int saveReport(CommandSourceStack source, String number) {
        if (number.length() != 4 || !number.chars().allMatch(Character::isDigit)) {
            source.sendFailure(Component.literal("The connection number must be exactly 4 digits."));
            return 0;
        }
        String report = REPORTS.render(number);
        if (report == null) {
            source.sendFailure(Component.literal("No connection found for number " + number
                    + ". It may never have been issued, or it has been evicted."));
            return 0;
        }
        try {
            Path directory = source.getServer().getServerDirectory().resolve("connection-verify");
            Files.createDirectories(directory);
            Path file = directory.resolve(number + ".txt");
            Files.write(file, report.getBytes(StandardCharsets.UTF_8));
            Path shown = file.toAbsolutePath();
            source.sendSuccess(() -> Component.literal("Saved connection report " + number + " -> " + shown), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Could not write the report: " + exception.getMessage()));
            LOGGER.warn("Failed to write connection report {}", number, exception);
            return 0;
        }
    }
}
