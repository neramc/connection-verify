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
package com.neramc.connectionverify.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server/common entry point for the Fabric edition.
 *
 * <p>Numbers every connection on join, logs the number to the console, and
 * writes a full report with {@code /cnt <number>}. The report includes the
 * advanced client details sent by the {@code ConnectionVerifyFabricClient}
 * companion over a custom payload channel.</p>
 */
public final class ConnectionVerifyFabric implements ModInitializer {

    public static final String MOD_ID = "connectionverify";
    public static final Logger LOGGER = LoggerFactory.getLogger("ConnectionVerify");
    public static final ConnectionReports REPORTS = new ConnectionReports();

    @Override
    public void onInitialize() {
        // Register the companion payload (runs on both client and server).
        PayloadTypeRegistry.playC2S().register(ClientInfoPayload.ID, ClientInfoPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClientInfoPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            REPORTS.attachClientInfo(player.getUuid(), payload.data());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));

        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(CommandManager.literal("cnt")
                        .requires(source -> source.hasPermissionLevel(3))
                        .then(CommandManager.argument("number", StringArgumentType.word())
                                .executes(ctx -> saveReport(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "number"))))));

        LOGGER.info("Connection Verify (Fabric) loaded - numbering every connection.");
    }

    private void onJoin(ServerPlayerEntity player) {
        String name = player.getGameProfile().getName();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Name", name);
        fields.put("UUID", player.getUuid().toString());
        fields.put("Entity id", String.valueOf(player.getId()));
        String number = REPORTS.open(player.getUuid(), fields);
        LOGGER.info("Join {}", name);
        LOGGER.info("Connection number {}", number);
    }

    private int saveReport(ServerCommandSource source, String number) {
        if (number.length() != 4 || !number.chars().allMatch(Character::isDigit)) {
            source.sendFeedback(() -> Text.literal("The connection number must be exactly 4 digits."), false);
            return 0;
        }
        String report = REPORTS.render(number);
        if (report == null) {
            source.sendFeedback(() -> Text.literal("No connection found for number " + number
                    + ". It may never have been issued, or it has been evicted."), false);
            return 0;
        }
        try {
            Path directory = FabricLoader.getInstance().getGameDir().resolve("connection-verify");
            Files.createDirectories(directory);
            Path file = directory.resolve(number + ".txt");
            Files.write(file, report.getBytes(StandardCharsets.UTF_8));
            Path shown = file.toAbsolutePath();
            source.sendFeedback(() -> Text.literal("Saved connection report " + number + " -> " + shown), false);
            return 1;
        } catch (IOException exception) {
            source.sendFeedback(() -> Text.literal("Could not write the report: " + exception.getMessage()), false);
            LOGGER.warn("Failed to write connection report {}", number, exception);
            return 0;
        }
    }
}
