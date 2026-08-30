package com.elysia.fakeinspector;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.server.FakePlayerCollector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 游戏内命令 /fpi：查看假人状态、最近行为与下线存档位置。 */
public final class FakeInspectorCommand {

    private FakeInspectorCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("fpi")
                        .executes(FakeInspectorCommand::list)
                        .then(Commands.literal("list").executes(FakeInspectorCommand::list))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();

        Map<String, String[]> lastAction = new HashMap<>();
        try {
            Path eventFile = FabricLoader.getInstance().getConfigDir().resolve("fake-player-inspector-events.log");
            if (Files.exists(eventFile)) {
                for (String line : Files.readAllLines(eventFile, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 4) {
                        lastAction.put(parts[3], new String[]{parts[1], parts[0]});
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略
        }

        Set<String> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(p.getStringUUID());
        }

        Map<String, FakePlayerData> entries = FakePlayerCollector.entries();
        if (entries.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[FakePlayerInspector] 暂无假人数据"), false);
            return 1;
        }
        for (Map.Entry<String, FakePlayerData> e : entries.entrySet()) {
            String uuid = e.getKey();
            FakePlayerData data = e.getValue();
            String status = online.contains(uuid) ? "在线" : "离线/存档";
            String[] action = lastAction.get(uuid);
            String act = (action != null) ? (action[0] + "  " + action[1]) : "(无行为记录)";
            String line = "[" + data.name() + "]  " + uuid + "  " + status + "  最近: " + act
                    + "  存档: players/data/" + uuid + ".dat";
            src.sendSuccess(() -> Component.literal("[FakePlayerInspector] " + line), false);
        }
        return 1;
    }
}
