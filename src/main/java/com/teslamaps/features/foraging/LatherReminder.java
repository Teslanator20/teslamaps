package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Tracks the 12 minute Honeycomb lather per tree and pings when one runs out. */
public class LatherReminder {

    public static final long DURATION_MS = 12 * 60 * 1000L;

    public enum Tree {
        HELIX("Helix", "§d"),
        FIG("Fig", "§e"),
        MANGROVE("Mangrove", "§a");

        public final String name;
        public final String color;

        Tree(String name, String color) {
            this.name = name;
            this.color = color;
        }
    }

    private static final Pattern LATHERED = Pattern.compile("You lathered the (?<tree>[\\S ]+) with Honeycomb!");

    private static final long[] endsAt = new long[Tree.values().length];

    public static void onChatMessage(String text) {
        Matcher m = LATHERED.matcher(Foraging.strip(text).trim());
        if (!m.matches()) return;

        Tree tree = byName(m.group("tree"));
        if (tree == null) return;
        endsAt[tree.ordinal()] = System.currentTimeMillis() + DURATION_MS;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = TeslaMapsConfig.get();

        for (Tree tree : Tree.values()) {
            long end = endsAt[tree.ordinal()];
            if (end == 0 || now < end) continue;
            endsAt[tree.ordinal()] = 0;
            if (c.latherReminder) alert(c, tree);
        }
    }

    private static void alert(TeslaMapsConfig c, Tree tree) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.sendSystemMessage(Component.literal(
                "§a[TeslaMaps] §eThe " + tree.color + tree.name + " Tree §elather ran out - honeycomb it again."));
        if (c.latherReminderTitle) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§6Lather the " + tree.name + " Tree!"));
        }
        if (c.latherReminderVolume > 0f) {
            com.teslamaps.utils.LoudSound.play(
                    com.teslamaps.utils.SoundOptions.resolve(c.latherReminderSound),
                    c.latherReminderVolume, 1.0f);
        }
    }

    /** @return seconds left, or -1 when that tree is not lathered. */
    public static long secondsLeft(Tree tree) {
        long end = endsAt[tree.ordinal()];
        if (end == 0) return -1;
        return Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
    }

    public static boolean anyRunning() {
        for (Tree tree : Tree.values()) {
            if (endsAt[tree.ordinal()] != 0) return true;
        }
        return false;
    }

    public static void reset() {
        java.util.Arrays.fill(endsAt, 0);
    }

    private static Tree byName(String raw) {
        String name = raw.toLowerCase();
        for (Tree tree : Tree.values()) {
            if (name.contains(tree.name.toLowerCase())) return tree;
        }
        return null;
    }
}
