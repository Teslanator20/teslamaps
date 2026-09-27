package com.teslamaps.features.hud;

// Zen VanilaHPHUD.kt (concept), SkyHanni features/misc/InWaterDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.List;

public final class SmallHuds {

    private static final List<String> HP_SAMPLE = List.of("§c20 ❤");
    private static final List<String> WATER_SAMPLE = List.of("§7In Water: §aTrue");
    private static final List<String> WATER_TRUE = List.of("§7In Water: §aTrue");
    private static final List<String> WATER_FALSE = List.of("§7In Water: §cFalse");

    private static HudEditElement hpHud, waterHud;
    private static List<String> hp = List.of(), water = List.of();
    private static int hpWidth, waterWidth, timer;
    private static float lastHp = -1;
    private static boolean stranded;

    private SmallHuds() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hpHud = HudEditElement.of("vanillaHp", "Vanilla HP")
                .enabled(() -> cfg().vanillaHpHud)
                .size(() -> TextHud.width(HP_SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, HP_SAMPLE, TextHud.width(HP_SAMPLE)));
        Feature.of("VanillaHpHud").when(() -> cfg().vanillaHpHud)
                .tick(SmallHuds::tickHp)
                .hudHiddenInInventory("vanillahp", (g, d) -> draw(g, hpHud, hp, hpWidth))
                .config("HUD Widgets", b -> {
                    b.label("Vanilla HP");
                    b.toggle("Vanilla HP Number", () -> cfg().vanillaHpHud, v -> cfg().vanillaHpHud = v)
                            .desc("Your vanilla hearts as a number, e.g. 20 ❤. Handy in the Rift. Position in /tmap gui.")
                            .tags("health", "hearts", "rift");
                });

        waterHud = HudEditElement.of("inWater", "In Water")
                .enabled(() -> cfg().inWaterDisplay)
                .size(() -> TextHud.width(WATER_FALSE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, WATER_SAMPLE, TextHud.width(WATER_SAMPLE)));
        Feature.of("InWaterDisplay").when(() -> cfg().inWaterDisplay)
                .tick(SmallHuds::tickWater)
                .hudHiddenInInventory("inwater", (g, d) -> draw(g, waterHud, water, waterWidth))
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("In Water");
                    b.toggle("In Water Display", () -> c.inWaterDisplay, v -> c.inWaterDisplay = v)
                            .desc("Shows In Water: True/False, for Stranded. Position in /tmap gui.")
                            .tags("stranded");
                    b.toggle("  Only On Stranded", () -> c.inWaterStrandedOnly, v -> c.inWaterStrandedOnly = v);
                });
    }

    private static void tickHp() {
        Player p = Minecraft.getInstance().player;
        float h = p.getHealth();
        if (h == lastHp && !hp.isEmpty()) return;
        lastHp = h;
        int r = Math.round(h * 10);
        hp = List.of("§c" + (r % 10 == 0 ? String.valueOf(r / 10) : (r / 10) + "." + (r % 10)) + " ❤");
        hpWidth = TextHud.width(hp);
    }

    private static void tickWater() {
        Minecraft mc = Minecraft.getInstance();
        if (++timer % 20 == 1) stranded = strandedProfile(mc);
        if (!SkyblockUtils.isInSkyblock() || (cfg().inWaterStrandedOnly && !stranded)) {
            water = List.of();
            return;
        }
        water = mc.player.isInWater() ? WATER_TRUE : WATER_FALSE;
        if (waterWidth == 0) waterWidth = TextHud.width(WATER_FALSE);
    }

    // SkyHanni HypixelData.checkSpecialModes: ☀ in the sidebar title, or the " ☀ Stranded" line
    private static boolean strandedProfile(Minecraft mc) {
        Objective obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return false;
        String title = obj.getDisplayName().getString();
        if (title.contains("GUEST")) return stranded;
        if (title.contains("☀")) return true;
        for (String line : com.teslamaps.utils.ScoreboardUtils.getScoreboardLines()) {
            if (StringUtil.strip(line).trim().equals("☀ Stranded")) return true;
        }
        return false;
    }

    private static void draw(GuiGraphicsExtractor g, HudEditElement e, List<String> lines, int w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || e == null) return;
        TextHud.draw(g, mc, e.x(), e.y(), e.scale(), lines, w);
    }
}
