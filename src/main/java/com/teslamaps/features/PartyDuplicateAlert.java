package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartyDuplicateAlert {

    // scoreboard line in the entrance looks like "[M] playername [Lvl42]"
    private static final Pattern CLASS_LINE = Pattern.compile("\\[([MBHAT])]\\s+([A-Za-z0-9_]+)");
    private static final Map<String, String> CLASS_NAMES = Map.of(
            "M", "Mage", "B", "Berserk", "H", "Healer", "A", "Archer", "T", "Tank");

    private static long firedForStart = -1; // dungeon start time we already alerted for (fire once per run)
    private static int tickCounter = 0;

    // Devonian's PartyDuplicateAlert: class picks from chat, checked once at the 4 second countdown
    private static final Pattern SELECTED = Pattern.compile("^(\\w{1,16}) selected the (Healer|Tank|Mage|Berserk|Archer) Class!$");
    private static final Map<String, String> COLORS = Map.of(
            "Mage", "§b", "Berserk", "§c", "Healer", "§d", "Archer", "§6", "Tank", "§a");
    private static final Map<String, String> picks = new LinkedHashMap<>();
    private static boolean pending = false;
    private static Object pickLevel;

    public static void onChatMessage(String message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.partyDuplicateAlert || !c.partyDuplicatePreStart) return;
        String s = com.teslamaps.utils.StringUtil.stripTrim(message);
        if (s.equals("Starting in 4 seconds.")) { pending = true; return; }
        Matcher m = SELECTED.matcher(s);
        if (m.matches()) {
            resetOnNewWorld();
            picks.put(m.group(1), m.group(2));
        }
    }

    private static void resetOnNewWorld() {
        Object level = Minecraft.getInstance().level;
        if (level != pickLevel) { picks.clear(); pending = false; pickLevel = level; }
    }

    private static void checkPreStart() {
        resetOnNewWorld();
        if (!pending) return;
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        java.util.Set<String> inWorld = new java.util.HashSet<>();
        for (var p : mc.level.players()) inWorld.add(p.getGameProfile().name());
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = CLASS_LINE.matcher(ScoreboardUtils.cleanLine(line));
            if (m.find() && inWorld.contains(m.group(2))) picks.put(m.group(2), CLASS_NAMES.get(m.group(1)));
        }
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : picks.entrySet()) byClass.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        StringBuilder title = new StringBuilder("§cDupe");
        List<String> plain = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byClass.entrySet()) {
            if (e.getValue().size() < 2) continue;
            title.append(' ').append(COLORS.getOrDefault(e.getKey(), "§f")).append(e.getKey()).append(" §c").append(String.join(" ", e.getValue()));
            plain.add(e.getKey() + " " + String.join(" ", e.getValue()));
        }
        if (plain.isEmpty()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        mc.gui.setTimes(0, 40, 5);
        mc.gui.setTitle(Component.literal(title.toString()));
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + title));
        if (c.partyDuplicateSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        if (c.partyDuplicateMessage) Announce.party("Dupe " + String.join(" | ", plain));
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().partyDuplicateAlert) return;
        if (TeslaMapsConfig.get().partyDuplicatePreStart) { checkPreStart(); return; }
        if (!com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;
        long start = com.teslamaps.dungeon.DungeonManager.getDungeonStartTime();
        if (start == firedForStart) return; // already alerted this run (survives state/scoreboard flicker)
        if (++tickCounter % 20 != 0) return; // throttle to ~1/s
        checkDuplicates(start);
    }

    private static void checkDuplicates(long start) {
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = CLASS_LINE.matcher(ScoreboardUtils.cleanLine(line));
            if (!m.find()) continue;
            String cls = CLASS_NAMES.get(m.group(1));
            byClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(m.group(2));
        }
        if (byClass.isEmpty()) return;

        List<String> dupes = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byClass.entrySet()) {
            if (e.getValue().size() > 1) dupes.add(e.getKey() + ": " + String.join(", ", e.getValue()));
        }

        // keep re-checking until a dupe appears (classes can change pre-start)
        if (dupes.isEmpty()) return;
        firedForStart = start;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §c§lDUPLICATE CLASSES! §7" + String.join(" §8| §7", dupes)));
        if (c.partyDuplicateSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        if (c.partyDuplicateMessage && mc.getConnection() != null) {
            com.teslamaps.features.ChatQueue.command("pc Duplicate classes: " + String.join(" | ", dupes));
        }
    }

    public static void reset() { firedForStart = -1; }
}
