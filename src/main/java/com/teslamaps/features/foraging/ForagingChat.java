package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Chat side of the foraging features: gift compacting, sweep details, rotating perk messages. */
public class ForagingChat {

    private static final Pattern SWEEP_HEADER = Pattern.compile("\\s*Sweep Details: [\\d,.]+\\s*\\S?\\s*Sweep\\s*");
    private static final Pattern SWEEP_TOUGHNESS =
            Pattern.compile("\\s*[\\S ]+ Tree Toughness: [\\d,.]+ [\\d,.]+ Logs\\s*");
    private static final Pattern SWEEP_PENALTY =
            Pattern.compile("\\s*[\\S ]+: -[\\d,.]+% Sweep [\\d,.]+ Logs.*");

    /** @return true when the message must not reach chat. */
    public static boolean shouldHide(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String raw = message.getString();
        String clean = Foraging.strip(raw);

        if (c.foragingTreeMobTitle) treeMobTitle(clean);
        if (c.sweepDetailsHud && Foraging.whereTreesGrow() && SweepDetailsHud.handle(clean)) return true;

        if (TreeGiftCompact.handle(message)) return true;

        if (c.foragingCompactSweep && Foraging.whereTreesGrow() && CompactSweep.handle(message, clean)) return true;

        if (c.foragingHideSweepDetails && Foraging.whereTreesGrow()
                && (SWEEP_HEADER.matcher(clean).matches()
                || SWEEP_TOUGHNESS.matcher(clean).matches()
                || SWEEP_PENALTY.matcher(clean).matches())) {
            return true;
        }

        return handleRotatingPerk(c, clean);
    }

    private static final Pattern TREE_MOB = Pattern.compile("A (.+?) fell from the Tree!");

    private static void treeMobTitle(String clean) {
        if (!clean.contains("fell from the Tree")) return;
        Matcher m = TREE_MOB.matcher(clean);
        if (!m.find()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.gui.setTimes(5, 40, 10);
        mc.gui.setSubtitle(Component.literal("§eFell from the Tree!"));
        mc.gui.setTitle(Component.literal("§c§l" + m.group(1).trim()));
    }

    /**
     * Reads the daily Lottery/Beekeeper effect out of chat and hides those lines away from
     * the foraging islands, where the buff does nothing.
     */
    private static boolean handleRotatingPerk(TeslaMapsConfig c, String clean) {
        Matcher m = HotfPerks.ROTATING_LINE.matcher(clean.trim());
        if (!m.matches()) return false;
        String effect = m.group("perk");

        String lottery = HotfPerks.matchLottery(effect);
        if (lottery != null) c.hotfLotteryPerk = lottery;
        String beekeeper = lottery == null ? HotfPerks.matchBeekeeper(effect) : null;
        if (beekeeper != null) c.hotfBeekeeperPerk = beekeeper;

        if (lottery == null && beekeeper == null) return false;
        return c.foragingHideLottery && !Foraging.onForagingIsland();
    }
}
