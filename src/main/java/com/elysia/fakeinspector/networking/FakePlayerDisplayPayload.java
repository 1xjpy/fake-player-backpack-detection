package com.elysia.fakeinspector.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端 -> 客户端：切换「悬停显示假人背包」开关。 */
public record FakePlayerDisplayPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<FakePlayerDisplayPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fakeinspector", "display"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerDisplayPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> buf.writeBoolean(value.enabled()), buf -> new FakePlayerDisplayPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
