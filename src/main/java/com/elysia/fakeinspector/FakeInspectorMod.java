package com.elysia.fakeinspector;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakePlayerQueryPayload;
import com.elysia.fakeinspector.networking.FakePlayerResponsePayload;
import com.elysia.fakeinspector.server.FakePlayerCollector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 主入口：注册网络协议、服务端逻辑与本地文件持久化。 */
public class FakeInspectorMod implements ModInitializer {
    public static final String MOD_ID = "fake-player-inspector";

    private static final Path DATA_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<List<FakePlayerData>>() {
    }.getType();

    private int tickCounter = 0;
    private static List<FakePlayerData> lastSaved = List.of();

    @Override
    public void onInitialize() {
        loadFromFile();

        PayloadTypeRegistry.serverboundPlay().register(FakePlayerQueryPayload.TYPE, FakePlayerQueryPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerResponsePayload.TYPE, FakePlayerResponsePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(FakePlayerQueryPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                FakePlayerResponsePayload response =
                        new FakePlayerResponsePayload(FakePlayerCollector.collect(context.server()));
                ServerPlayNetworking.send(context.player(), response);
            });
        });

        // 服务端轻量检查假人背包；仅在数据变化（放入物品/新增假人）时写一次文件
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;
                List<FakePlayerData> now = FakePlayerCollector.collect(server);
                if (!FakePlayerCollector.sameData(lastSaved, now)) {
                    lastSaved = now;
                    saveToFile();
                }
            }
        });
    }

    private static void loadFromFile() {
        try {
            if (Files.exists(DATA_FILE)) {
                String json = Files.readString(DATA_FILE, StandardCharsets.UTF_8);
                List<FakePlayerData> data = GSON.fromJson(json, DATA_TYPE);
                FakePlayerCollector.load(data);
            }
        } catch (IOException | RuntimeException ex) {
            // 读取失败时忽略，从空缓存开始
        }
    }

    private static void saveToFile() {
        try {
            Path parent = DATA_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = GSON.toJson(FakePlayerCollector.snapshot(), DATA_TYPE);
            Path tmp = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, DATA_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException ex) {
            // 写失败不影响游戏运行
        }
    }
}
