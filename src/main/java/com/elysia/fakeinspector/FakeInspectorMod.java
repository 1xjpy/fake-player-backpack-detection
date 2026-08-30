package com.elysia.fakeinspector;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakePlayerDisplayPayload;
import com.elysia.fakeinspector.networking.FakePlayerQueryPayload;
import com.elysia.fakeinspector.networking.FakePlayerResponsePayload;
import com.elysia.fakeinspector.networking.FakeSlot;
import com.elysia.fakeinspector.server.FakePlayerCollector;
import com.elysia.fakeinspector.util.FakePlayerDetector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final Path FAKE_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector-fake.json");
    private static final Path REAL_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector-real.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<List<FakePlayerData>>() {
    }.getType();
    private static final Type NAMES_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private static final Type FAKE_TYPE = new TypeToken<Set<String>>() {
    }.getType();

    private int tickCounter = 0;
    private static List<FakePlayerData> lastSaved = List.of();
    private static final Map<String, String> lastOnline = new HashMap<>();
    private static final Map<String, String> knownNames = new HashMap<>();
    private static final Set<String> knownFakeUuids = new HashSet<>();
    /** 记录过的真实玩家 UUID（用于排除真人）。 */
    private static final Set<String> knownRealUuids = new HashSet<>();
    /** usercache.json 里的 uuid -> 名字，用于把 v4 假人的名字显示出来。 */
    private static final Map<String, String> uuidNameCache = new HashMap<>();
    /** 在 /player <名字> ... 命令里出现过的名字，视为召唤过的假人。 */
    private static final Set<String> spawnedBotNames = new HashSet<>();
    private static boolean autoRead = true;

    @Override
    public void onInitialize() {
        loadKnownNames();
        FakeInspectorCommand.register();
        PayloadTypeRegistry.serverboundPlay().register(FakePlayerQueryPayload.TYPE, FakePlayerQueryPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerResponsePayload.TYPE, FakePlayerResponsePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FakePlayerDisplayPayload.TYPE, FakePlayerDisplayPayload.STREAM_CODEC);

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
                if (!autoRead) {
                    return;
                }
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

    public static void setAutoRead(boolean enabled) {
        autoRead = enabled;
    }

    public static boolean isAutoRead() {
        return autoRead;
    }

    /** 用周期对比记录假人出现（spawn）与消失（kill）。 */
    private static void recordFakePlayerEvents(MinecraftServer server) {
        Map<String, String> now = new HashMap<>();
        boolean realDirty = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (FakePlayerDetector.isFakePlayer(p)) {
                now.put(p.getStringUUID(), p.getScoreboardName());
            } else if (knownRealUuids.add(p.getStringUUID())) {
                // 记录真实玩家（非假人），用于离线读档时把它们排除掉
                realDirty = true;
            }
        }
        if (realDirty) {
            saveRealUuids();
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
        boolean fakeDirty = false;
        for (String uuid : now.keySet()) {
            if (knownFakeUuids.add(uuid)) {
                fakeDirty = true;
            }
        }
        if (fakeDirty) {
            saveFakeUuids();
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

    private static void saveFakeUuids() {
        try {
            Path parent = FAKE_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(FAKE_FILE, GSON.toJson(knownFakeUuids, FAKE_TYPE), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 忽略
        }
    }

    private static void saveRealUuids() {
        try {
            Path parent = REAL_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(REAL_FILE, GSON.toJson(knownRealUuids, FAKE_TYPE), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 忽略
        }
    }

    /** 从当前世界的 players\\data 读取所有离线玩家/假人的背包。 */
    private static void loadOfflineFromDisk(MinecraftServer server) {
        if (!autoRead) {
            return;
        }
        // 进入新世界时先清空旧数据，避免上个世界的假人残留
        FakePlayerCollector.clear();
        try {
            Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            if (!Files.exists(dir)) {
                return;
            }
            // 只跳过在线真人；在线假人仍读取存档，保证自定义假人类也能显示
            Set<UUID> onlineReal = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!FakePlayerDetector.isFakePlayer(p)) {
                    onlineReal.add(p.getUUID());
                }
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().endsWith(".dat")
                        && !f.getFileName().toString().endsWith("_old"))
                        .forEach(f -> {
                            String uuidStr = f.getFileName().toString().replace(".dat", "");
                            try {
                                UUID uu = UUID.fromString(uuidStr);
                                if (onlineReal.contains(uu)) {
                                    return;
                                }
                                // 名字优先用已记录的真名，其次 usercache -> 真名
                                String resolvedName = knownNames.get(uuidStr);
                                if (resolvedName == null) {
                                    resolvedName = uuidNameCache.get(uuidStr);
                                }
                                // 假人判定：确认过 / v3 离线UUID / 查询到名字且不是真人
                                boolean isFake = knownFakeUuids.contains(uuidStr)
                                        || uu.version() == 3
                                        || (resolvedName != null
                                            && (spawnedBotNames.contains(resolvedName)
                                                || !knownRealUuids.contains(uuidStr)));
                                if (!isFake) {
                                    return;
                                }
                                // 后遍历：读取该假人的背包
                                CompoundTag tag = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
                                ListTag inv = tag.getListOrEmpty("Inventory");
                                List<FakeSlot> slots = new ArrayList<>();
                                int idx = 0;
                                for (Tag t : inv) {
                                    if (!(t instanceof CompoundTag c)) {
                                        continue;
                                    }
                                    // 用真实 Slot 编号作为槽位，和在线读取保持一致
                                    int base = c.getInt("Slot").orElse(idx);
                                    addOfflineStack(slots, base, c, 0);
                                    idx++;
                                }
                                String name = resolvedName != null ? resolvedName : "未命名假人";
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

    /**
     * 离线读档专用：按 NBT 结构递归统计单个物品及其内部容器（潜影盒/束袋等）。
     * 与在线逻辑保持一致：components.minecraft:container -> List<{slot, item}>，
     * 旧格式 components.minecraft:block_entity_data -> Items。
     */
    private static void addOfflineStack(List<FakeSlot> slots, int base, CompoundTag stack, int depth) {
        if (stack == null || depth > 4) {
            return;
        }
        String id = stack.getStringOr("id", "");
        if (id.isEmpty()) {
            return;
        }
        int count = stack.getIntOr("count", 1);
        if (count <= 0) {
            count = 1;
        }
        slots.add(new FakeSlot(base, id, count));

        CompoundTag comps = stack.getCompoundOrEmpty("components");
        if (comps == null) {
            return;
        }
        // 现代潜影盒：容器组件是 List<{slot, item}>
        if (comps.contains("minecraft:container")) {
            Tag container = comps.get("minecraft:container");
            if (container instanceof ListTag list) {
                int i = 0;
                for (Tag el : list) {
                    if (el instanceof CompoundTag entry) {
                        CompoundTag item = entry.getCompoundOrEmpty("item");
                        addOfflineStack(slots, base + 1000 + i, item, depth + 1);
                        i++;
                    }
                }
            }
        }
        // 旧式潜影盒：block_entity_data -> Items
        if (comps.contains("minecraft:block_entity_data")) {
            Tag bed = comps.get("minecraft:block_entity_data");
            if (bed instanceof CompoundTag bedComp) {
                ListTag items = bedComp.getListOrEmpty("Items");
                int i = 0;
                for (Tag el : items) {
                    if (el instanceof CompoundTag item) {
                        addOfflineStack(slots, base + 2000 + i, item, depth + 1);
                        i++;
                    }
                }
            }
        }
    }

    /** 从事件日志恢复 uuid -> 假人真名 映射。 */
    private static void loadKnownNames() {
        try {
            if (Files.exists(FAKE_FILE)) {
                Set<String> s = GSON.fromJson(Files.readString(FAKE_FILE, StandardCharsets.UTF_8), FAKE_TYPE);
                if (s != null) {
                    knownFakeUuids.addAll(s);
                }
            }
        } catch (Exception ignored) {
        }
        // 加载记录过的真实玩家
        try {
            if (Files.exists(REAL_FILE)) {
                Set<String> s = GSON.fromJson(Files.readString(REAL_FILE, StandardCharsets.UTF_8), FAKE_TYPE);
                if (s != null) {
                    knownRealUuids.addAll(s);
                }
            }
        } catch (Exception ignored) {
        }
        loadUsercache();
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
                                // 凡是 /player <名字> ... 出现过的，都当作召唤过的假人名字
                                spawnedBotNames.add(name);
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

    /** 读取 usercache.json，建立 uuid -> 名字 的映射（用于显示 v4 假人的真名）。 */
    private static void loadUsercache() {
        try {
            Path uc = FabricLoader.getInstance().getGameDir().resolve("usercache.json");
            if (!Files.exists(uc)) {
                return;
            }
            JsonArray arr = JsonParser.parseString(Files.readString(uc, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String u = o.has("uuid") ? o.get("uuid").getAsString() : null;
                String n = o.has("name") ? o.get("name").getAsString() : null;
                if (u != null && n != null && !u.isBlank() && !n.isBlank()) {
                    uuidNameCache.putIfAbsent(u, n);
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
