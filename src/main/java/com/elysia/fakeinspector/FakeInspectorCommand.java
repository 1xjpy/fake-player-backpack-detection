package com.elysia.fakeinspector;

import com.elysia.fakeinspector.networking.FakePlayerData;
import com.elysia.fakeinspector.networking.FakePlayerDisplayPayload;
import com.elysia.fakeinspector.networking.FakeSlot;
import com.elysia.fakeinspector.server.FakePlayerCollector;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("fpi")
                    .executes(FakeInspectorCommand::lokpkkHelp)
                    .then(Commands.literal("list").executes(FakeInspectorCommand::list))
                    .then(Commands.literal("auto")
                            .then(Commands.argument("state", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean enabled = BoolArgumentType.getBool(ctx, "state");
                                        FakeInspectorMod.setAutoRead(enabled);
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "[FakePlayerInspector] 后台读取已" + (enabled ? "开启" : "关闭")), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("display")
                            .then(Commands.argument("state", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean enabled = BoolArgumentType.getBool(ctx, "state");
                                        ServerPlayer player = ctx.getSource().getPlayer();
                                        if (player != null) {
                                            ServerPlayNetworking.send(player, new FakePlayerDisplayPayload(enabled));
                                        }
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "[FakePlayerInspector] 悬停显示假人背包已" + (enabled ? "开启" : "关闭")), false);
                                        return 1;
                                    })))
                    .then(Commands.argument("target", StringArgumentType.word())
                            .executes(ctx -> backpack(ctx, StringArgumentType.getString(ctx, "target")))));
        });
    }

    private static int lokpkkHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean auto = FakeInspectorMod.isAutoRead();
        src.sendSuccess(() -> Component.literal("[FakePlayerInspector] 假人背包查看器"), false);
        src.sendSuccess(() -> Component.literal("  当前后台读取: " + (auto ? "开启" : "关闭")), false);
        src.sendSuccess(() -> Component.literal("  可用指令："), false);
        src.sendSuccess(() -> Component.literal("    /fpi                    查看假人列表"), false);
        src.sendSuccess(() -> Component.literal("    /fpi <假人名>           查看某假人背包"), false);
        src.sendSuccess(() -> Component.literal("    /fpi auto true/false    后台读取开关"), false);
        src.sendSuccess(() -> Component.literal("    /fpi display true/false 悬停显示假人背包开关"), false);
        return 1;
    }

    private static int backpack(CommandContext<CommandSourceStack> ctx, String target) {
        CommandSourceStack src = ctx.getSource();
        Map<String, FakePlayerData> entries = FakePlayerCollector.entries();
        for (Map.Entry<String, FakePlayerData> e : entries.entrySet()) {
            FakePlayerData data = e.getValue();
            String uuid = e.getKey();
            boolean match = data.name().equalsIgnoreCase(target)
                    || uuid.equalsIgnoreCase(target)
                    || uuid.startsWith(target)
                    || data.name().contains(target);
            if (match) {
                src.sendSuccess(() -> Component.literal("[FakePlayerInspector] " + data.name() + " 的背包："), false);
                List<FakeSlot> slots = data.slots();
                if (slots.isEmpty()) {
                    src.sendSuccess(() -> Component.literal("  （空背包）"), false);
                } else {
                    for (FakeSlot s : slots) {
                        src.sendSuccess(() -> Component.literal("  " + s.itemId() + " x" + s.count()), false);
                    }
                }
                return 1;
            }
        }
        src.sendSuccess(() -> Component.literal("[FakePlayerInspector] 未找到假人: " + target), false);
        return 1;
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
