package com.elysia.fakeinspector;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakePlayerQueryPayload;
import com.elysia.fakeinspector.networking.FakePlayerResponsePayload;
import com.elysia.fakeinspector.networking.FakeSlot;
import com.elysia.fakeinspector.server.FakePlayerCollector;
import com.elysia.fakeinspector.util.FakePlayerDetector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** 主入口：网络协议、服务端记录、本地文件持久化、离线读盘。 */
public class FakeInspectorMod implements ModInitializer {
    public static final String MOD_ID = "fake-player-inspector";

    private static final Path DATA_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector.json");
    private static final Path EVENT_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector-events.log");
    private static final Path NAMES_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector-names.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<List<FakePlayerData>>() {
    }.getType();
    private static final Type NAMES_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private int tickCounter = 0;
    private static List<FakePlayerData> lastSaved = List.of();
    private static final Map<String, String> lastOnline = new HashMap<>();
    private static final Map<String, String> knownNames = new HashMap<>();

    @Override
    public void onInitialize() {
        loadKnownNames();
        FakeInspectorCommand.register();
        PayloadTypeRegistry.serverboundPlay().register(FakePlayerQueryPayload.TYPE, FakePlayerQueryPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerResponsePayload.TYPE, FakePlayerResponsePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(FakePlayerQueryPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                FakePlayerResponsePayload response =
                        new FakePlayerResponsePayload(FakePlayerCollector.collect(context.server()));
                ServerPlayNetworking.send(context.player(), response);
            });
        });

        // 每次进入世界（服务器启动）都重新读该世界的玩家数据
        ServerLifecycleEvents.SERVER_STARTED.register(server -> loadOfflineFromDisk(server));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;
                recordFakePlayerEvents(server);
                List<FakePlayerData> now = FakePlayerCollector.collect(server);
                if (!FakePlayerCollector.sameData(lastSaved, now)) {
                    lastSaved = now;
                    saveToFile();
                    // 实时推送给所有在线客户端
                    FakePlayerResponsePayload response = new FakePlayerResponsePayload(now);
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(player, response);
                    }
                }
            }
        });
    }

    /** 用周期对比记录假人出现（spawn）与消失（kill）。 */
    private static void recordFakePlayerEvents(MinecraftServer server) {
        Map<String, String> now = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (FakePlayerDetector.isFakePlayer(p)) {
                now.put(p.getStringUUID(), p.getScoreboardName());
            }
        }
        for (Map.Entry<String, String> e : now.entrySet()) {
            if (!lastOnline.containsKey(e.getKey())) {
                appendEvent("spawn", e.getValue(), e.getKey());
            }
        }
        for (Map.Entry<String, String> e : lastOnline.entrySet()) {
            if (!now.containsKey(e.getKey())) {
                appendEvent("kill", e.getValue(), e.getKey());
            }
        }
        boolean nameDirty = false;
        for (Map.Entry<String, String> e : now.entrySet()) {
            if (knownNames.putIfAbsent(e.getKey(), e.getValue()) == null) {
                nameDirty = true;
            }
        }
        if (nameDirty) {
            saveNamesToFile();
        }
        lastOnline.clear();
        lastOnline.putAll(now);
    }

    private static void appendEvent(String type, String name, String uuid) {
        String line = Instant.now().toString()
                + "\t" + type
                + "\t" + name
                + "\t" + uuid
                + "\tplayers/data/" + uuid + ".dat";
        try {
            Path parent = EVENT_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(EVENT_FILE, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 忽略写入失败
        }
    }

    private static void saveNamesToFile() {
        try {
            Path parent = NAMES_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(NAMES_FILE, GSON.toJson(knownNames, NAMES_TYPE), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 忽略
        }
    }

    /** 从当前世界的 players\\data 读取所有离线玩家/假人的背包。 */
    private static void loadOfflineFromDisk(MinecraftServer server) {
        try {
            Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            if (!Files.exists(dir)) {
                return;
            }
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                online.add(p.getUUID());
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().endsWith(".dat")
                        && !f.getFileName().toString().endsWith("_old"))
                        .forEach(f -> {
                            String uuidStr = f.getFileName().toString().replace(".dat", "");
                            try {
                                UUID uu = UUID.fromString(uuidStr);
                                if (online.contains(uu)) {
                                    return;
                                }
                                CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
                                ListTag inv = tag.getListOrEmpty("Inventory");
                                List<FakeSlot> slots = new ArrayList<>();
                                int idx = 0;
                                for (Tag t : inv) {
                                    CompoundTag c = (CompoundTag) t;
                                    String id = c.getString("id").orElse("");
                                    int count = c.getInt("count").orElse(1);
                                    if (id == null || id.isEmpty()) {
                                        continue;
                                    }
                                    if (count <= 0) {
                                        count = 1;
                                    }
                                    slots.add(new FakeSlot(idx++, id, count));
                                }
                                String shortName = uuidStr.length() >= 8 ? uuidStr.substring(0, 8) : uuidStr;
                                String name = knownNames.getOrDefault(uuidStr, shortName);
                                FakePlayerCollector.putOffline(uuidStr, new FakePlayerData(name, slots));
                            } catch (Exception ignored) {
                                // 单个文件失败不影响其它
                            }
                        });
            }
        } catch (Exception ignored) {
            // 读取失败忽略
        }
    }

    /** 从事件日志恢复 uuid -> 假人真名 映射。 */
    private static void loadKnownNames() {
        // 先加载持久化的 uuid -> 名字
        try {
            if (Files.exists(NAMES_FILE)) {
                Map<String, String> m = GSON.fromJson(Files.readString(NAMES_FILE, StandardCharsets.UTF_8), NAMES_TYPE);
                if (m != null) {
                    knownNames.putAll(m);
                }
            }
        } catch (Exception ignored) {
            // 忽略
        }
        try {
            if (Files.exists(EVENT_FILE)) {
                for (String line : Files.readAllLines(EVENT_FILE, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 4) {
                        knownNames.putIfAbsent(parts[3], parts[2]);
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略
        }
        // 从命令历史恢复 /player <名字> spawn 的名字 -> 离线 UUID
        try {
            Path cmd = FabricLoader.getInstance().getGameDir().resolve("command_history.txt");
            if (Files.exists(cmd)) {
                for (String line : Files.readAllLines(cmd, StandardCharsets.UTF_8)) {
                    String raw = line.trim();
                    if (raw.startsWith("/player ")) {
                        String[] parts = raw.split("\\s+");
                        if (parts.length >= 2) {
                            String name = parts[1];
                            if (!name.isBlank()) {
                                UUID uu = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                                knownNames.putIfAbsent(uu.toString(), name);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略
        }
    }

    private static void loadFromFile() {
        try {
            if (Files.exists(DATA_FILE)) {
                String json = Files.readString(DATA_FILE, StandardCharsets.UTF_8);
                List<FakePlayerData> data = GSON.fromJson(json, DATA_TYPE);
                FakePlayerCollector.load(data);
            }
        } catch (IOException | RuntimeException ex) {
            // 读取失败时忽略
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
