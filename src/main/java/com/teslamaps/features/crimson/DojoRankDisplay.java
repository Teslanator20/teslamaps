package com.teslamaps.features.crimson;

// Ported from SkyHanni's inventory/DojoRankDisplay.kt, belts from SkyHanni-REPO Belts.json
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DojoRankDisplay {

    private static final Pattern TEST = Pattern.compile("Test of (.+)");
    private static final Pattern RANK = Pattern.compile("Your Rank: (\\S+) \\((\\d+)\\)");
    private static final String[] BELT_NAMES = {"§fWhite Belt", "§eYellow Belt", "§aGreen Belt", "§9Blue Belt", "§6Brown Belt", "§8Black Belt"};
    private static final int[] BELT_POINTS = {0, 1000, 2000, 4000, 6000, 7000};

    private static Feature feature;
    private static List<String> panel = List.of();
    private static long panelAt;

    private DojoRankDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("DojoRankDisplay").section("Crimson Isle").when(() -> cfg().dojoRankDisplay)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Dojo Rank Display", () -> c.dojoRankDisplay, v -> c.dojoRankDisplay = v)
                            .desc("Next to the Dojo \"Challenges\" menu: rank and points per test (red under 100), total, current belt and points to the next belt")
                            .tags("dojo", "belt", "challenges", "rank", "crimson");
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            if (!StringUtil.stripTrim(screen.getTitle().getString()).equals("Challenges")) return;
            panel = List.of();
            panelAt = 0;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                if (feature == null || !feature.active() || !SkyblockUtils.isInArea("Crimson Isle")) return;
                if (System.currentTimeMillis() - panelAt > 500) build(cs);
                draw(cs, g);
            });
        });
    }

    private static void build(AbstractContainerScreen<?> cs) {
        panelAt = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        int total = 0;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Matcher n = TEST.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
            if (!n.matches()) continue;
            for (String line : ItemUtil.loreLines(st)) {
                Matcher r = RANK.matcher(line.trim());
                if (!r.find()) continue;
                int score = Integer.parseInt(r.group(2));
                total += score;
                out.add("§e" + n.group(1) + "§f: " + rankColor(r.group(1)) + r.group(1) + " §7(" + (score < 100 ? "§c" : "§a")
                        + String.format(Locale.ROOT, "%,d", score) + "§7)");
                break;
            }
        }
        if (out.isEmpty()) {
            panel = List.of();
            return;
        }
        int cur = 0;
        for (int i = 0; i < BELT_POINTS.length; i++) if (total >= BELT_POINTS[i]) cur = i;
        out.add(0, "§6Dojo Ranks");
        out.add("§7Total Score: §6" + String.format(Locale.ROOT, "%,d", total) + " §7(" + BELT_NAMES[cur] + "§7)");
        if (cur + 1 < BELT_POINTS.length) {
            out.add("§7Points needed for " + BELT_NAMES[cur + 1] + "§f: §6" + String.format(Locale.ROOT, "%,d", BELT_POINTS[cur + 1] - total));
        }
        panel = out;
    }

    private static String rankColor(String rank) {
        return switch (rank) {
            case "S" -> "§6";
            case "A" -> "§a";
            case "B" -> "§e";
            case "C" -> "§2";
            case "D" -> "§c";
            default -> "§4";
        };
    }

    private static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        List<String> lines = panel;
        if (lines.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        w += 8;
        HandledScreenAccessor a = (HandledScreenAccessor) cs;
        int x = a.getX() - w - 6, y = a.getY();
        if (x < 2) return;
        HudStyle.screenPanel(g, x, y, w, 6 + lines.size() * 10, 4);
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFFFF);
    }
}
