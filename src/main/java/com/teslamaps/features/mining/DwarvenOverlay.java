package com.teslamaps.features.mining;

// After NEU's MiningOverlay, SkyMallDisplay.kt and StarCultCalculator
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Mining HUD: commission %, powders, forge slots, pickaxe cooldown, Star Cult and today's Sky Mall perk. */
public final class DwarvenOverlay {

    private static final long YEAR_0 = 1560275700000L;
    private static final long DAY_MS = 1_200_000L;
    private static final long CULT_MS = 300_000L;
    private static final String NEW_DAY = "New day! Your Sky Mall buff changed!";
    private static final String NEW_BUFF = "New buff: ";

    private static final List<String> SAMPLE = List.of("§3§lMining", "§fMithril Miner: §a52%",
            "§3Mithril: §a1,234,567", "§3Gemstone: §d812,004", "§3Glacite: §b64,210", "§6Pickobulus§8: §aAvailable",
            "§3Star Cult: §a14m 2s", "§3Sky Mall: §a+100⸕ Mining Speed §7(12m)");

    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static long buffAt;
    private static HudEditElement hud;

    private DwarvenOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("dwarvenOverlay", "Dwarven Overlay").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().dwarvenOverlay)
                .size(() -> Math.max(80, lines.isEmpty() ? width(SAMPLE) : width),
                        () -> (lines.isEmpty() ? SAMPLE.size() : lines.size()) * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? width(SAMPLE) : width));
        Feature.of("SkyMallTracker").section("Mining").when(() -> cfg().dwarvenOverlay && cfg().dwarvenOverlaySkyMall)
                .serverChat(DwarvenOverlay::onChat);
        Feature.of("DwarvenOverlay").section("Mining").when(() -> cfg().dwarvenOverlay)
                .tick(DwarvenOverlay::tick)
                .hudHiddenInInventory("dwarvenoverlay", DwarvenOverlay::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Dwarven Overlay");
                    b.toggle("Dwarven Overlay", () -> c.dwarvenOverlay, v -> c.dwarvenOverlay = v)
                            .desc("One mining HUD with commission progress, powders, forge slots, pickaxe cooldown, the next Star Cult and today's Sky Mall perk. Move it in /tmap gui.")
                            .tags("dwarven", "commission", "powder", "sky mall", "star cult", "forge", "mithril", "gemstone", "glacite");
                    b.toggle("  Commissions", () -> c.dwarvenOverlayCommissions, v -> c.dwarvenOverlayCommissions = v);
                    b.toggle("  Powders", () -> c.dwarvenOverlayPowders, v -> c.dwarvenOverlayPowders = v);
                    b.toggle("  Forge Slots", () -> c.dwarvenOverlayForge, v -> c.dwarvenOverlayForge = v);
                    b.toggle("  Pickaxe Ability", () -> c.dwarvenOverlayPickaxe, v -> c.dwarvenOverlayPickaxe = v);
                    b.toggle("  Star Cult", () -> c.dwarvenOverlayStarCult, v -> c.dwarvenOverlayStarCult = v);
                    b.toggle("  Sky Mall", () -> c.dwarvenOverlaySkyMall, v -> c.dwarvenOverlaySkyMall = v)
                            .desc("Taken from the \"New buff\" chat line, so it shows after the next SkyBlock day change");
                    b.info("Needs the Commissions, Powders, Forges and Pickaxe Ability tab widgets: /tab");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.equals(NEW_DAY)) {
            buffAt = System.currentTimeMillis();
            return;
        }
        if (!t.startsWith(NEW_BUFF) || System.currentTimeMillis() - buffAt > 3000) return;
        String perk = t.substring(NEW_BUFF.length()).trim();
        if (perk.startsWith("Gain ")) perk = perk.substring(5);
        if (perk.endsWith(".")) perk = perk.substring(0, perk.length() - 1);
        buffAt = 0;
        cfg().skyMallPerk = perk;
        cfg().skyMallDay = sbDay(System.currentTimeMillis());
        TeslaMapsConfig.save();
    }

    private static long sbDay(long now) {
        return Math.floorDiv(now - YEAR_0, DAY_MS);
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        if (!Mining.onMiningIsland()) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>();
        out.add("§3§lMining");
        if (c.dwarvenOverlayCommissions) {
            for (Commissions.Commission cm : Commissions.active()) {
                int pct = Math.round(cm.progress() * 100);
                String col = cm.done() || pct >= 75 ? "§a" : pct >= 40 ? "§e" : "§c";
                out.add("§f" + cm.name() + ": " + col + (cm.done() ? "DONE" : pct + "%"));
            }
        }
        if (c.dwarvenOverlayPowders) powders(out);
        if (c.dwarvenOverlayForge) out.addAll(ForgeSlotHud.slotLines());
        if (c.dwarvenOverlayPickaxe) out.addAll(PickaxeAbility.lines());
        long now = System.currentTimeMillis();
        if (c.dwarvenOverlayStarCult) out.add("§3Star Cult: " + starCult(now));
        if (c.dwarvenOverlaySkyMall) {
            long day = sbDay(now);
            String left = ForgeSlotHud.format(YEAR_0 + (day + 1) * DAY_MS - now);
            if (c.skyMallDay == day && !c.skyMallPerk.isEmpty()) out.add("§3Sky Mall: §a" + c.skyMallPerk + " §7(" + left + ")");
            else out.add("§3Sky Mall: §7? §8(next in " + left + ")");
        }
        lines = out.size() > 1 ? out : List.of();
        width = width(lines);
    }

    private static void powders(List<String> out) {
        boolean in = false;
        for (String raw : TabListUtils.getOrderedLines()) {
            String line = StringUtil.stripTrim(raw);
            if (!in) {
                if (line.equals("Powders:")) in = true;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || line.isEmpty()) break;
            String kind = line.substring(0, colon);
            String col = switch (kind) {
                case "Mithril" -> "§a";
                case "Gemstone" -> "§d";
                case "Glacite" -> "§b";
                default -> null;
            };
            if (col == null) break;
            out.add("§3" + kind + ": " + col + line.substring(colon + 1).trim());
        }
    }

    /** Star Cult: days 7/14/21/28 of every SkyBlock month, 5 real minutes from the start of the day. */
    private static String starCult(long now) {
        long day = sbDay(now);
        for (long d = day; d < day + 40; d++) {
            long dom = Math.floorMod(d, 31) + 1;
            if (dom % 7 != 0 || dom > 28) continue;
            long start = YEAR_0 + d * DAY_MS;
            if (start + CULT_MS <= now) continue;
            if (start <= now) return "§aActive! §7(" + ForgeSlotHud.format(start + CULT_MS - now) + ")";
            return "§a" + ForgeSlotHud.format(start - now);
        }
        return "§7?";
    }

    private static int width(List<String> l) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : l) w = Math.max(w, mc.font.width(s));
        return w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), l, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        int pad = HudStyle.padding();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, w + pad * 2, l.size() * 10 - 1 + pad * 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
