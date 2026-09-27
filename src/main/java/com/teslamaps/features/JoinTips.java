package com.teslamaps.features;

// Idea from Skyblocker's Tips
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JoinTips {

    private JoinTips() {}

    // text, command (suggested on click, null = none)
    private static final String[][] TIPS = {
            {"Drag and scale every HUD panel with /tmap gui. TAB shows all of them.", "/tmap gui"},
            {"Search every container you ever opened with /tmap search.", "/tmap search"},
            {"Bind chat messages and commands to keys in /tmap hotkeys.", "/tmap hotkeys"},
            {"Short aliases for long commands live in /tmap shortcut.", "/tmap shortcut"},
            {"Record and follow waypoint routes with /tmap route.", "/tmap route"},
            {"The Greenhouse planner reports what is left to do with /tmap greenhouse todo.", "/tmap greenhouse todo"},
            {"Mark player heads for Head ESP with /tmap head add <name>.", "/tmap head add "},
            {"Rename and recolor your items with /tmap customize.", "/tmap customize"},
            {"Browse, edit and share your screenshots in /tmap screenshots.", "/tmap screenshots"},
            {"Pick your cape in /tmap cape.", "/tmap cape"},
            {"Critter Safari history and stats: /tmap safari gui.", "/tmap safari gui"},
            {"Frame drops? /tmap perf shows what the mod spends its time on.", "/tmap perf"},
            {"See the current mayor and perks with /tmap mayor.", "/tmap mayor"},
            {"Your unlocked achievements: /tmap achievements.", "/tmap achievements"},
            {"Switch between config presets with /tmap profile.", "/tmap profile"},
            {"The config search box finds every option by name or keyword.", "/tmap"},
            {"Right Shift opens the config from anywhere (change it under Interface).", null},
            {"Trackers keep session and all-time counts: /tmap tracker <id> view.", "/tmap tracker "},
    };

    private static final List<Integer> ORDER = new ArrayList<>();
    private static int index = -1;
    private static boolean sent, wasInSkyblock;
    private static int waitTicks;

    private static final Feature FEATURE = Feature.of("JoinTips").when(() -> TeslaMapsConfig.get().joinTips);

    public static void register() {
        for (int i = 0; i < TIPS.length; i++) ORDER.add(i);
        Collections.shuffle(ORDER);
        FEATURE.tick(JoinTips::tick)
                .config("QoL & Misc", b -> {
                    b.label("Tips");
                    b.toggle("Tip On SkyBlock Join", () -> TeslaMapsConfig.get().joinTips, v -> TeslaMapsConfig.get().joinTips = v)
                            .desc("Once per session a random feature tip with a clickable command, plus [Previous] [Next] [Disable]")
                            .tags("tips", "join", "help");
                });
    }

    private static int tickCount;

    private static void tick() {
        if (sent || ++tickCount % 20 != 0) return;
        boolean in = SkyblockUtils.isInSkyblock();
        if (in && !wasInSkyblock && !sent) waitTicks = 3;
        wasInSkyblock = in;
        if (waitTicks > 0 && --waitTicks == 0 && in && !sent) {
            sent = true;
            show(1);
        }
    }

    private static void show(int step) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        index = Math.floorMod(index + step, TIPS.length);
        mc.player.sendSystemMessage(message(TIPS[ORDER.get(index)]));
    }

    private static Component message(String[] tip) {
        MutableComponent line = Component.literal("§b[TeslaMaps] §eTip: ");
        MutableComponent text = Component.literal("§f" + tip[0]);
        if (tip[1] != null) text = text.withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand(tip[1]))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to type §e" + tip[1]))));
        line.append(text).append(" ");
        line.append(button("§7[Previous]", "/tmap tips previous")).append(" ");
        line.append(button("§a[Next]", "/tmap tips next")).append(" ");
        line.append(button("§c[Disable]", "/tmap tips disable"));
        return line;
    }

    private static Component button(String label, String cmd) {
        return Component.literal(label).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(cmd)));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("tips")
                .executes(c -> { show(1); return 1; })
                .then(ClientCommands.literal("next").executes(c -> { show(1); return 1; }))
                .then(ClientCommands.literal("previous").executes(c -> { show(-1); return 1; }))
                .then(ClientCommands.literal("enable").executes(c -> toggle(c.getSource(), true)))
                .then(ClientCommands.literal("disable").executes(c -> toggle(c.getSource(), false)));
    }

    private static int toggle(FabricClientCommandSource src, boolean on) {
        TeslaMapsConfig.get().joinTips = on;
        TeslaMapsConfig.save();
        src.sendFeedback(Component.literal("§b[TeslaMaps] §fJoin tips " + (on ? "§aenabled" : "§cdisabled") + " ")
                .append(button(on ? "§7[Disable]" : "§7[Enable]", on ? "/tmap tips disable" : "/tmap tips enable")));
        return 1;
    }
}
