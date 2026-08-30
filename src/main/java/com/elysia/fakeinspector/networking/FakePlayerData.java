package com.elysia.fakeinspector.networking;

import java.util.List;

/** 一个假人的数据：名字 + 背包内容。 */
public record FakePlayerData(String name, List<FakeSlot> slots) {
}
