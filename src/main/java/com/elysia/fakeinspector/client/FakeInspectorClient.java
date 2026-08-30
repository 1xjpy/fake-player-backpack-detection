package com.elysia.fakeinspector.client;

import com.elysia.fakeinspector.networking.FakePlayerQueryPayload;
import com.elysia.fakeinspector.networking.FakePlayerResponsePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** 客户端入口：注册 tooltip 显示、数据刷新按键与网络接收。 */
public class FakeInspectorClient implements ClientModInitializer {

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

        // 按键手动刷新
        KeyMapping refreshKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fakeinspector.refresh",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyMapping.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (refreshKey.consumeClick()) {
                ClientPlayNetworking.send(new FakePlayerQueryPayload());
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
