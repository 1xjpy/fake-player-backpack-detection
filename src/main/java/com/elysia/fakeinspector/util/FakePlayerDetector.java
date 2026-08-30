package com.elysia.fakeinspector.util;

import net.minecraft.server.level.ServerPlayer;

/**
 * 通过类名反射识别「地毯模组假人」（carpet.patches.EntityPlayerMPFake）。
 * 不直接依赖地毯模组，避免硬编码版本而冲突。
 */
public final class FakePlayerDetector {

    private FakePlayerDetector() {
    }

    public static boolean isFakePlayer(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        String className = player.getClass().getName();
        return className.contains("EntityPlayerMPFake")
                || className.contains("PlayerMPFake")
                || className.contains("FakePlayer")
                || className.endsWith(".Fake");
    }
}
