package com.teslamaps.features;

// Ported from SkyHanni's NewYearCakeReminder.kt and ReindrakeWarpHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.regex.Pattern;

public final class WinterReminders {

    private static final Pattern REINDRAKE = Pattern.compile("^WOAH! .+ summoned (?:a Reindrake|TWO Reindrakes) from the depths!$");
    private static final long SB_EPOCH = 1560275700000L, YEAR_MS = 446_400_000L;

    private static int ticks;
    private static long lastReminder;

    private WinterReminders() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NewYearCakeReminder").section("Events").when(() -> cfg().newYearCakeReminder)
                .tick(WinterReminders::tick)
                .serverChat(WinterReminders::onCakeChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("New Year Celebration");
                    b.toggle("New Year Cake Reminder", () -> c.newYearCakeReminder, v -> c.newYearCakeReminder = v)
                            .desc("During the New Year event: chat reminder every 30s until you claimed the free cake. Click it to open the baker.");
                });
        Feature.of("ReindrakeWarpHelper").section("Fishing").when(() -> cfg().reindrakeWarpHelper)
                .serverChat(WinterReminders::onReindrakeChat)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Reindrake");
                    b.toggle("Reindrake Warp Helper", () -> c.reindrakeWarpHelper, v -> c.reindrakeWarpHelper = v)
                            .desc("Jerry's Workshop: when a Reindrake spawns, a clickable chat message warps you to the island spawn.");
                });
    }

    private static int year() {
        return (int) ((System.currentTimeMillis() - SB_EPOCH) / YEAR_MS) + 1;
    }

    private static void markClaimed() {
        if (cfg().newYearCakeClaimedYear == year()) return;
        cfg().newYearCakeClaimedYear = year();
        TeslaMapsConfig.save();
    }

    private static void onCakeChat(String text) {
        if (StringUtil.stripTrim(text).equals("You claimed a New Year Cake!")) markClaimed();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs && StringUtil.stripTrim(cs.getTitle().getString()).equals("Baker")) {
            markClaimed();
            return;
        }
        if (++ticks % 20 != 0) return;
        if (cfg().newYearCakeClaimedYear == year() || !SkyblockUtils.isInSkyblock()) return;
        if (System.currentTimeMillis() - lastReminder < 30_000L || busy() || !cakeTime()) return;
        lastReminder = System.currentTimeMillis();
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eGrab your free New Year Cake! §7Click to open the baker menu.")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/openbaker"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /openbaker")))));
    }

    private static boolean busy() {
        if (DungeonManager.isInDungeon()) return true;
        String a = SkyblockUtils.getArea();
        return a.equalsIgnoreCase("Kuudra") || a.equalsIgnoreCase("The Rift") || a.equalsIgnoreCase("Instanced");
    }

    private static boolean cakeTime() {
        for (String l : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(l).startsWith("New Year Event!")) return true;
        }
        return false;
    }

    private static void onReindrakeChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("WOAH!") || !REINDRAKE.matcher(t).matches()) return;
        String a = SkyblockUtils.getArea();
        if (!a.isEmpty() && !a.contains("Jerry")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cA Reindrake spawned! §eClick to warp to the Jerry's Workshop spawn.")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/warp winter"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /warp winter")))));
    }
}
