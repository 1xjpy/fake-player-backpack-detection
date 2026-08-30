package com.elysia.fakeinspector.server;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakeSlot;
import com.elysia.fakeinspector.util.FakePlayerDetector;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 服务端：收集所有地毯假人的名字与背包内容。 */
public final class FakePlayerCollector {

    private FakePlayerCollector() {
    }

    public static List<FakePlayerData> collect(MinecraftServer server) {
        List<FakePlayerData> result = new ArrayList<>();
        if (server == null) {
            return result;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!FakePlayerDetector.isFakePlayer(player)) {
                continue;
            }
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

            result.add(new FakePlayerData(player.getScoreboardName(), slots));
        }
        return result;
    }

    private static void addSlot(List<FakeSlot> slots, int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        slots.add(new FakeSlot(index, id, stack.getCount()));
    }
}
