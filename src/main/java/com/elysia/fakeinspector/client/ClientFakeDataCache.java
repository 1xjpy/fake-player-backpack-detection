package com.elysia.fakeinspector.client;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakeSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 客户端缓存：保存服务端返回的假人背包数据，供 tooltip 查询。 */
public final class ClientFakeDataCache {

    private static volatile List<FakePlayerData> data = List.of();
    private static volatile boolean showHolders = false;

    private ClientFakeDataCache() {
    }

    public static void init() {
        data = List.of();
    }

    public static void set(List<FakePlayerData> newData) {
        data = newData == null ? List.of() : List.copyOf(newData);
    }

    /** 是否在物品 tooltip 上显示假人持有信息（默认关闭，需要手动开开关）。 */
    public static boolean isShowHolders() {
        return showHolders;
    }

    public static void setShowHolders(boolean value) {
        showHolders = value;
    }

    public static boolean toggleShowHolders() {
        showHolders = !showHolders;
        return showHolders;
    }

    public static List<FakePlayerData> get() {
        return data;
    }

    public static List<Holder> holdersFor(ItemStack stack) {
        List<Holder> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return out;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (FakePlayerData p : data) {
            int total = 0;
            for (FakeSlot s : p.slots()) {
                if (s.itemId().equals(id)) {
                    total += s.count();
                }
            }
            if (total > 0) {
                out.add(new Holder(p.name(), total));
            }
        }
        return out;
    }

    public record Holder(String name, int count) {
    }
}
