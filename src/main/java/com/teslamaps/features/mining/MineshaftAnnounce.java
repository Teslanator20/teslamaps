package com.teslamaps.features.mining;

// After SkyHanni's MineshaftDetection.kt and SkyOcean's MineshaftAnnouncement.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the mineshaft type off the scoreboard on entry and posts it with the corpse count. */
public final class MineshaftAnnounce {

    private static final Pattern ID = Pattern.compile("\\b([A-Z]{4})_?([12CL])\\b");
    private static final Map<String, String[]> TYPES = Map.ofEntries(
            Map.entry("TOPA", new String[]{"Topaz", "§e", "2"}),
            Map.entry("SAPP", new String[]{"Sapphire", "§9", "2"}),
            Map.entry("AMET", new String[]{"Amethyst", "§5", "2"}),
            Map.entry("AMBE", new String[]{"Amber", "§6", "2"}),
            Map.entry("JADE", new String[]{"Jade", "§a", "2"}),
            Map.entry("RUBY", new String[]{"Ruby", "§c", "2"}),
            Map.entry("ONYX", new String[]{"Onyx", "§8", "2"}),
            Map.entry("AQUA", new String[]{"Aquamarine", "§3", "2"}),
            Map.entry("CITR", new String[]{"Citrine", "§e", "2"}),
            Map.entry("PERI", new String[]{"Peridot", "§2", "2"}),
            Map.entry("JASP", new String[]{"Jasper", "§d", ""}),
            Map.entry("OPAL", new String[]{"Opal", "§f", ""}),
            Map.entry("TITA", new String[]{"Titanium", "§7", ""}),
            Map.entry("UMBE", new String[]{"Umber", "§6", ""}),
            Map.entry("TUNG", new String[]{"Tungsten", "§8", ""}),
            Map.entry("FAIR", new String[]{"Vanguard", "§b", ""}),
            Map.entry("LITT", new String[]{"Littlefoot's Den", "§b", ""}));
    // Lapis, Tungsten, Umber, Vanguard
    private static final String[] CORPSE_HELMETS = {"LAPIS_ARMOR_HELMET", "MINERAL_HELMET", "ARMOR_OF_YOG_HELMET", "VANGUARD_HELMET"};
    private static final String[] CORPSE_CODES = {"§9L", "§7T", "§6U", "§fV"};
    private static final String[] KEYS = {null, "§7Tungsten Key", "§6Umber Key", "§fSkeleton Key"};

    private static Object lastLevel;
    private static String type, color;
    private static int wait, ticks;
    private static boolean done;
    private static String echo;
    private static long echoAt;

    private MineshaftAnnounce() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MineshaftAnnounce").section("Mining").when(() -> cfg().mineshaftAnnounce)
                .tick(MineshaftAnnounce::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mineshaft Type Announce", () -> c.mineshaftAnnounce, v -> c.mineshaftAnnounce = v)
                            .desc("On entering a Glacite Mineshaft: \"Mineshaft Entered | Topaz 1 | 2T, 1U\" with the type from the scoreboard and the corpses per type.")
                            .tags("mineshaft", "type", "jasper", "vanguard", "corpse", "party");
                    b.toggle("  Show As Title", () -> c.mineshaftAnnounceTitle, v -> c.mineshaftAnnounceTitle = v);
                    b.toggle("    Keys As Subtitle", () -> c.mineshaftAnnounceKeys, v -> c.mineshaftAnnounceKeys = v)
                            .desc("Which corpse keys the shaft needs");
                    b.toggle("  Since-Last Stats", () -> c.mineshaftAnnounceStats, v -> c.mineshaftAnnounceStats = v)
                            .desc("\"It took N mineshafts and X to get a Jasper Crystal mineshaft\"");
                    b.channel("  Announce To", () -> c.mineshaftAnnounceChannel, v -> c.mineshaftAnnounceChannel = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            type = null;
            done = false;
            wait = 0;
        }
        if (done || ++ticks % 5 != 0) return;
        if (!SkyblockUtils.isInArea("Mineshaft")) return;
        if (type == null) {
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                Matcher m = ID.matcher(ScoreboardUtils.cleanLine(line));
                while (m.find()) {
                    String[] t = TYPES.get(m.group(1));
                    if (t == null) continue;
                    String v = m.group(2);
                    type = t[0] + (v.equals("C") ? " Crystal" : !t[2].isEmpty() ? " " + v : "");
                    color = t[1];
                    wait = 0;
                    break;
                }
                if (type != null) break;
            }
            return;
        }
        // corpses load a moment after the shaft
        if (++wait < 8) return;
        done = true;
        announce(mc);
    }

    private static void announce(Minecraft mc) {
        TeslaMapsConfig c = cfg();
        int[] counts = new int[CORPSE_HELMETS.length];
        for (ArmorStand as : EntityCache.armorStands()) {
            if (as.isRemoved() || as.hasCustomName()) continue;
            String id = ItemUtil.skyblockId(as.getItemBySlot(EquipmentSlot.HEAD));
            for (int i = 0; i < CORPSE_HELMETS.length; i++) if (CORPSE_HELMETS[i].equals(id)) counts[i]++;
        }
        StringBuilder corpses = new StringBuilder();
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            if (corpses.length() > 0) corpses.append("§7, ");
            corpses.append("§f").append(counts[i]).append(CORPSE_CODES[i]);
            if (KEYS[i] != null) {
                if (keys.length() > 0) keys.append("§7, ");
                keys.append("§f").append(counts[i]).append("x ").append(KEYS[i]);
            }
        }
        String line = "§7Mineshaft Entered §8| " + color + type + (corpses.length() > 0 ? " §8| " + corpses : "");

        long now = System.currentTimeMillis();
        int since = c.mineshaftSinceType.getOrDefault(type, 0);
        Long last = c.mineshaftLastType.get(type);
        for (String[] t : TYPES.values()) {
            for (String v : t[2].isEmpty() ? new String[]{"", " Crystal"} : new String[]{" 1", " 2", " Crystal"}) {
                String n = t[0] + v;
                if (!n.equals(type)) c.mineshaftSinceType.merge(n, 1, Integer::sum);
            }
        }
        c.mineshaftSinceType.put(type, 0);
        c.mineshaftLastType.put(type, now);
        TeslaMapsConfig.save();

        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + line));
        if (c.mineshaftAnnounceStats && last != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §aIt took §e" + time(now - last) + " §aand §e" + since
                    + " §amineshaft" + (since == 1 ? "" : "s") + " entered to get a " + color + type + " §amineshaft."));
        }
        if (c.mineshaftAnnounceTitle) {
            mc.gui.setTimes(10, 60, 10);
            mc.gui.setTitle(Component.literal(color + type));
            mc.gui.setSubtitle(Component.literal(c.mineshaftAnnounceKeys && keys.length() > 0 ? keys.toString() : ""));
        }
        if (!Announce.NOBODY.equals(c.mineshaftAnnounceChannel)) {
            echo = StringUtil.strip(line);
            echoAt = now;
            Announce.send(c.mineshaftAnnounceChannel, echo);
        }
    }

    /** Hides the party/all echo of our own announcement once. */
    public static boolean hideEcho(String stripped) {
        if (echo == null) return false;
        if (System.currentTimeMillis() - echoAt > 15_000) { echo = null; return false; }
        if (!stripped.endsWith(": " + echo)) return false;
        echo = null;
        return true;
    }

    private static String time(long ms) {
        long s = ms / 1000;
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        if (s < 86400) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        return (s / 86400) + "d " + (s % 86400 / 3600) + "h";
    }
}
