package com.teslamaps.features;

// Ported from SkyHanni's Calculator (/shcalc) and SkillApi (/shskills xpforlevel|levelwithxp)
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.Calculator;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ChatCalculator {
    private static final Deque<String> HISTORY = new ArrayDeque<>();

    private ChatCalculator() {}

    public static void register() {
        Feature.of("ChatCalculator")
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Calculator");
                    b.toggle("/calc Command", () -> c.calcCommand, v -> c.calcCommand = v)
                            .desc("/calc 12.5m*3/64 in chat, /calc alone = history, /calc xpforlevel <lvl>, /calc levelwithxp <xp>. Also /tmap calc.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(tree("calc", true));
            d.register(ClientCommands.literal("tmap").then(tree("calc", false)));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name, boolean forward) {
        return ClientCommands.literal(name)
                .executes(c -> {
                    if (off(forward, name)) return 1;
                    if (HISTORY.isEmpty()) msg("§7No calculations yet. §f/" + name + " <expression>");
                    else for (String h : HISTORY) msg(h);
                    return 1;
                })
                .then(ClientCommands.literal("xpforlevel")
                        .then(ClientCommands.argument("level", IntegerArgumentType.integer(0, 60)).executes(c -> {
                            if (off(forward, name + " xpforlevel " + IntegerArgumentType.getInteger(c, "level"))) return 1;
                            int lvl = IntegerArgumentType.getInteger(c, "level");
                            msg("§7Level §e" + lvl + " §7needs §a" + Calculator.format(SkillData.xpForLevel(lvl)) + " §7total skill XP");
                            return 1;
                        })))
                .then(ClientCommands.literal("levelwithxp")
                        .then(ClientCommands.argument("xp", StringArgumentType.greedyString()).executes(c -> {
                            String raw = StringArgumentType.getString(c, "xp");
                            if (off(forward, name + " levelwithxp " + raw)) return 1;
                            Double xp = Calculator.eval(raw);
                            if (xp == null || xp < 0) { msg("§cInvalid XP: " + raw); return 1; }
                            SkillData s = new SkillData("", xp, "skill");
                            String lvl = s.hasOverflow() ? String.valueOf(s.getOverflowLevel()) : String.valueOf(s.getLevel());
                            msg("§a" + Calculator.format(xp) + " §7XP = level §e" + lvl
                                    + (s.getXpToNext() > 0 ? " §7(" + Calculator.format(Math.ceil(s.getXpToNext() - s.getProgressXp())) + " to next)" : ""));
                            return 1;
                        })))
                .then(ClientCommands.argument("expression", StringArgumentType.greedyString()).executes(c -> {
                    String expr = StringArgumentType.getString(c, "expression");
                    if (off(forward, name + " " + expr)) return 1;
                    Double v = Calculator.eval(expr);
                    if (v == null) { msg("§c" + expr + " = ?"); return 1; }
                    String result = Calculator.format(v);
                    String line = "§7" + expr + " §f= §a" + result;
                    HISTORY.addFirst(line);
                    while (HISTORY.size() > 10) HISTORY.removeLast();
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + line)
                            .withStyle(s -> s.withClickEvent(new ClickEvent.CopyToClipboard(result.replace(",", "")))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy")))));
                    return 1;
                }));
    }

    // /calc while off goes to the server as typed
    private static boolean off(boolean forward, String line) {
        if (TeslaMapsConfig.get().calcCommand) return false;
        Minecraft mc = Minecraft.getInstance();
        if (forward) {
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChatCommandPacket(line));
        } else {
            msg("§7Enable §f/calc Command §7in Chat settings.");
        }
        return true;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + s));
    }
}
