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
package com.neramc.connectionverify.fabric.client;

import com.neramc.connectionverify.fabric.ClientInfoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Client entry point: the companion that, on joining a server running the
 * Connection Verify mod, gathers advanced client-side details the server cannot
 * otherwise see (loaded mods, FPS, view distance, OS, ...) and sends them over
 * the custom payload channel. It only sends when the server can receive it, so
 * it is harmless on vanilla or modless servers.
 */
public final class ConnectionVerifyFabricClient implements ClientModInitializer {

    private static final int MAX_LENGTH = 30_000;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ClientInfoPayload.ID)) {
                client.execute(() -> ClientPlayNetworking.send(new ClientInfoPayload(gather(client))));
            }
        });
    }

    private static String gather(MinecraftClient client) {
        StringBuilder sb = new StringBuilder();
        sb.append("Client brand: ").append(ClientBrandRetriever.getClientModName()).append('\n');
        sb.append("Fabric loader: ").append(FabricLoader.getInstance().getModContainer("fabricloader")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?")).append('\n');

        var mods = FabricLoader.getInstance().getAllMods();
        sb.append("Mods loaded: ").append(mods.size()).append('\n');
        sb.append("Mod list: ").append(mods.stream()
                .map(mod -> mod.getMetadata().getId())
                .sorted()
                .collect(Collectors.joining(", "))).append('\n');

        sb.append("FPS: ").append(client.getCurrentFps()).append('\n');
        try {
            sb.append("View distance: ").append(client.options.getViewDistance().getValue()).append('\n');
            sb.append("Max frame rate: ").append(client.options.getMaxFps().getValue()).append('\n');
        } catch (Throwable ignored) {
            // Option shape differs on this build; skip it.
        }
        sb.append("OS: ").append(System.getProperty("os.name"))
                .append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("Max memory: ")
                .append(String.format(Locale.ROOT, "%.1f MB", Runtime.getRuntime().maxMemory() / 1048576.0D))
                .append('\n');

        String data = sb.toString();
        return data.length() > MAX_LENGTH ? data.substring(0, MAX_LENGTH) : data;
    }
}
