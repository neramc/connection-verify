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

import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Client entry point: the companion that, on joining a server running the
 * Connection Verify mod, gathers advanced client-side details the server cannot
 * otherwise see (loaded mods, FPS, render distance, OS, ...) and sends them over
 * the custom payload channel. The channel is registered as optional, so this is
 * harmless on vanilla or modless servers - the send is simply dropped.
 */
@Mod(value = ConnectionVerifyNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class ConnectionVerifyNeoForgeClient {

    private static final int MAX_LENGTH = 30_000;

    public ConnectionVerifyNeoForgeClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onLoggingIn);
    }

    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            PacketDistributor.sendToServer(new ClientInfoPayload(gather()));
        } catch (Throwable ignored) {
            // The server did not register the optional companion channel; nothing to send.
        }
    }

    private static String gather() {
        Minecraft client = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("Client brand: ").append(ClientBrandRetriever.getClientModName()).append('\n');

        var mods = ModList.get().getMods();
        sb.append("Mods loaded: ").append(mods.size()).append('\n');
        sb.append("Mod list: ").append(mods.stream()
                .map(mod -> mod.getModId())
                .sorted()
                .collect(Collectors.joining(", "))).append('\n');

        try {
            sb.append("FPS: ").append(Minecraft.getInstance().getFps()).append('\n');
            sb.append("Render distance: ").append(client.options.renderDistance().get()).append('\n');
            sb.append("Max frame rate: ").append(client.options.framerateLimit().get()).append('\n');
        } catch (Throwable ignored) {
            // Option/telemetry shape differs on this build; skip those fields.
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
