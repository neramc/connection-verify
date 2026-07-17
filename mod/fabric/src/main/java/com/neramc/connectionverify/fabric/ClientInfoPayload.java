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

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The custom client-to-server payload carrying advanced client details gathered
 * by the companion (mod list, FPS, view distance, OS, ...). The body is a single
 * pre-formatted string so the server can merge it straight into a report.
 */
public record ClientInfoPayload(String data) implements CustomPayload {

    public static final CustomPayload.Id<ClientInfoPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ConnectionVerifyFabric.MOD_ID, "client_info"));

    public static final PacketCodec<PacketByteBuf, ClientInfoPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.data()),
            buf -> new ClientInfoPayload(buf.readString()));

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
