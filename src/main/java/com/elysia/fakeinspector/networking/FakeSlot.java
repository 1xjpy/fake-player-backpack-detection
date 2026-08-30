package com.elysia.fakeinspector.networking;

/** 背包中的一个格子：槽位、物品注册名、数量。 */
public record FakeSlot(int slot, String itemId, int count) {
}
