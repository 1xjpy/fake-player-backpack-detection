package com.elysia.fakeinspector.client;

import com.elysia.fakeinspector.networking.FakePlayerQueryPayload;
import com.elysia.fakeinspector.networking.FakePlayerResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 客户端入口：注册 tooltip 显示、数据刷新按键与网络接收。 */
public class FakeInspectorClient implements ClientModInitializer {
    private int autoTicks = 0;

    @Override
    public void onInitializeClient() {
        ClientFakeDataCache.init();

        // 接收服务端假人背包数据
        ClientPlayNetworking.registerGlobalReceiver(FakePlayerResponsePayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> ClientFakeDataCache.set(payload.players()));
        });

        // 加入世界时自动请求一次
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> ClientPlayNetworking.send(new FakePlayerQueryPayload())));

        // 兜底：进游戏后如果还没有假人数据，自动请求，直到拿到为止
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && ClientFakeDataCache.get().isEmpty()) {
                if (++autoTicks >= 40) {
                    autoTicks = 0;
                    ClientPlayNetworking.send(new FakePlayerQueryPayload());
                }
            } else {
                autoTicks = 0;
            }
        });

        // 在物品 tooltip 末尾追加假人背包数量与名字
        ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
            List<ClientFakeDataCache.Holder> holders = ClientFakeDataCache.holdersFor(stack);
            if (holders.isEmpty()) {
                return;
            }
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).getString().isEmpty()) {
                lines.add(Component.empty());
            }
            for (ClientFakeDataCache.Holder h : holders) {
                lines.add(Component.literal(h.name() + " 背包 × " + h.count()).withStyle(ChatFormatting.AQUA));
            }
        });
    }
}
