package com.elysia.fakeinspector.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** 服务端 -> 客户端：返回所有假人及其背包内容。 */
public record FakePlayerResponsePayload(List<FakePlayerData> players) implements CustomPacketPayload {
    public static final Type<FakePlayerResponsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fakeinspector", "response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakePlayerResponsePayload> STREAM_CODEC =
            StreamCodec.of(FakePlayerResponsePayload::write, FakePlayerResponsePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, FakePlayerResponsePayload payload) {
        buf.writeVarInt(payload.players().size());
        for (FakePlayerData p : payload.players()) {
            buf.writeUtf(p.name(), 64);
            buf.writeVarInt(p.slots().size());
            for (FakeSlot s : p.slots()) {
                buf.writeVarInt(s.slot());
                buf.writeUtf(s.itemId(), 128);
                buf.writeVarInt(s.count());
            }
        }
    }

    private static FakePlayerResponsePayload read(RegistryFriendlyByteBuf buf) {
        int playerCount = buf.readVarInt();
        List<FakePlayerData> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            String name = buf.readUtf(64);
            int slotCount = buf.readVarInt();
            List<FakeSlot> slots = new ArrayList<>(slotCount);
            for (int j = 0; j < slotCount; j++) {
                int slot = buf.readVarInt();
                String itemId = buf.readUtf(128);
                int count = buf.readVarInt();
                slots.add(new FakeSlot(slot, itemId, count));
            }
            players.add(new FakePlayerData(name, slots));
        }
        return new FakePlayerResponsePayload(players);
    }
}
