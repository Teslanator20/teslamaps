package com.teslamaps.features.fishing;

// After SkyHanni's fishing/SharkFishCounter.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class SharkCounter {

    private static final String[] NAMES = {"Nurse Shark", "Blue Shark", "Tiger Shark", "Great White Shark"};
    private static final String[] COLORS = {"§a", "§9", "§5", "§6"};
    private static final String END = "FISHING FESTIVAL The festival has concluded! Time to dry off and repair your rods!";
    private static final long DOUBLE_HOOK_MS = 2000;
    private static final List<String> SAMPLE = List.of("§7Sharks caught: §e42 §7(§a20 §915 §55 §62§7)");

    private static final int[] counts = new int[4];
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static long doubleHookAt;
    private static boolean holding;

    private SharkCounter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("sharkCounter", "Shark Counter").section("Fishing")
                .enabled(() -> cfg().sharkCounter)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("SharkCounter").section("Fishing").when(() -> cfg().sharkCounter)
                .tick(SharkCounter::tick)
                .serverChat(SharkCounter::onChat)
                .hudHiddenInInventory("sharkcounter", SharkCounter::render)
                .config("Fishing", b -> b.toggle("Shark Counter", () -> cfg().sharkCounter, v -> cfg().sharkCounter = v)
                        .desc("Fishing Festival: sharks caught per type while holding a water rod, summary in chat when the festival ends (/tmap gui)."));
    }

    private static int total() {
        return counts[0] + counts[1] + counts[2] + counts[3];
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("It's a Double Hook!") || t.equals("Double Hook!")) {
            doubleHookAt = System.currentTimeMillis();
            return;
        }
        if (t.equals(END)) {
            summary();
            return;
        }
        String name = SeaCreatureList.nameFor(t);
        if (name == null || !name.contains("Shark")) return;
        for (int i = 0; i < NAMES.length; i++) {
            if (!name.contains(NAMES[i])) continue;
            boolean dh = System.currentTimeMillis() - doubleHookAt < DOUBLE_HOOK_MS;
            doubleHookAt = 0;
            counts[i] += dh ? 2 : 1;
            rebuild();
            return;
        }
    }

    private static void rebuild() {
        StringBuilder sb = new StringBuilder("§7Sharks caught: §e").append(total()).append(" §7(");
        for (int i = 0; i < 4; i++) sb.append(i > 0 ? " " : "").append(COLORS[i]).append(counts[i]);
        lines = List.of(sb.append("§7)").toString());
        width = TextHud.width(lines);
    }

    private static void summary() {
        int n = total();
        if (n == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String comment = n < 50 ? "Well done!" : n < 100 ? "Nice!" : n < 150 ? "Really nice!" : n < 200 ? "Super cool!"
                    : n < 250 ? "Mega cool!" : n < 350 ? "Like a pro!" : "How???";
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7You caught §e" + n + " §f(§a" + counts[0] + " §9" + counts[1]
                    + " §5" + counts[2] + " §6" + counts[3] + "§f) §7sharks during this fishing festival. §e" + comment));
        }
        java.util.Arrays.fill(counts, 0);
        lines = List.of();
    }

    private static void tick() {
        if (++timer % 10 == 0) holding = FishingHelpers.holdingRod() && !FishingHelpers.holdingLavaRod();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !holding || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
