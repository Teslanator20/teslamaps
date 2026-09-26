package com.teslamaps.features.mobvisuals;

// Ported from Bax's features/combat/ElusiveHealthBar.java + utils/EntityUtils.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.CropStats;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MobHealthHud {

    public static final List<String> DEFAULTS = List.of("Inquisitor", "Jawbus", "Plhlegblast", "Ragnarok", "Thunder",
            "Titanoboa", "Wiki Tiki", "King Minos", "Sphinx", "Manticore");

    private static final Pattern HEALTH = Pattern.compile("([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)\\s*[❤♥\\uE010]");
    private static final Pattern HEALTH_ALT = Pattern.compile("[❤♥\\uE010]\\s*([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)");
    private static final Pattern LEVEL = Pattern.compile("\\[Lv\\d+]\\s*");
    private static final Pattern STRIP = Pattern.compile("[\\d,.]+[kKmMbB]?\\s*/\\s*[\\d,.]+[kKmMbB]?\\s*[❤♥\\uE010]?");
    private static final Pattern STRIP_ALT = Pattern.compile("[❤♥\\uE010]\\s*[\\d,.]+[kKmMbB]?\\s*/\\s*[\\d,.]+[kKmMbB]?");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-zA-Z0-9\\s']");
    private static final int MAX = 5;
    private static final List<String> SAMPLE = List.of("§cInquisitor  §a31.2M§8/§a40M", "§cJawbus  §e21M§8/§e45M");

    /** Parsed "cur/max❤" nametag; name is plain, without level and health. */
    public record Parsed(Component src, String name, double cur, double max) {}

    private static Int2ObjectOpenHashMap<Parsed> cache = new Int2ObjectOpenHashMap<>();
    private static List<String> lines = List.of();
    private static int linesW;
    private static int ticks;
    private static HudEditElement hud;

    private MobHealthHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("mobHealthHud", "Mob Health List").section("Mob Visuals")
                .enabled(() -> cfg().mobVisuals && cfg().mobHealthHud)
                .size(() -> Math.max(linesW, 130), () -> CropStats.linesHeight(Math.max(lines.size(), 2)))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, SAMPLE, 130));
        Feature.of("MobHealthHud").section("Mob Visuals").when(() -> cfg().mobVisuals && cfg().mobHealthHud)
                .tick(MobHealthHud::tick)
                .hudHiddenInInventory("mobhealthhud", MobHealthHud::render);
    }

    /** Health of a "cur/max❤" nametag, or null. */
    public static double[] parseHealth(String clean) {
        Matcher m = HEALTH.matcher(clean);
        if (!m.find()) {
            m = HEALTH_ALT.matcher(clean);
            if (!m.find()) return null;
        }
        double cur = NumberUtil.parseSuffixed(m.group(1));
        double max = NumberUtil.parseSuffixed(m.group(2));
        return max > 0 ? new double[]{cur, max} : null;
    }

    public static String parseName(String clean) {
        String s = LEVEL.matcher(clean).replaceAll("");
        s = STRIP.matcher(s).replaceAll("");
        s = STRIP_ALT.matcher(s).replaceAll("");
        s = NON_ALNUM.matcher(s).replaceAll("").trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? "Mob" : s;
    }

    private static void tick() {
        if (++ticks % 4 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        List<String> tracked = c.mobHealthHudMobs;
        Int2ObjectOpenHashMap<Parsed> next = new Int2ObjectOpenHashMap<>();
        List<ArmorStand> hits = new ArrayList<>();
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            Component name = s.getCustomName();
            if (name == null) continue;
            Parsed p = cache.get(s.getId());
            if (p == null || p.src != name) {
                String raw = name.getString();
                if (raw.indexOf('❤') < 0 && raw.indexOf('♥') < 0 && raw.indexOf('') < 0) continue;
                String clean = StringUtil.strip(raw);
                double[] hp = parseHealth(clean);
                if (hp == null) continue;
                p = new Parsed(name, parseName(clean), hp[0], hp[1]);
            }
            next.put(s.getId(), p);
            if (p.cur > 0 && matches(tracked, p.name)) hits.add(s);
        }
        cache = next;
        hits.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        List<String> out = new ArrayList<>(MAX);
        int w = 0;
        for (ArmorStand s : hits) {
            Parsed p = next.get(s.getId());
            double pct = p.cur / p.max;
            String col = pct > 0.5 ? "§a" : pct > 0.25 ? "§e" : "§c";
            String l = "§c" + p.name + "  " + col + NumberUtil.formatSuffixed(p.cur) + "§8/" + col + NumberUtil.formatSuffixed(p.max);
            out.add(l);
            w = Math.max(w, mc.font.width(l));
            if (out.size() >= MAX) break;
        }
        linesW = w + 8;
        lines = out;
    }

    private static boolean matches(List<String> tracked, String name) {
        if (tracked == null || tracked.isEmpty()) return false;
        String low = name.toLowerCase(Locale.ROOT);
        for (String t : tracked) if (!t.isEmpty() && low.contains(t.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        CropStats.drawLines(g, mc, hud.x(), hud.y(), hud.scale(), l, linesW);
    }
}
