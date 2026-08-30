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
        if (className.contains("EntityPlayerMPFake")
                || className.contains("PlayerMPFake")
                || className.contains("FakePlayer")
                || className.toLowerCase().contains("fake")) {
            return true;
        }
        // 部分 carpet 系扩展的假人，connection 是伪造的（NetHandlerPlayServerFake）
        try {
            Object connection = player.connection;
            if (connection != null && connection.getClass().getName().contains("NetHandlerPlayServerFake")) {
                return true;
            }
        } catch (Exception ignored) {
            // 忽略反射异常
        }
        return false;
    }
}
