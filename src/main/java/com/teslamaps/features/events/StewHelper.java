package com.teslamaps.features.events;

// Ported from SkyHanni's StewHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.SackApi;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Witches Stew menu: ingredients of the stews not eaten yet with sack amounts, eaten/ready stews highlighted. */
public final class StewHelper {

    private static final Pattern INGREDIENT = Pattern.compile("^(.*) x(\\d+)$");

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inMenu;
    private static int ticks, panelW;
    private static List<String> lines = List.of();
    private static int[] slotColors = new int[0];

    private StewHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("StewHelper").section("Events")
                .when(() -> cfg().stewHelper)
                .tick(StewHelper::tick)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Year of the Witch");
                    b.toggle("Witches Stew Helper", () -> c.stewHelper, v -> c.stewHelper = v)
                            .desc("In the Witches Stew menu: ingredients for every stew you have not eaten yet, with your "
                                    + "sack amount (green = enough). Needs the Sack API for the amounts")
                            .tags("stew", "witch", "year of the witch", "sacks");
                    b.toggle("  Highlight Stews", () -> c.stewHighlight, v -> c.stewHighlight = v)
                            .desc("Eaten stews green, stews you can hand in now yellow");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) { lastScreen = null; inMenu = false; lines = List.of(); }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            lines = List.of();
            inMenu = StringUtil.stripTrim(cs.getTitle().getString()).equals("Witches Stew");
        }
        if (!inMenu || ticks++ % 10 != 0) return;
        build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        List<Slot> slots = cs.getMenu().slots;
        int[] colors = new int[slots.size()];
        Map<String, Integer> need = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (!stack.is(Items.PLAYER_HEAD)) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            if (lore.isEmpty()) continue;
            String last = StringUtil.stripTrim(lore.get(lore.size() - 1));
            if (last.equals("You've already eaten this stew!")) { colors[i] = 0x8055FF55; continue; }
            if (last.equals("Click to give ingredients!")) colors[i] = 0x80FFFF55;
            for (int l = 0; l < lore.size() - 1; l++) {
                if (!StringUtil.stripTrim(lore.get(l)).equals("Requires:")) continue;
                Matcher m = INGREDIENT.matcher(StringUtil.stripTrim(lore.get(l + 1)));
                if (m.matches()) need.put(m.group(1), Integer.parseInt(m.group(2)));
                break;
            }
        }
        List<String> out = new ArrayList<>();
        out.add("§d§lStew Helper");
        if (need.isEmpty()) out.add("§a§lYou have eaten all the stews!");
        for (var e : need.entrySet()) {
            long have = SackApi.amount(e.getKey());
            String c = have >= e.getValue() ? "§a" : "§e";
            out.add("§7- §a" + e.getKey() + " " + c + (have < 0 ? "?" : String.valueOf(have)) + "/" + e.getValue());
        }
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : out) w = Math.max(w, font.width(s));
        panelW = w;
        slotColors = colors;
        lines = out;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!inMenu || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("StewHelper.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (cfg().stewHighlight) {
            List<Slot> slots = cs.getMenu().slots;
            int[] colors = slotColors;
            for (int i = 0; i < colors.length && i < slots.size(); i++) {
                if (colors[i] == 0) continue;
                Slot s = slots.get(i);
                int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
                ctx.fill(sx, sy, sx + 16, sy + 16, colors[i]);
            }
        }
        List<String> l = lines;
        if (!l.isEmpty()) {
            int lh = mc.font.lineHeight + 1;
            int x = acc.getX() + acc.getImageWidth() + 8;
            int y = acc.getY() + 4;
            com.teslamaps.render.gui.HudStyle.panel(ctx, x - 4, y - 4, panelW + 8, l.size() * lh + 6);
            for (int i = 0; i < l.size(); i++) ctx.text(mc.font, l.get(i), x, y + i * lh, 0xFFFFFFFF);
        }
        Perf.end();
    }
}
