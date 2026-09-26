package com.teslamaps.features;

// After Skysoft's DianaSphinxAnswerHighlighter.kt (answer table also in WpcMod's AutoAnswerSphinx.kt)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.Map;

public final class SphinxHelper {

    private static final Map<String, String> ANSWERS = Map.ofEntries(
            Map.entry("How do you obtain the Dark Purple Dye?", "Dark Auction"),
            Map.entry("How many floors are there in The Catacombs?", "7"),
            Map.entry("What does Junker Joel collect?", "Junk"),
            Map.entry("What is the first type of slayer Maddox offers?", "Zombie"),
            Map.entry("What item do you use to kill Pests?", "Vacuum"),
            Map.entry("What type of mob is exclusive to the Fishing Festival?", "Shark"),
            Map.entry("Where is the Titanoboa found?", "Backwater Bayou"),
            Map.entry("Where is Trevor the Trapper found?", "Mushroom Desert"),
            Map.entry("Which item rarity comes after Mythic?", "Divine"),
            Map.entry("Which of these is NOT a pet?", "Slime"),
            Map.entry("Which of these is NOT a type of Gemstone?", "Prismite"),
            Map.entry("Which type of Gemstone has the lowest Breaking Power?", "Ruby"),
            Map.entry("Who helps you apply Rod Parts?", "Roddy"),
            Map.entry("Who owns the Gold Essence Shop?", "Marigold"),
            Map.entry("Who runs the Chocolate Factory?", "Hoppity"));
    private static final String HEADER = "Question";
    private static final String FOOTER = "Click your response to answer!";
    private static final String RIGHT = "Right answer! The Sphinx is intrigued by your superior intellect.";
    private static final long TIMEOUT = 30_000L;

    private static Feature feature;
    private static boolean waiting;
    private static String answer;
    private static int seen;
    private static long expires;

    private SphinxHelper() {}

    public static void register() {
        feature = Feature.of("SphinxHelper").section("Events").when(() -> TeslaMapsConfig.get().sphinxHelper)
                .config("Events", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.toggle("Sphinx Answer Helper", () -> c.sphinxHelper, v -> c.sphinxHelper = v)
                            .desc("Highlights the correct answer line of the Sphinx question in chat (Hub, Diana). You still click it yourself.");
                });
    }

    /** Chat rewrite hook, every added line in order. */
    public static Component rewrite(Component message) {
        if (feature == null || !feature.active()) return message;
        long now = System.currentTimeMillis();
        if (now >= expires) clear();
        String text = StringUtil.stripTrim(message.getString());
        if (text.equals(RIGHT)) {
            clear();
            return message;
        }
        if (text.equals(HEADER)) {
            if (!SkyblockUtils.isInArea("Hub")) return message;
            waiting = true;
            answer = null;
            seen = 0;
            expires = now + TIMEOUT;
            return message;
        }
        if (waiting) {
            String a = ANSWERS.get(text);
            if (a == null) {
                clear();
            } else {
                waiting = false;
                answer = a;
                expires = now + TIMEOUT;
            }
            return message;
        }
        if (answer == null) return message;
        if (text.length() < 4 || text.charAt(1) != ')' || "ABC".indexOf(text.charAt(0)) < 0 || text.charAt(2) != ' ') {
            if (!text.isBlank() && !text.equals(FOOTER)) clear();
            return message;
        }
        seen++;
        Component out = text.substring(3).trim().equals(answer) ? highlight(message) : message;
        if (seen >= 3) clear();
        return out;
    }

    private static void clear() {
        waiting = false;
        answer = null;
        seen = 0;
        expires = 0;
    }

    // keeps the click event, only recolors
    private static MutableComponent highlight(Component c) {
        MutableComponent copy = c.getContents() instanceof PlainTextContents p
                ? Component.literal(StringUtil.strip(p.text())) : c.plainCopy();
        copy.setStyle(c.getStyle().withColor(ChatFormatting.GREEN).withBold(true));
        for (Component s : c.getSiblings()) copy.append(highlight(s));
        return copy;
    }
}
