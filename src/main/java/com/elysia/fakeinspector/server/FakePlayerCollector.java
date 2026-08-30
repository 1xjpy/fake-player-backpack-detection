package com.elysia.fakeinspector.server;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakeSlot;
import com.elysia.fakeinspector.util.FakePlayerDetector;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ContainerComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端：收集所有地毯假人的名字与背包内容。
 * 使用快照缓存：只要某个假人出现过，就记下它的背包；
 * 之后即使它离线 / 暂不在玩家列表，也能查询到最近一次的背包。
 */
public final class FakePlayerCollector {

    private static final Map<String, FakePlayerData> CACHE = new ConcurrentHashMap<>();

    private FakePlayerCollector() {
    }

    public static List<FakePlayerData> collect(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!FakePlayerDetector.isFakePlayer(player)) {
                    continue;
                }
                FakePlayerData data = buildFrom(player);
                CACHE.put(data.name(), data);
            }
        }
        return new ArrayList<>(CACHE.values());
    }

    /** 从文件加载缓存（服务器启动时调用）。 */
    public static void load(List<FakePlayerData> data) {
        CACHE.clear();
        if (data != null) {
            for (FakePlayerData d : data) {
                CACHE.put(d.name(), d);
            }
        }
    }

    /** 获取当前缓存快照（用于写回文件 / 发送给客户端）。 */
    public static List<FakePlayerData> snapshot() {
        return new ArrayList<>(CACHE.values());
    }

    /** 判断两份数据是否一致（忽略顺序）。 */
    public static boolean sameData(List<FakePlayerData> a, List<FakePlayerData> b) {
        if (a == null || b == null) {
            return a == b;
        }
        java.util.List<FakePlayerData> sortedA =
                a.stream().sorted(Comparator.comparing(FakePlayerData::name)).toList();
        java.util.List<FakePlayerData> sortedB =
                b.stream().sorted(Comparator.comparing(FakePlayerData::name)).toList();
        return sortedA.equals(sortedB);
    }

    /** 清空缓存（服务器停止等场景可调用）。 */
    public static void clear() {
        CACHE.clear();
    }

    private static FakePlayerData buildFrom(ServerPlayer player) {
        List<FakeSlot> slots = new ArrayList<>();
        Inventory inv = player.getInventory();

        // 主背包 0-35
        NonNullList<ItemStack> main = inv.getNonEquipmentItems();
        for (int i = 0; i < main.size(); i++) {
            addSlot(slots, i, main.get(i));
        }
        // 护甲 36-39
        for (int i = 36; i <= 39; i++) {
            addSlot(slots, i, inv.getItem(i));
        }
        // 副手 40
        addSlot(slots, 40, inv.getItem(Inventory.SLOT_OFFHAND));

        return new FakePlayerData(player.getScoreboardName(), slots);
    }

    private static void addSlot(List<FakeSlot> slots, int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        slots.add(new FakeSlot(index, id, stack.getCount()));
        collectContainerContents(slots, index, stack, 0);
    }

    /** 递归统计容器（潜影盒/箱子等）内部的物品。 */
    private static void collectContainerContents(List<FakeSlot> slots, int base, ItemStack stack, int depth) {
        if (depth > 3) {
            return;
        }
        ContainerComponent container = stack.get(DataComponents.CONTAINER);
        if (container == null) {
            return;
        }
        int i = 0;
        for (ItemStack inner : container) {
            if (inner != null && !inner.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
                int slotIndex = base + 1000 + i;
                slots.add(new FakeSlot(slotIndex, id, inner.getCount()));
                collectContainerContents(slots, slotIndex, inner, depth + 1);
            }
            i++;
        }
    }
}
