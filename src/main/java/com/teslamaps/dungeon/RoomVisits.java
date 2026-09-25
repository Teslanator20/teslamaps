package com.teslamaps.dungeon;

// Concept from IllegalMap's VisitedCommand.js
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Time each party member spent per room this run. */
public final class RoomVisits {

    private static final Map<String, Map<String, Long>> ms = new LinkedHashMap<>();
    private static ClientLevel lastLevel;
    private static long lastSample = 0;
    private static int counter = 0;

    private RoomVisits() {}

    public static void register() {
        Feature.of("RoomVisits").section("Score & Splits").when(() -> TeslaMapsConfig.get().visitedTracking)
                .tick(RoomVisits::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            ms.clear();
            lastSample = 0;
        }
        if (++counter % 5 != 0) return;
        long now = System.currentTimeMillis();
        long dt = lastSample == 0 ? 0 : Math.min(1000, now - lastSample);
        lastSample = now;
        if (dt == 0 || !DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;

        Set<String> party = new HashSet<>();
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) party.add(p.getName());
        party.add(mc.getUser().getName());
        for (Player p : mc.level.players()) {
            String name = p.getName().getString();
            if (!party.contains(name)) continue;
            int[] grid = ComponentGrid.worldToGrid(p.getX(), p.getZ());
            if (grid == null) continue;
            DungeonRoom room = DungeonManager.getRoomAt(grid[0], grid[1]);
            if (room == null || room.getName() == null) continue;
            ms.computeIfAbsent(room.getName(), k -> new LinkedHashMap<>()).merge(name, dt, Long::sum);
        }
    }

    public static List<String> rooms() {
        return new ArrayList<>(ms.keySet());
    }

    public static String query(String prefix) {
        if (!TeslaMapsConfig.get().visitedTracking)
            return "§cTurn on \"Track Room Time Per Player\" (Score & Splits) first.";
        if (ms.isEmpty()) return "§eNo room times recorded this run.";
        String p = prefix.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return "§b[Visited] §7Rooms: §f" + String.join("§7, §f", ms.keySet());
        String match = null;
        for (String r : ms.keySet()) {
            if (r.equalsIgnoreCase(p)) { match = r; break; }
            if (match == null && r.toLowerCase(Locale.ROOT).startsWith(p)) match = r;
        }
        if (match == null) return "§cNobody was in a room starting with \"" + prefix.trim() + "\".";
        StringBuilder sb = new StringBuilder("§b[Visited] §e").append(match).append("§7:");
        ms.get(match).entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> sb.append("\n §f").append(e.getKey()).append(" §a")
                        .append(String.format(Locale.ROOT, "%.1fs", e.getValue() / 1000.0)));
        return sb.toString();
    }
}
