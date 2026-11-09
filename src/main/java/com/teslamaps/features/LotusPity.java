package com.teslamaps.features;

// Ported from Devonian's features/misc/LotusPityDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.HudVisibility;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LotusPity {

    private static final Pattern TAB_FROG = Pattern.compile("^([●○]+) ([\\w ]+)$");
    private static final Pattern PROGRESS = Pattern.compile("^Progress to (GOLD|DIAMOND): (\\d+)/(\\d+)$");
    private static final Pattern CAUGHT = Pattern.compile(
            "^♔ TROPHY FROG! You caught (?:an? )?([\\w ]+?) (BRONZE|SILVER|GOLD|DIAMOND)(?: x(\\d+))?!$");
    private static final String SAMPLE = "§aCommon Frog §7(§bDIAMOND§7)§f: §c100§f/§a600";

    private static final Map<String, Integer> filled = new HashMap<>();
    private static String line;
    private static int timer, width;
    private static HudEditElement hud;

    private LotusPity() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("lotusPity", "Lotus Frog Pity").section("Fishing")
                .enabled(() -> cfg().lotusPity)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE) + 6, () -> 14)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, mc.font.width(SAMPLE)));
        Feature.of("LotusPity").section("Fishing").when(() -> cfg().lotusPity)
                .tick(LotusPity::tick)
                .serverChat(LotusPity::onChat)
                .hudHiddenInInventory("lotuspity", LotusPity::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Lotus Atoll");
                    b.toggle("Trophy Frog Pity", () -> c.lotusPity, v -> c.lotusPity = v)
                            .desc("Pity counter of the last trophy frog you caught. Open /pity -> Lotus Atoll once, keep the trophy frog tab widget visible. Position in /tmap gui.")
                            .tags("lotus", "atoll", "frog", "trophy", "pity", "fishing");
                });
    }

    private static void onChat(String text) {
        Matcher m = CAUGHT.matcher(StringUtil.stripTrim(text));
        if (!m.matches()) return;
        String name = m.group(1);
        int amount = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));
        cfg().lotusPityLast = name;
        int[] p = cfg().lotusPityData.get(name);
        if (p != null) {
            switch (m.group(2)) {
                case "GOLD" -> p[0] = 0;
                case "DIAMOND" -> p[2] = 0;
                default -> { p[0] += amount; p[2] += amount; }
            }
        }
        TeslaMapsConfig.save();
        rebuild();
    }

    private static void tick() {
        if (timer++ % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                && StringUtil.strip(cs.getTitle().getString()).trim().equals("Lotus Atoll Pity")) readMenu(cs);
        for (String raw : TabListUtils.getTabListLines()) {
            if (raw.indexOf('●') < 0 && raw.indexOf('○') < 0) continue;
            Matcher m = TAB_FROG.matcher(StringUtil.strip(raw).trim());
            if (!m.matches()) continue;
            int n = 0;
            for (char ch : m.group(1).toCharArray()) if (ch == '●') n++;
            filled.put(m.group(2).trim(), n);
        }
        rebuild();
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        var slots = cs.getMenu().slots;
        boolean changed = false;
        for (int i = 9; i <= 26 && i < slots.size(); i++) {
            ItemStack st = slots.get(i).getItem();
            if (!st.is(Items.PLAYER_HEAD)) continue;
            String name = StringUtil.strip(st.getHoverName().getString()).trim();
            int[] p = new int[4];
            boolean any = false;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = PROGRESS.matcher(l.trim());
                if (!m.matches()) continue;
                int off = m.group(1).equals("GOLD") ? 0 : 2;
                p[off] = Integer.parseInt(m.group(2));
                p[off + 1] = Integer.parseInt(m.group(3));
                any = true;
            }
            if (!any) continue;
            int[] old = cfg().lotusPityData.put(name, p);
            if (old == null || !java.util.Arrays.equals(old, p)) changed = true;
        }
        if (changed) TeslaMapsConfig.save();
    }

    private static void rebuild() {
        String last = cfg().lotusPityLast;
        int[] p = last == null || last.isEmpty() ? null : cfg().lotusPityData.get(last);
        Integer n = last == null ? null : filled.get(last);
        if (p == null) {
            line = null;
            return;
        }
        boolean gold = n == null ? p[1] > 0 && p[0] < p[1] : n < 3;
        int amt = gold ? p[0] : p[2], total = gold ? p[1] : p[3];
        line = "§a" + last + " §7(" + (gold ? "§6GOLD" : "§bDIAMOND") + "§7)§f: §c" + amt + "§f/§a" + total;
        width = Minecraft.getInstance().font.width(line);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), l, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String l, int w) {
        Perf.begin("LotusPity.hud");
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w + 6, 14);
        g.text(mc.font, l, 3, 3, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        Perf.end();
    }
}
