package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// open wither doors (FunnyMapExtras MapUpdate), scan chat info (FunnyMap DungeonScan.scan),
// "Blaze Done" from party chat (FunnyMapExtras RunInformation)
public class MapExtras {

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static int ticks, scanTicks;
    private static boolean scanInfoSent;
    private static long allLoadedSince;
    private static Object scanLevel;

    public static void register() {
        Feature.of("OpenDoors").section("Map").when(() -> cfg().mapColorOpenDoors && DungeonManager.isInDungeon())
                .tick(() -> { if (ticks++ % 5 == 0) DoorScanner.tickOpened(); });
        Feature.of("ScanChatInfo").section("Map").when(() -> cfg().mapScanChatInfo)
                .tick(MapExtras::tickScanInfo);
        Feature.of("BlazeDoneChat").section("Map").when(() -> cfg().mapBlazeDoneFromChat && DungeonManager.isInDungeon())
                .chat(MapExtras::onChat);
    }

    private static void onChat(String raw) {
        if (raw.indexOf("Party") < 0) return;
        String s = StringUtil.stripTrim(raw);
        if (!s.startsWith("Party > ")) return;
        int colon = s.indexOf(": ");
        if (colon < 0) return;
        String msg = s.substring(colon + 2).trim().toLowerCase(Locale.ROOT);
        if (!msg.startsWith("blaze done") && !msg.equals("blaze puzzle solved!")) return;
        DungeonScore.markBlazeDone();
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getType() != RoomType.PUZZLE || room.getName() == null || !room.getName().contains("Blaze")) continue;
            room.setExplored(true);
            if (room.getCheckmarkState() != CheckmarkState.GREEN) room.setCheckmarkState(CheckmarkState.GREEN);
        }
    }

    private static void tickScanInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonManager.isInDungeon() || mc.level != scanLevel) {
            scanLevel = mc.level;
            scanInfoSent = false;
            allLoadedSince = 0;
            return;
        }
        if (scanInfoSent || scanTicks++ % 10 != 0 || mc.level == null || DungeonManager.isInBossArea()) return;
        if (LegitMode.isActive()) return;

        for (int gx = 0; gx < ComponentGrid.GRID_SIZE; gx++) {
            for (int gz = 0; gz < ComponentGrid.GRID_SIZE; gz++) {
                int[] c = ComponentGrid.gridToWorld(gx, gz);
                if (!CoreHasher.isPositionLoaded(mc.level, c[0], c[1])) { allLoadedSince = 0; return; }
            }
        }
        long now = System.currentTimeMillis();
        if (allLoadedSince == 0) allLoadedSince = now;

        boolean allKnown = !DungeonManager.getGrid().getAllRooms().isEmpty();
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (!room.isIdentified()) { allKnown = false; break; }
        }
        // a room missing from the database must not block the message forever
        if (now - allLoadedSince < (allKnown ? 1500 : 10_000)) return;
        scanInfoSent = true;
        DoorScanner.scanAllDoors();
        send(mc);
    }

    private static void send(Minecraft mc) {
        if (mc.player == null) return;
        List<String> puzzles = new ArrayList<>();
        String trap = "?";
        int secrets = 0;
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getSecrets() > 0) secrets += room.getSecrets();
            if (room.getType() == RoomType.PUZZLE && room.getName() != null) puzzles.add(room.getName());
            else if (room.getType() == RoomType.TRAP && room.getName() != null) trap = room.getName();
        }
        long ms = System.currentTimeMillis() - DungeonManager.getDungeonStartTime();
        double pct = DungeonScore.sPlusSecretPercent(DungeonManager.getCurrentFloor());
        int minSecrets = (int) Math.ceil(secrets * pct / 100.0);

        StringBuilder sb = new StringBuilder("§aScan Finished! §7(" + (ms / 100) / 10.0 + "s)");
        sb.append("\n§aPuzzles (§c").append(puzzles.size()).append("§a):");
        for (String p : puzzles) sb.append("\n§b- §d").append(p);
        sb.append("\n§6Trap: §a").append(trap);
        sb.append("\n§8Wither Doors: §7").append(DoorScanner.countType(DoorType.WITHER));
        sb.append("\n§7Total Crypts: §6").append(DungeonManager.getTotalCrypts());
        sb.append("\n§7Total Secrets: §b").append(secrets);
        sb.append("\n§7Minimum Secrets (S+): §e").append(minSecrets);
        mc.player.sendSystemMessage(Component.literal(sb.toString()));
    }
}
