package com.teslamaps.features.rift;

// After SkyHanni's VerminTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Vacuumed Silverfish / Spiders / Flies, session and total, synced with the Vermin Bin. */
public final class VerminTracker {

    private static final String[] NAMES = {"§aFlies", "§aSpiders", "§aSilverfish"};
    private static final String[] BAG_KEYS = {"vacuumed_mosquito", "vacuumed_spider", "vacuumed_silverfish"};
    private static final String VACUUMED = "You vacuumed a ";
    private static final Pattern BIN = Pattern.compile("Vermin Bin: (\\d+) (\\w+)");

    private static final int[] session = new int[3];
    private static List<String> lines = List.of();
    private static boolean dirty = true, show, hasVacuum, shownTotal;
    private static int lineWidth;
    private static int ticks, binTicks;
    private static Object binScreen;

    private VerminTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("riftVerminTracker", "Vermin Tracker").ctx(HudEditElement.HudCtx.ANY).section("Rift")
                .enabled(() -> cfg().riftVerminTracker)
                .size(() -> Math.max(80, width()), () -> 4 * (Minecraft.getInstance().font.lineHeight + 1))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s));
        Feature.of("VerminTracker").section("Rift").when(() -> cfg().riftVerminTracker)
                .tick(VerminTracker::tick)
                .hud("riftvermintracker", VerminTracker::render)
                .serverChat(VerminTracker::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Vermin Tracker", () -> c.riftVerminTracker, v -> c.riftVerminTracker = v)
                            .desc("Counts vacuumed Silverfish, Spiders and Flies; opening the Vermin Bin syncs the totals. Move it in /tmap gui.")
                            .tags("vermin", "vacuum", "turbomax", "west village", "silverfish", "fly", "spider");
                    b.toggle("  Show Outside West Village", () -> c.riftVerminOutsideVillage, v -> c.riftVerminOutsideVillage = v);
                    b.toggle("  Show Without Vacuum", () -> c.riftVerminWithoutVacuum, v -> c.riftVerminWithoutVacuum = v)
                            .desc("Off = only while the Turbomax Vacuum is in your inventory");
                    b.toggle("  Hide Vacuum Chat", () -> c.riftVerminHideChat, v -> c.riftVerminHideChat = v)
                            .desc("Hides \"You vacuumed a ...!\"");
                    b.toggle("  Show Total", () -> c.riftVerminShowTotal, v -> c.riftVerminShowTotal = v)
                            .desc("Total instead of this session");
                    b.buttonRow(new String[]{"Reset Session", "Reset Total"}, new Runnable[]{() -> {
                        java.util.Arrays.fill(session, 0);
                        dirty = true;
                    }, () -> {
                        c.riftVerminTotalFly = c.riftVerminTotalSpider = c.riftVerminTotalSilverfish = 0;
                        dirty = true;
                        TeslaMapsConfig.save();
                    }});
                });
    }

    private static int type(String t) {
        if (!t.startsWith(VACUUMED)) return -1;
        if (t.contains("Silverfish")) return 2;
        if (t.contains("Spider")) return 1;
        if (t.contains("Fly")) return 0;
        return -1;
    }

    private static void onChat(String raw) {
        if (!RiftSpot.inRift()) return;
        int i = type(StringUtil.stripTrim(raw));
        if (i < 0) return;
        session[i]++;
        setTotal(i, total(i) + 1);
    }

    /** From ChatMixin. */
    public static boolean hide(String text) {
        TeslaMapsConfig c = cfg();
        if (!c.riftVerminTracker || !c.riftVerminHideChat || !c.section("Rift")) return false;
        return type(StringUtil.stripTrim(text)) >= 0 && RiftSpot.inRift();
    }

    private static int total(int i) {
        TeslaMapsConfig c = cfg();
        return i == 0 ? c.riftVerminTotalFly : i == 1 ? c.riftVerminTotalSpider : c.riftVerminTotalSilverfish;
    }

    private static void setTotal(int i, int v) {
        TeslaMapsConfig c = cfg();
        if (i == 0) c.riftVerminTotalFly = v;
        else if (i == 1) c.riftVerminTotalSpider = v;
        else c.riftVerminTotalSilverfish = v;
        dirty = true;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean tot = cfg().riftVerminShowTotal;
        if (dirty || tot != shownTotal) {
            dirty = false;
            shownTotal = tot;
            String[] out = new String[4];
            out[0] = "§7Vermin Tracker " + (tot ? "§8(Total)" : "§8(Session)");
            for (int i = 0; i < 3; i++) out[i + 1] = " §7- §e" + String.format(java.util.Locale.ROOT, "%,d", tot ? total(i) : session[i]) + " " + NAMES[i];
            int w = 0;
            for (String line : out) w = Math.max(w, mc.font.width(line));
            lineWidth = w;
            lines = List.of(out);
        }
        boolean rift = RiftSpot.inRift();
        if (!rift) {
            show = false;
            return;
        }
        if (++ticks >= 20) {
            ticks = 0;
            hasVacuum = vacuum(mc) != null;
            boolean village = cfg().riftVerminOutsideVillage;
            if (!village) {
                String a = RiftSpot.sub();
                village = a.equals("West Village") || a.equals("Infested House");
            }
            show = village && (cfg().riftVerminWithoutVacuum || hasVacuum);
        }
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs && cs != binScreen && ++binTicks >= 10) {
            binTicks = 0;
            if (StringUtil.stripTrim(cs.getTitle().getString()).equals("Vermin Bin")) syncBin(mc, cs);
        }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) binScreen = null;
    }

    private static void syncBin(Minecraft mc, AbstractContainerScreen<?> cs) {
        if (cs.getMenu().slots.size() <= 13) return;
        ItemStack bin = cs.getMenu().slots.get(13).getItem();
        ItemLore lore = bin.get(DataComponents.LORE);
        if (lore == null) return;
        int[] counts = new int[3];
        boolean found = false;
        for (Component line : lore.lines()) {
            Matcher m = BIN.matcher(StringUtil.stripTrim(line.getString()));
            if (!m.find()) continue;
            found = true;
            String v = m.group(2).toLowerCase(java.util.Locale.ROOT);
            int i = v.startsWith("silverfish") ? 2 : v.startsWith("spider") ? 1 : v.startsWith("fl") ? 0 : 2;
            counts[i] = Integer.parseInt(m.group(1));
        }
        if (!found) return;
        binScreen = cs;
        ItemStack vac = vacuum(mc);
        if (vac != null) {
            CustomData d = vac.get(DataComponents.CUSTOM_DATA);
            if (d != null) {
                CompoundTag tag = d.copyTag();
                for (int i = 0; i < 3; i++) counts[i] += tag.getIntOr(BAG_KEYS[i], 0);
            }
        }
        for (int i = 0; i < 3; i++) setTotal(i, counts[i]);
    }

    private static ItemStack vacuum(Minecraft mc) {
        if (mc.player == null) return null;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && "TURBOMAX_VACUUM".equals(ItemUtil.skyblockId(s))) return s;
        }
        return null;
    }

    private static int width() {
        return lineWidth;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        List<String> l = lines;
        if (l.isEmpty()) return;
        int pad = HudStyle.padding();
        int h = l.size() * (mc.font.lineHeight + 1);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, width() + pad * 2, h + pad * 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * (mc.font.lineHeight + 1), 0xFFFFFFFF);
        pose.popMatrix();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        draw(g, mc, c.riftVerminTrackerX, c.riftVerminTrackerY, c.riftVerminTrackerScale);
    }
}
