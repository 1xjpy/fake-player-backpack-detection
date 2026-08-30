package com.elysia.fakeinspector.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端 -> 服务端：请求所有假人及其背包。 */
public record FakePlayerQueryPayload() implements CustomPacketPayload {
    public static final Type<FakePlayerQueryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fakeinspector", "query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerQueryPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {}, buf -> new FakePlayerQueryPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
