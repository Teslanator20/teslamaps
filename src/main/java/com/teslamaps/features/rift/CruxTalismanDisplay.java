package com.teslamaps.features.rift;

// Ported from SkyHanni's CruxTalismanDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CruxTalismanDisplay {

    private static final Pattern PROGRESS = Pattern.compile("([IV1-4-]+)\\s+(\\w+):\\s*(MAXED|(\\d+)/(\\d+)).*");
    private static final List<String> SAMPLE = List.of("§7Crux Talisman Progress: §a62.5%",
            "  §e3 Shy: §e88§7/§a100", "  §e2 Shadow: §e42§7/§a50", "  §e4 Puff: §a§lMAXED");

    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;

    private CruxTalismanDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("cruxTalisman", "Crux Talisman").section("Rift")
                .enabled(() -> cfg().cruxTalismanDisplay)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("CruxTalismanDisplay").section("Rift").when(() -> cfg().cruxTalismanDisplay)
                .tick(CruxTalismanDisplay::tick)
                .hudHiddenInInventory("cruxtalisman", CruxTalismanDisplay::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Crux Talisman Progress", () -> c.cruxTalismanDisplay, v -> c.cruxTalismanDisplay = v)
                            .desc("HUD with the Crux Talisman progress in the Rift: total percent plus kills per crux. "
                                    + "Read from the talisman in your inventory. Move it in /tmap gui.")
                            .tags("crux", "talisman", "rift", "shy", "volt", "puff");
                    b.toggle("  Compact When Maxed", () -> c.cruxCompactWhenMaxed, v -> c.cruxCompactWhenMaxed = v);
                    b.toggle("  Show Total Bonuses", () -> c.cruxShowBonuses, v -> c.cruxShowBonuses = v);
                });
    }

    private static void tick() {
        if (++ticks % 40 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!RiftSpot.inRift()) {
            lines = List.of();
            return;
        }
        Inventory inv = mc.player.getInventory();
        List<String> cruxes = new ArrayList<>();
        List<String> bonuses = new ArrayList<>();
        int found = 0, sum = 0;
        boolean maxed = true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !ItemUtil.skyblockId(stack).startsWith("CRUX_TALISMAN")) continue;
            boolean bonus = false;
            for (String line : ItemUtil.loreLines(stack)) {
                Matcher m = PROGRESS.matcher(line.trim());
                if (m.matches()) {
                    found++;
                    String tier = m.group(1).replace("-", "0");
                    if (m.group(4) == null) {
                        sum += 100;
                        cruxes.add("  §e" + tier + " " + m.group(2) + ": §a§lMAXED");
                    } else {
                        maxed = false;
                        int have = Integer.parseInt(m.group(4)), need = Math.max(1, Integer.parseInt(m.group(5)));
                        sum += Math.min(100, have * 100 / need);
                        cruxes.add("  §e" + tier + " " + m.group(2) + ": §e" + have + "§7/§a" + need);
                    }
                    continue;
                }
                if (line.startsWith("Total Bonuses")) { bonus = true; continue; }
                if (bonus) {
                    if (line.isBlank()) bonus = false;
                    else bonuses.add("  §7" + line.trim());
                }
            }
            break;
        }
        if (found == 0) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        boolean compact = maxed && c.cruxCompactWhenMaxed;
        List<String> out = new ArrayList<>();
        double pct = Math.round(sum * 1000.0 / (found * 100)) / 10.0;
        out.add("§7Crux Talisman Progress: " + (compact ? "§a§lMAXED!" : "§a" + pct + "%"));
        if (!compact) out.addAll(cruxes);
        if (c.cruxShowBonuses && !bonuses.isEmpty()) {
            out.add("§7Bonuses:");
            out.addAll(bonuses);
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
