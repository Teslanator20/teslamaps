package com.teslamaps.features;

// Ported from SkyHanni's BroodmotherFeatures.kt / GanKura's BroodmotherHandler, Bal alert after SkyblockAddons' BAL_BOSS_ALERT
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

import java.util.List;
import java.util.Locale;

public final class BroodmotherTracker {

    private enum Stage {
        SLAIN("§eSlain", 10), DORMANT("§eDormant", 9), SOON("§6Soon", 6), AWAKENING("§6Awakening", 3),
        IMMINENT("§4Imminent", 1), ALIVE("§4Alive!", 0);

        final String label;
        final int minutes;

        Stage(String label, int minutes) { this.label = label; this.minutes = minutes; }

        static Stage of(String s) {
            try {
                return valueOf(s.replace("!", "").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final String SAMPLE = "§4Broodmother spawning in §b4m 12s";

    private static Stage stage, last;
    private static long spawnAt;
    private static List<String> lines = List.of();
    private static int lineW;
    private static Object lastLevel;
    private static int ticks;
    private static final IntOpenHashSet balSeen = new IntOpenHashSet();
    private static HudEditElement hud;

    private BroodmotherTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("broodmother", "Broodmother Countdown").section("Combat")
                .enabled(() -> cfg().broodmotherTracker && cfg().broodmotherHud)
                .size(() -> Math.max(lineW, Minecraft.getInstance().font.width(SAMPLE) + 8), () -> 16)
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, List.of(SAMPLE), mc.font.width(SAMPLE) + 8));
        Feature.of("BroodmotherTracker").section("Combat").when(() -> cfg().broodmotherTracker)
                .tick(BroodmotherTracker::tick)
                .hudHiddenInInventory("broodmother", BroodmotherTracker::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Broodmother");
                    b.toggle("Broodmother Tracker", () -> c.broodmotherTracker, v -> c.broodmotherTracker = v)
                            .desc("Spider's Den: reads the Broodmother tab widget (enable it in /tab)")
                            .tags("broodmother", "spider", "nest");
                    b.toggle("  Countdown HUD", () -> c.broodmotherHud, v -> c.broodmotherHud = v)
                            .desc("Time until it spawns, estimated from the stage. Position in /tmap gui");
                    b.toggle("  Stage Changes in Chat", () -> c.broodmotherChat, v -> c.broodmotherChat = v)
                            .desc("\"Broodmother: Dormant -> Soon. 6 minutes until it spawns!\"");
                    b.toggle("  Imminent Warning", () -> c.broodmotherImminent, v -> c.broodmotherImminent = v)
                            .desc("Sound + chat when it turns Imminent (60 s left)");
                    b.toggle("  Spawn Alert", () -> c.broodmotherSpawnAlert, v -> c.broodmotherSpawnAlert = v)
                            .desc("Title + sound on spawn, plus a chat line you can click to /warp nest");
                    b.toggle("  Bal Alert (Crystal Hollows)", () -> c.balAlert, v -> c.balAlert = v)
                            .desc("Title + sound when Bal (big Magma Cube) shows up in the Crystal Hollows")
                            .tags("bal", "crystal hollows", "magma");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            stage = last = null;
            spawnAt = 0;
            lines = List.of();
            balSeen.clear();
        }
        TeslaMapsConfig c = cfg();
        if (++ticks % 10 == 0) {
            if (c.balAlert && SkyblockUtils.isInArea("Crystal Hollows")) scanBal(mc);
            if (SkyblockUtils.isInArea("Spider's Den")) readTab(mc, c);
            else stage = null;
        }
        if (ticks % 5 != 0) return;
        String line = buildLine();
        lineW = line == null ? 0 : mc.font.width(line) + 8;
        lines = line == null ? List.of() : List.of(line);
    }

    private static void readTab(Minecraft mc, TeslaMapsConfig c) {
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            if (!l.startsWith("Broodmother:")) continue;
            Stage s = Stage.of(l.substring(12).trim().split(" ")[0]);
            if (s != null && s != stage) onStage(mc, c, s);
            return;
        }
    }

    private static void onStage(Minecraft mc, TeslaMapsConfig c, Stage s) {
        last = stage;
        stage = s;
        if (last == null) {
            spawnAt = s == Stage.ALIVE ? 0 : System.currentTimeMillis() + s.minutes * 60_000L;
            return;
        }
        if (s == Stage.IMMINENT && last == Stage.ALIVE) return;
        if (s == Stage.ALIVE) {
            spawnAt = 0;
            if (c.broodmotherSpawnAlert) {
                mc.gui.hud.setTimes(5, 50, 10);
                mc.gui.hud.setTitle(Component.literal("BROODMOTHER SPAWNED").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                mc.gui.hud.setSubtitle(Component.empty());
                LoudSound.play(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.0f);
                mc.player.sendSystemMessage(Component.literal("§eThe Broodmother has spawned! §b[Click to warp to the Top of the Nest]")
                        .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/warp nest"))));
            }
            return;
        }
        spawnAt = System.currentTimeMillis() + s.minutes * 60_000L;
        if (s == Stage.IMMINENT && c.broodmotherImminent) {
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
            mc.player.sendSystemMessage(Component.literal("§eThe Broodmother is §4Imminent§e! It will spawn in §b60 seconds§e!"));
            return;
        }
        if (!c.broodmotherChat) return;
        if (s == Stage.SLAIN) {
            mc.player.sendSystemMessage(Component.literal("§eThe Broodmother was killed!"));
        } else {
            mc.player.sendSystemMessage(Component.literal("§eBroodmother: " + last.label + " §e-> " + s.label + "§e. §b"
                    + s.minutes + (s.minutes == 1 ? " minute" : " minutes") + " §euntil it spawns!"));
        }
    }

    private static String buildLine() {
        if (stage == null) return null;
        if (stage == Stage.ALIVE) return "§4Broodmother spawned!";
        long left = spawnAt - System.currentTimeMillis();
        if (left <= 0) return "§4Broodmother spawning now!";
        long secs = left / 1000;
        return "§4Broodmother spawning in §b" + (secs >= 60 ? secs / 60 + "m " : "") + secs % 60 + "s";
    }

    private static void scanBal(Minecraft mc) {
        for (MagmaCube m : mc.level.getEntitiesOfClass(MagmaCube.class, mc.player.getBoundingBox().inflate(64))) {
            if (m.getSize() <= 10 || !balSeen.add(m.getId())) continue;
            mc.gui.hud.setTimes(5, 50, 10);
            mc.gui.hud.setTitle(Component.literal("BAL SPAWNED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            mc.gui.hud.setSubtitle(Component.empty());
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null || !cfg().broodmotherHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        CropStats.drawLines(g, mc, hud.x(), hud.y(), hud.scale(), l, lineW);
    }
}
