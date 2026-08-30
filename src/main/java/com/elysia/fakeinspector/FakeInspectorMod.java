package com.elysia.fakeinspector;

import com.elysia.fakeinspector.networking.FakePlayerQueryPayload;
import com.elysia.fakeinspector.networking.FakePlayerResponsePayload;
import com.elysia.fakeinspector.server.FakePlayerCollector;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** 主入口：注册网络协议与服务端逻辑。 */
public class FakeInspectorMod implements ModInitializer {
    public static final String MOD_ID = "fake-player-inspector";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(FakePlayerQueryPayload.TYPE, FakePlayerQueryPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerResponsePayload.TYPE, FakePlayerResponsePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(FakePlayerQueryPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                FakePlayerResponsePayload response =
                        new FakePlayerResponsePayload(FakePlayerCollector.collect(context.server()));
                ServerPlayNetworking.send(context.player(), response);
            });
        });
    }
}
