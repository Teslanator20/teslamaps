package com.teslamaps.features.kuudra;

// Ported from IQ's SupplyDetector + custom supply message, regex from Odin's SupplyHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraSupply {

    private KuudraSupply() {}

    private static final Pattern RECOVERED = Pattern.compile(
            "^(?:\\[[^\\]]*\\] ?)?(\\w{1,16}) recovered one of Elle's supplies! \\((\\d)/(\\d)\\)$");
    private static final String RETRIEVED = "You retrieved some of Elle's supplies from the Lava!";

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraSupply").section("Kuudra").when(() -> cfg().kuuSupplyMessage)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Supplies");
                    b.toggle("Compact Supply Message", () -> c.kuuSupplyMessage, v -> c.kuuSupplyMessage = v)
                            .desc("Replaces the supply recovered line with name, run time and count, hides \"You retrieved some of Elle's supplies\"");
                    KuudraRunInfo.supplyConfig(b);
                });
    }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.kuuSupplyMessage && c.section("Kuudra") && KuudraBuild.inKuudra();
    }

    /** ChatMixin HEAD: drop the pickup line. */
    public static boolean hide(String text) {
        if (!text.contains("Elle's supplies from the Lava") || !on()) return false;
        return StringUtil.stripTrim(text).equals(RETRIEVED);
    }

    public static Component rewrite(Component message) {
        String s = message.getString();
        if (!s.contains("recovered one of Elle's supplies") || !on()) return message;
        Matcher m = RECOVERED.matcher(StringUtil.stripTrim(s));
        if (!m.matches()) return message;
        long runStart = Kuudra.runStartMs();
        String time = runStart > 0
                ? "§e" + String.format(Locale.ROOT, "%.2fs", (System.currentTimeMillis() - runStart) / 1000.0) : "§e?";
        return Component.literal("§6" + m.group(1) + " §arecovered a supply in " + time + " §8(" + m.group(2) + "/" + m.group(3) + ")");
    }
}
