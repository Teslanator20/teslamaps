package com.teslamaps.features.mining;

// Durability bar after Skyblocker's ItemStackMixin/ItemUtils.getDurability, fuel HUD after NEU's FuelBar
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fuel / durability bar under drills and the Pickonimbus, plus a fuel HUD while holding a drill. */
public final class DrillFuelDisplay {

    private static final Pattern ACTION_BAR = Pattern.compile("([\\d,]+)/([\\d,]+k?) Drill Fuel");
    private static final int PICKONIMBUS_MAX = 2000;
    private static final int BAR_W = 80;
    private static final float NONE = -1f;

    private static final Map<ItemStack, Float> cache = new WeakHashMap<>();
    private static int abFuel = -1, abMax = -1;
    private static long abAt;
    private static int ticks;
    private static String text = "";
    private static float fill = NONE;
    private static int textW;
    private static HudEditElement hud;

    private DrillFuelDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("drillFuelHud", "Drill Fuel").section("Mining")
                .enabled(() -> cfg().drillFuelDisplay && cfg().drillFuelHud)
                .size(() -> BAR_W, () -> 20)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§2Drill Fuel: §a2,345§7/§a3,000", 0.78f));
        Feature.of("DrillFuelDisplay").section("Mining").when(() -> cfg().drillFuelDisplay)
                .tick(DrillFuelDisplay::tick)
                .hudHiddenInInventory("drillfuelhud", DrillFuelDisplay::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Drill Fuel");
                    b.toggle("Drill Fuel Display", () -> c.drillFuelDisplay, v -> c.drillFuelDisplay = v)
                            .desc("Shows drill fuel and Pickonimbus durability, which stay hidden otherwise (especially with Hide Drill Fuel on).")
                            .tags("drill", "fuel", "pickonimbus", "durability", "bar");
                    b.toggle("  Durability Bars", () -> c.drillFuelBars, v -> c.drillFuelBars = v)
                            .desc("Red-to-green bar under the item icon, like vanilla durability");
                    b.toggle("  Fuel HUD", () -> c.drillFuelHud, v -> c.drillFuelHud = v)
                            .desc("Fuel bar while holding a drill. Move it in /tmap gui.");
                });
    }

    /** From InGameHudMixin, before the action bar filter hides the fuel. */
    public static void onActionBar(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.drillFuelDisplay || !c.drillFuelHud) return;
        String s = message.getString();
        if (!s.contains("Drill Fuel")) return;
        Matcher m = ACTION_BAR.matcher(StringUtil.strip(s));
        if (!m.find()) return;
        abFuel = num(m.group(1));
        abMax = num(m.group(2));
        abAt = System.currentTimeMillis();
    }

    private static int num(String s) {
        String t = s.replace(",", "").toLowerCase(Locale.ROOT);
        int mult = 1;
        if (t.endsWith("k")) {
            mult = 1000;
            t = t.substring(0, t.length() - 1);
        }
        try { return Integer.parseInt(t) * mult; } catch (NumberFormatException e) { return -1; }
    }

    private static void tick() {
        if (++ticks % 200 == 0) cache.clear();
        if (ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        fill = NONE;
        text = "";
        if (!cfg().drillFuelHud || mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!ItemUtil.skyblockId(held).contains("DRILL")) return;
        int fuel, max;
        if (System.currentTimeMillis() - abAt < 3000 && abMax > 0) {
            fuel = abFuel;
            max = abMax;
        } else {
            int[] lf = loreFuel(held);
            if (lf == null) return;
            fuel = lf[0];
            max = lf[1];
        }
        fill = Mth.clamp(fuel / (float) max, 0f, 1f);
        String col = fill > 0.5f ? "§a" : fill > 0.2f ? "§e" : "§c";
        text = "§2Drill Fuel: " + col + String.format(Locale.ROOT, "%,d", fuel) + "§7/§a" + String.format(Locale.ROOT, "%,d", max);
        textW = mc.font.width(text);
    }

    private static int[] loreFuel(ItemStack st) {
        for (String l : ItemUtil.loreLines(st)) {
            int i = l.indexOf("Fuel: ");
            if (i < 0) continue;
            String[] p = l.substring(i + 6).trim().split("/");
            if (p.length != 2) return null;
            int fuel = num(p[0].trim()), max = num(p[1].trim().split(" ")[0]);
            return fuel >= 0 && max > 0 ? new int[]{fuel, max} : null;
        }
        return null;
    }

    private static float fraction(ItemStack st) {
        Float f = cache.get(st);
        if (f != null) return f;
        float v = NONE;
        String id = ItemUtil.skyblockId(st);
        if (id.equals("PICKONIMBUS")) {
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            int dur = d == null ? PICKONIMBUS_MAX : d.copyTag().getIntOr("pickonimbus_durability", PICKONIMBUS_MAX);
            v = Mth.clamp(dur / (float) PICKONIMBUS_MAX, 0f, 1f);
        } else {
            int[] lf = loreFuel(st);
            if (lf != null) v = Mth.clamp(lf[0] / (float) lf[1], 0f, 1f);
        }
        cache.put(st, v);
        return v;
    }

    /** From ItemBarMixin; true replaces the vanilla bar. */
    public static boolean drawBar(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        TeslaMapsConfig c = cfg();
        if (!c.drillFuelDisplay || !c.drillFuelBars || stack.isEmpty()) return false;
        String id = ItemUtil.skyblockId(stack);
        if (!id.contains("DRILL") && !id.equals("PICKONIMBUS")) return false;
        if (!c.section("Mining")) return false;
        float f = fraction(stack);
        if (f < 0) return false;
        int w = Math.round(13 * f);
        int rgb = Mth.hsvToRgb(f / 3f, 1f, 1f);
        g.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
        g.fill(x + 2, y + 13, x + 2 + w, y + 14, 0xFF000000 | rgb);
        return true;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || fill < 0 || !cfg().drillFuelHud) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), text, fill);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String label, float f) {
        int pad = HudStyle.padding();
        int w = Math.max(BAR_W, label == text ? textW : mc.font.width(label));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, w + pad * 2, 19 + pad * 2);
        g.text(mc.font, label, 0, 0, 0xFFFFFFFF, true);
        g.fill(0, 11, w, 18, 0xC0101010);
        int fw = Math.round((w - 2) * f);
        if (fw > 0) g.fill(1, 12, 1 + fw, 17, 0xFF000000 | Mth.hsvToRgb(f / 3f, 1f, 1f));
        pose.popMatrix();
    }
}
