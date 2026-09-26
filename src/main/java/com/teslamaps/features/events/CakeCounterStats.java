package com.teslamaps.features.events;

// After SkyHanni's CakeCounterFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Private Island Cake Counter: what happened while you were away, and live Cake Soul finds. */
public final class CakeCounterStats {

    private static final Pattern CAKES = Pattern.compile("^Cakes Eaten: ([\\d,]+)$");
    private static final Pattern SOULS = Pattern.compile("^Souls Found: ([\\d,]+)$");

    private static Object lastLevel;
    private static int ticks, seenTicks;
    private static long cakes = -1, souls = -1;
    private static boolean reported;
    private static long lastSelfSoul;

    private CakeCounterStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CakeCounterStats").section("Events").when(() -> cfg().cakeCounterStats)
                .tick(CakeCounterStats::tick)
                .serverChat(CakeCounterStats::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Cake Counter Stats", () -> c.cakeCounterStats, v -> c.cakeCounterStats = v)
                            .desc("Private Island: on join, how many Century Cakes were eaten and Cake Souls found since you were last there.")
                            .tags("cake", "soul", "century", "private island");
                    b.toggle("  Cake Soul Found Alert", () -> c.cakeCounterSoulAlert, v -> c.cakeCounterSoulAlert = v)
                            .desc("Chat line when someone finds a Cake Soul on your island while you are there");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            cakes = -1;
            souls = -1;
            reported = false;
            seenTicks = 0;
        }
        if (ticks++ % 10 != 0 || !SkyblockUtils.isInArea("Private Island")) return;
        long c = -1, s = -1;
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            String n = StringUtil.stripTrim(as.getCustomName().getString());
            Matcher m = CAKES.matcher(n);
            if (m.matches()) { c = num(m.group(1)); continue; }
            m = SOULS.matcher(n);
            if (m.matches()) s = num(m.group(1));
        }
        if (c < 0) return;
        TeslaMapsConfig cfg = cfg();
        if (!reported) {
            // souls stand is optional, give it a few seconds to show up
            if (s < 0 && ++seenTicks < 6) return;
            reported = true;
            report(cfg, c, s);
        } else {
            if (s > souls && souls >= 0 && cfg.cakeCounterSoulAlert && System.currentTimeMillis() - lastSelfSoul > 1500) {
                chat("§eSomeone just found a Cake Soul on your Island!");
            }
        }
        cakes = c;
        souls = s;
        if (c > cfg.cakeCounterCakes || (s >= 0 && s > cfg.cakeCounterSouls)) {
            cfg.cakeCounterCakes = Math.max(c, cfg.cakeCounterCakes);
            if (s >= 0) cfg.cakeCounterSouls = Math.max(s, cfg.cakeCounterSouls);
            TeslaMapsConfig.save();
        }
    }

    private static void report(TeslaMapsConfig cfg, long c, long s) {
        if (cfg.cakeCounterCakes < 0) return;
        long dc = c - cfg.cakeCounterCakes;
        long ds = s >= 0 && cfg.cakeCounterSouls >= 0 ? s - cfg.cakeCounterSouls : 0;
        if (dc <= 0 && ds <= 0) return;
        StringBuilder sb = new StringBuilder("§eSince you were last on your Private Island, players ");
        if (dc > 0) sb.append("ate §6").append(fmt(dc)).append(dc == 1 ? " §eCentury Cake" : " §eCentury Cakes");
        if (dc > 0 && ds > 0) sb.append(" and ");
        if (ds > 0) sb.append("found §d").append(fmt(ds)).append(ds == 1 ? " §eCake Soul" : " §eCake Souls");
        chat(sb.append('.').toString());
    }

    private static void onChat(String raw) {
        if (StringUtil.stripTrim(raw).equals("You found a Cake Soul!")) lastSelfSoul = System.currentTimeMillis();
    }

    private static long num(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Cake Counter] §r" + msg));
    }
}
