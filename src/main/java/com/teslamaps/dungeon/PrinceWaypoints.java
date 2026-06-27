/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// Room-relative markers for the structure you superboom to spawn a Prince. Stored per room name
// (relative X/Z + absolute Y). Two coordinate modes per room:
//   clay   (default) = relative to the blue-terracotta clay marker (rotation-invariant) — what most rooms use.
//   corner          = room corner + core-derived rotation (puzzle-solver transform) — fallback for rooms
//                     with no blue terracotta (e.g. Supertall) where the clay scan can't anchor.
// Existing files have no mode entries, so they default to clay and stay byte-for-byte compatible.
public class PrinceWaypoints {

    private static final String MODE_KEY = "__modes__";
    private static final int MODE_CLAY = 0;
    private static final int MODE_CORNER = 1;

    // {relX, absY, relZ}
    private static final Map<String, List<int[]>> byRoom = new HashMap<>();
    private static final Map<String, Integer> roomMode = new HashMap<>();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("prince_waypoints.json");

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        byRoom.clear();
        roomMode.clear();
        // bundled defaults shipped with the mod (everyone gets these without collecting)
        try (Reader r = new java.io.InputStreamReader(
                PrinceWaypoints.class.getResourceAsStream("/assets/teslamaps/data/prince_waypoints.json"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            parse(r);
        } catch (Exception ex) {
            TeslaMaps.LOGGER.warn("[Prince] No bundled prince_waypoints.json", ex);
        }
        // user's own file overlays/overrides the bundled defaults per room
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE)) {
                parse(r);
            } catch (Exception ex) {
                TeslaMaps.LOGGER.error("[Prince] Failed to load user file", ex);
            }
        }
        TeslaMaps.LOGGER.info("[Prince] Loaded markers across {} rooms", byRoom.size());
    }

    private static void parse(Reader r) {
        JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (e.getKey().equals(MODE_KEY)) {
                for (Map.Entry<String, JsonElement> m : e.getValue().getAsJsonObject().entrySet()) {
                    roomMode.put(m.getKey(), "corner".equals(m.getValue().getAsString()) ? MODE_CORNER : MODE_CLAY);
                }
                continue;
            }
            List<int[]> list = new ArrayList<>();
            for (JsonElement el : e.getValue().getAsJsonArray()) {
                JsonArray a = el.getAsJsonArray();
                list.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
            }
            if (!list.isEmpty()) byRoom.put(e.getKey(), list); // later parse() overrides earlier per room
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, List<int[]>> e : byRoom.entrySet()) {
                JsonArray arr = new JsonArray();
                for (int[] p : e.getValue()) {
                    JsonArray a = new JsonArray();
                    a.add(p[0]); a.add(p[1]); a.add(p[2]);
                    arr.add(a);
                }
                root.add(e.getKey(), arr);
            }
            JsonObject modes = new JsonObject();
            for (Map.Entry<String, Integer> e : roomMode.entrySet()) {
                if (e.getValue() == MODE_CORNER && byRoom.containsKey(e.getKey())) modes.addProperty(e.getKey(), "corner");
            }
            if (modes.size() > 0) root.add(MODE_KEY, modes);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[Prince] Failed to save", ex);
        }
    }

    // marks the block you are looking at as a Prince structure in the current room
    public static String mark() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "§cNo player.";
        if (!DungeonManager.isInDungeon()) return "§cNot in a dungeon.";

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return "§cNo named room at your position.";

        BlockPos target = (mc.hitResult instanceof BlockHitResult bhr)
                ? bhr.getBlockPos() : mc.player.blockPosition().below();

        int mode;
        int[] rel;
        int[] clay = DungeonWaypoints.scanClayPos(room);
        if (clay != null) {
            mode = MODE_CLAY;
            rel = DungeonWaypoints.worldToRelative(clay, target.getX(), target.getZ());
        } else {
            // no terracotta (e.g. Supertall): anchor on room corner + core-derived rotation
            int rot = room.getRotation();
            if (rot < 0) return "§cNo terracotta and rotation not detected yet — walk inside the room and retry.";
            mode = MODE_CORNER;
            rel = toComp(target.getX(), target.getZ(), rot, room.getCornerX(), room.getCornerZ());
        }

        int[] entry = new int[]{rel[0], target.getY(), rel[1]};
        List<int[]> list = byRoom.computeIfAbsent(room.getName(), k -> new ArrayList<>());
        roomMode.put(room.getName(), mode);

        // toggle: remove if one already at this exact relative pos
        for (int i = 0; i < list.size(); i++) {
            int[] p = list.get(i);
            if (p[0] == entry[0] && p[1] == entry[1] && p[2] == entry[2]) {
                list.remove(i);
                if (list.isEmpty()) { byRoom.remove(room.getName()); roomMode.remove(room.getName()); }
                save();
                return "§eRemoved Prince marker in §e\"" + room.getName() + "\"§e.";
            }
        }
        list.add(entry);
        save();
        String how = mode == MODE_CORNER ? " §7(corner-mode)" : "";
        return "§aMarked Prince structure in §e\"" + room.getName() + "\"§a (" + list.size() + " here)" + how + ".";
    }

    public static String clearRoom() {
        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        if (room == null || room.getName() == null) return "§cNo named room here.";
        List<int[]> removed = byRoom.remove(room.getName());
        roomMode.remove(room.getName());
        save();
        return removed == null ? "§eNo Prince markers in \"" + room.getName() + "\"."
                : "§aCleared " + removed.size() + " Prince marker(s) in \"" + room.getName() + "\".";
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().princeESP || byRoom.isEmpty()) return;
        if (!DungeonManager.isInDungeon()) return;
        if (DungeonScore.isPrinceKilled()) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return;
        List<int[]> list = byRoom.get(room.getName());
        if (list == null || list.isEmpty()) return;

        int mode = roomMode.getOrDefault(room.getName(), MODE_CLAY);
        int[] clay = null;
        int rot = room.getRotation(), cornerX = room.getCornerX(), cornerZ = room.getCornerZ();
        if (mode == MODE_CLAY) {
            clay = DungeonWaypoints.scanClayPos(room);
            if (clay == null) return;
        } else if (rot < 0) {
            return;
        }

        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorPrinceESP);

        // box every marker; collect world positions for clustering the labels
        List<int[]> worlds = new ArrayList<>();
        for (int[] p : list) {
            int[] w = (mode == MODE_CLAY)
                    ? DungeonWaypoints.relativeToWorld(clay, p[0], p[2])
                    : fromComp(p[0], p[2], rot, cornerX, cornerZ);
            int wx = w[0], wy = p[1], wz = w[1];
            AABB box = new AABB(wx, wy, wz, wx + 1.0, wy + 1.0, wz + 1.0);
            ESPRenderer.drawFilledBox(matrices, box.inflate(0.01), color, cameraPos, true);
            worlds.add(new int[]{wx, wy, wz});
        }

        // one "Prince" label per cluster of nearby markers (greedy, seeded)
        double thresh2 = 4.0 * 4.0;
        boolean[] used = new boolean[worlds.size()];
        for (int i = 0; i < worlds.size(); i++) {
            if (used[i]) continue;
            int[] seed = worlds.get(i);
            double sx = seed[0], sy = seed[1], sz = seed[2];
            int cnt = 1;
            used[i] = true;
            for (int j = i + 1; j < worlds.size(); j++) {
                if (used[j]) continue;
                int[] o = worlds.get(j);
                double dx = o[0] - seed[0], dy = o[1] - seed[1], dz = o[2] - seed[2];
                if (dx * dx + dy * dy + dz * dz <= thresh2) {
                    used[j] = true; sx += o[0]; sy += o[1]; sz += o[2]; cnt++;
                }
            }
            Vec3 textPos = new Vec3(sx / cnt + 0.5, sy / cnt + 1.7, sz / cnt + 0.5);
            ESPRenderer.drawText(matrices, "Prince", textPos, 2.5f, cameraPos);
        }
    }

    // corner + core-derived rotation transform (same as the puzzle solvers), for the no-terracotta fallback
    private static int[] rotatePos(int x, int z, int degree) {
        return switch (((degree % 360) + 360) % 360) {
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    private static int[] fromComp(int x, int z, int rotation, int cornerX, int cornerZ) {
        int[] r = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{r[0] + cornerX, r[1] + cornerZ};
    }

    private static int[] toComp(int worldX, int worldZ, int rotation, int cornerX, int cornerZ) {
        return rotatePos(worldX - cornerX, worldZ - cornerZ, rotation);
    }
}
