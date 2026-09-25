package com.teslamaps.features;

// Ported from SkyHanni's FarmingPersonalBestGain.kt. The items-per-fortune rate is learned from the
// perk message itself instead of SkyHanni's repo table.
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PbFortuneGain {

    private static final Pattern NEW = Pattern.compile("\\[NPC] Jacob: You collected ([\\d,]+) items! PERSONAL BEST!");
    private static final Pattern OLD = Pattern.compile("\\[NPC] Jacob: Your previous Personal Best was ([\\d,]+)\\.");
    private static final Pattern FF = Pattern.compile("\\[NPC] Jacob: Your Personal Bests perk is now granting you \\+([\\d.,]+)☘? ?(.+) Fortune!");

    private static double newCollected = -1, oldCollected = -1, newFf = -1;
    private static String crop;
    private static long lastMsg;

    private PbFortuneGain() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PbFortuneGain").section("Garden").when(() -> cfg().pbFortuneGain)
                .serverChat(PbFortuneGain::onChat)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Personal Best Fortune Gain", () -> c.pbFortuneGain, v -> c.pbFortuneGain = v)
                            .desc("After a new Jacob contest personal best: how much crop fortune the Personal Bests perk gained.")
                            .tags("jacob", "contest", "personal best", "pb", "fortune");
                    b.toggle("  Include Overflow Over 100", () -> c.pbFortuneOverflow, v -> c.pbFortuneOverflow = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("[NPC] Jacob: ")) return;
        long now = System.currentTimeMillis();
        if (now - lastMsg > 5000L) reset();
        Matcher m;
        if ((m = NEW.matcher(t)).matches()) newCollected = num(m.group(1));
        else if ((m = OLD.matcher(t)).matches()) oldCollected = num(m.group(1));
        else if ((m = FF.matcher(t)).matches()) {
            newFf = num(m.group(1));
            crop = m.group(2).trim();
        } else return;
        lastMsg = now;
        check();
    }

    private static void check() {
        if (newCollected < 0 || oldCollected < 0 || newFf < 0 || crop == null) return;
        TeslaMapsConfig c = cfg();
        String key = crop.toLowerCase(Locale.ROOT);
        if (newFf > 0 && newFf < 100) {
            c.pbItemsPerFortune.put(key, newCollected / newFf);
            TeslaMapsConfig.save();
        }
        Double rate = c.pbItemsPerFortune.get(key);
        double nc = newCollected, oc = oldCollected, nf = newFf;
        String cr = crop;
        reset();
        if (rate == null || rate <= 0) {
            say("§7Personal best fortune is maxed, gain unknown until a non-maxed PB for §e" + cr + "§7 was seen.");
            return;
        }
        double oldFf = oc / rate;
        double newOverflow = nc / rate;
        if (!c.pbFortuneOverflow) {
            if (oldFf < 100) say("This is §6" + fmt(nf - oldFf) + "☘ " + cr + " Fortune §fmore than previously!");
        } else if (newOverflow > 100) {
            say("You have §6" + fmt(newOverflow) + "☘ " + cr + " Fortune §fincluding overflow!");
            say("This is §6" + fmt(newOverflow - oldFf) + "☘ " + cr + " Fortune §fmore than previously!");
        } else {
            say("This is §6" + fmt(nf - Math.min(100, oldFf)) + "☘ " + cr + " Fortune §fmore than previously!");
        }
    }

    private static void reset() {
        newCollected = oldCollected = newFf = -1;
        crop = null;
    }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + msg));
    }
}
