// Ported from SkyHanni's ForagingTracker.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.TickScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Squashes the whole TREE GIFT block into one line that keeps the reward hover, plus one
 * line per bonus gift drop. Patterns follow SkyHanni's ForagingTrackerLegacy.
 */
public class TreeGiftCompact {

    // "§2§l▬{64}" — the bar Hypixel wraps the gift block in
    private static final Pattern SEPARATOR = Pattern.compile("▬{64}");
    private static final Pattern GIFT_HEADER = Pattern.compile(" *TREE GIFT");
    private static final Pattern BONUS_HEADER = Pattern.compile(" *BONUS GIFT");
    private static final Pattern CONTRIBUTION = Pattern.compile(
            " *You helped cut (?<percentage>[\\d.]+)% of the (?<type>.*) Tree\\.");
    private static final Pattern REWARDS = Pattern.compile(" *\\+(?<count>[\\d,]+) rewards gained!(?: \\(hover\\))?");
    private static final Pattern BONUS_REWARD = Pattern.compile(" *(?<item>.*) \\((?<percentage>[\\d.]+)%\\)");
    private static final Pattern PHANTOM = Pattern.compile(" *A (?<phantom>[\\S ]+) fell from the Tree!");

    private static boolean inBlock = false;
    private static boolean inBonusGift = false;
    private static int lines = 0;
    private static String treeType = "";
    private static String percent = "";
    private static String rewardCount = "0";
    private static Style hover = null;
    private static final List<String> rareDrops = new ArrayList<>();

    /** @return true when the line belongs to a gift block and must not reach chat. */
    public static boolean handle(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.foragingCompactGifts) {
            inBlock = false;
            return false;
        }

        String clean = Foraging.strip(message.getString()).stripTrailing();
        String trimmed = clean.trim();

        if (SEPARATOR.matcher(trimmed).matches()) {
            if (inBlock) {
                inBlock = false;
                send();
            } else {
                start();
            }
            return true;
        }
        if (!inBlock) {
            // the opening bar can go missing when other mods eat it, arm on the header instead
            if (GIFT_HEADER.matcher(clean).matches()) {
                start();
                return true;
            }
            return false;
        }
        // never swallow the rest of chat if the closing bar goes missing
        if (++lines > 60) {
            inBlock = false;
            return false;
        }

        if (BONUS_HEADER.matcher(clean).matches()) {
            inBonusGift = true;
            return true;
        }

        Matcher m = CONTRIBUTION.matcher(clean);
        if (m.matches()) {
            String value = m.group("percentage");
            percent = (value.equals("100") ? "§a" : "§c") + value + "%";
            treeType = m.group("type");
            return true;
        }

        m = REWARDS.matcher(clean);
        if (m.matches()) {
            rewardCount = m.group("count");
            hover = findHover(message);
            return true;
        }

        m = PHANTOM.matcher(clean);
        if (m.matches()) {
            rareDrops.add("A wild §d" + m.group("phantom") + " §fappeared!");
            return true;
        }

        if (inBonusGift) {
            m = BONUS_REWARD.matcher(clean);
            if (m.matches()) rareDrops.add(m.group("item"));
        }
        return true;
    }

    public static void reset() {
        inBlock = false;
        inBonusGift = false;
    }

    private static void start() {
        inBlock = true;
        inBonusGift = false;
        lines = 0;
        treeType = "";
        percent = "";
        rewardCount = "0";
        hover = null;
        rareDrops.clear();
    }

    private static void send() {
        if (treeType.isEmpty()) return;
        String text = "§9" + treeType + " Tree Gift. §7You helped cut " + percent
                + " §7and gained §e" + rewardCount + " rewards§a!";
        MutableComponent line = Component.literal(text);
        if (hover != null) line.setStyle(line.getStyle().withHoverEvent(hover.getHoverEvent()));
        List<String> drops = List.copyOf(rareDrops);

        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.sendSystemMessage(line);
            for (String drop : drops) mc.player.sendSystemMessage(Component.literal("§f - " + drop));
        });
    }

    private static Style findHover(Component component) {
        if (component.getStyle().getHoverEvent() != null) return component.getStyle();
        for (Component sibling : component.getSiblings()) {
            Style found = findHover(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
