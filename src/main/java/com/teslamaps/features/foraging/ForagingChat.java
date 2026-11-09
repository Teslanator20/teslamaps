package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Chat side of the foraging features: gift compacting, sweep details, rotating perk messages. */
public class ForagingChat {

    /** @return true when the message must not reach chat. */
    public static boolean shouldHide(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String raw = message.getString();
        String clean = Foraging.strip(raw);

        ForagingTracker.onChat(message, clean);
        if (StarlynCompact.handle(clean)) return true;

        spawnTitles(c, clean.trim());
        if (c.foragingTreeMobTitle) treeMobTitle(clean);
        if (c.sweepDetailsHud && Foraging.whereTreesGrow() && SweepDetailsHud.handle(clean)) return true;

        if (TreeGiftCompact.handle(message)) return true;

        handleRotatingPerk(c, clean);
        return false;
    }

    private static final Pattern TREE_MOB = Pattern.compile("A (.+?) fell from the Tree!");

    private static void treeMobTitle(String clean) {
        if (!clean.contains("fell from the Tree")) return;
        Matcher m = TREE_MOB.matcher(clean);
        if (!m.find()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.gui.hud.setTimes(5, 40, 10);
        mc.gui.hud.setSubtitle(Component.literal("§eFell from the Tree!"));
        mc.gui.hud.setTitle(Component.literal("§c§l" + m.group(1).trim()));
    }

    private static final Pattern BEEHEEMOTH = Pattern.compile("A Beeheemoth has spawned at (.+?)!");

    private static void spawnTitles(TeslaMapsConfig c, String clean) {
        String title, sub;
        if (c.beeheemothTitle && clean.contains("A Beeheemoth has spawned at")) {
            Matcher m = BEEHEEMOTH.matcher(clean);
            if (!m.find()) return;
            title = "§6§lBEEHEEMOTH!";
            sub = "§e" + m.group(1).trim();
        } else if (c.macawTitle && clean.contains("Two Macaws were attracted to the Birdfeeder!")) {
            title = "§c§lMACAWS ATTRACTED!";
            sub = "§eForest Biome";
        } else {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.gui.hud.setTimes(5, 70, 20);
            mc.gui.hud.setSubtitle(Component.literal(sub));
            mc.gui.hud.setTitle(Component.literal(title));
        });
    }

    /** Reads the daily Lottery/Beekeeper effect out of chat. */
    private static void handleRotatingPerk(TeslaMapsConfig c, String clean) {
        Matcher m = HotfPerks.ROTATING_LINE.matcher(clean.trim());
        if (!m.matches()) return;
        String effect = m.group("perk");

        String lottery = HotfPerks.matchLottery(effect);
        if (lottery != null) c.hotfLotteryPerk = lottery;
        String beekeeper = lottery == null ? HotfPerks.matchBeekeeper(effect) : null;
        if (beekeeper != null) c.hotfBeekeeperPerk = beekeeper;
    }
}
