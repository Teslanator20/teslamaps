package com.teslamaps.features.croesus;

// Ported from Devonian's CroesusChestCounter.kt (+ Odin's chestWarning threshold)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonInfoHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CroesusChestCounter {

    private static final int MAX = 60;
    private static final Pattern TAB = Pattern.compile("^Unclaimed chests: (\\d+)$");
    private static final Pattern TEAM_SCORE = Pattern.compile("^Team Score: \\d+ \\(.{1,2}\\)(?: \\(NEW RECORD!\\))?$");
    private static final String WIPE = "Warning! The instance will close in 10s.";
    private static final List<String> SAMPLE = List.of("§6Croesus: §a5§f/§460");

    private static boolean added = false;
    private static boolean alerted = false;
    private static boolean visible = false;
    private static List<String> line = List.of();
    private static int shownCount = Integer.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static int counter = 0;
    private static HudEditElement el;

    private CroesusChestCounter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("croesusChestCounter", "Croesus Chests")
                .enabled(() -> cfg().croesusChestCounter)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE.get(0)), () -> 10)
                .preview((g, mc, x, y, s) -> DungeonInfoHud.draw(g, mc, x, y, s, SAMPLE, mc.font.width(SAMPLE.get(0))));
        Feature.of("CroesusChestCounter").when(() -> cfg().croesusChestCounter)
                .tick(CroesusChestCounter::tick)
                .serverChat(CroesusChestCounter::onChat)
                .hud("croesuschestcounter", CroesusChestCounter::render)
                .config("Croesus", b -> {
                    b.label("Croesus Chest Counter");
                    b.toggle("Unclaimed Chest Counter (n/60)", () -> cfg().croesusChestCounter, v -> cfg().croesusChestCounter = v)
                            .desc("HUD in the Dungeon Hub and in runs; count comes from the tab, +1 per finished run");
                    b.toggle("  Warn Near Limit", () -> cfg().croesusChestWarn, v -> cfg().croesusChestWarn = v);
                    b.slider("    Warn At", 30f, 60f, () -> (float) cfg().croesusChestWarnAt,
                            v -> cfg().croesusChestWarnAt = Math.round(v), 0);
                    b.toggle("    Warning Sound", () -> cfg().croesusChestWarnSound, v -> cfg().croesusChestWarnSound = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            added = false;
        }
        if (++counter % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        visible = DungeonManager.isInDungeon() || SkyblockUtils.getArea().equalsIgnoreCase("Dungeon Hub");
        if (visible) {
            for (String raw : TabListUtils.getTabListLines()) {
                String s = StringUtil.stripTrim(raw);
                if (!s.startsWith("Unclaimed chests")) continue;
                Matcher m = TAB.matcher(s);
                if (m.matches()) setCount(Math.min(MAX, Integer.parseInt(m.group(1))));
                break;
            }
        }
        int n = c.croesusChestCount;
        if (c.croesusChestWarn && n >= 0 && n >= c.croesusChestWarnAt && !alerted && mc.player != null) {
            alerted = true;
            mc.gui.hud.setTimes(0, 50, 10);
            mc.gui.hud.setTitle(Component.literal(n >= MAX ? "§cCroesus Chests Full!" : "§6Croesus: " + n + "/" + MAX + " Chests"));
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §6" + n + "/" + MAX
                    + " unclaimed Croesus chests §7— claim some, new loot is lost at 60."));
            if (c.croesusChestWarnSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.6f);
        }
        if (n != shownCount) {
            shownCount = n;
            String col = n >= MAX * 0.75 ? "§4" : n >= MAX * 0.5 ? "§c" : n >= MAX * 0.25 ? "§e" : "§a";
            line = List.of("§6Croesus: " + col + (n < 0 ? "?" : String.valueOf(n)) + "§f/§4" + MAX);
        }
    }

    private static void setCount(int n) {
        TeslaMapsConfig c = cfg();
        if (n < c.croesusChestWarnAt) alerted = false;
        if (n == c.croesusChestCount) return;
        c.croesusChestCount = n;
        TeslaMapsConfig.save();
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String s = StringUtil.stripTrim(raw);
        TeslaMapsConfig c = cfg();
        if (added && s.equals(WIPE)) {
            added = false;
            if (c.croesusChestCount > 0) setCount(c.croesusChestCount - 1);
            return;
        }
        if (added || !s.startsWith("Team Score:") || !TEAM_SCORE.matcher(s).matches()) return;
        added = true;
        if (c.croesusChestCount >= 0) setCount(Math.min(MAX, c.croesusChestCount + 1));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!visible || el == null || line.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        DungeonInfoHud.draw(g, mc, el.x(), el.y(), el.scale(), line, mc.font.width(line.get(0)));
    }
}
