package com.teslamaps.features.npc;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Ported from NEU's Navigation.java (island_warps from NEU-REPO constants/islands.json)
public final class NpcWarps {

    private record Warp(String name, String island, double x, double y, double z) {}

    private static final List<Warp> WARPS = List.of(
            new Warp("elizabeth", "Hub", -4, 79, 16),
            new Warp("castle", "Hub", -250, 130, 45),
            new Warp("crypts", "Hub", -161, 62, -106),
            new Warp("museum", "Hub", 29, 72, 1),
            new Warp("hub", "Hub", -3, 70, -70),
            new Warp("da", "Hub", 91, 75, 173),
            new Warp("wizard", "Hub", 44, 119, 93),
            new Warp("stonks", "Hub", -37, 70, -82),
            new Warp("carnival", "Hub", -87, 71, -2),
            new Warp("taylor", "Hub", 29, 73, -42),
            new Warp("nether", "Crimson Isle", -361, 80, -430),
            new Warp("kuudra", "Crimson Isle", -373, 114, -1034),
            new Warp("smold", "Crimson Isle", -283, 108, -1031),
            new Warp("deep", "Deep Caverns", 4, 157, 80),
            new Warp("gold", "Gold Mine", -8, 68, -393),
            new Warp("mines", "Dwarven Mines", -49, 200, -121),
            new Warp("forge", "Dwarven Mines", 0, 149, -68),
            new Warp("basecamp", "Dwarven Mines", 0, 128, 200),
            new Warp("barn", "The Farming Islands", 113, 71, -207),
            new Warp("desert", "The Farming Islands", 160, 77, -370),
            new Warp("trapper", "The Farming Islands", 281, 104, -547),
            new Warp("park", "The Park", -266, 79, -18),
            new Warp("jungle", "The Park", -463, 133, -128),
            new Warp("howl", "The Park", -333, 90, -55),
            new Warp("dungeon_hub", "Dungeon Hub", -31, 121, 0),
            new Warp("spider", "Spider's Den", -203, 83, -233),
            new Warp("nest", "Spider's Den", -189, 176, -310),
            new Warp("arachne", "Spider's Den", -282, 47, -198),
            new Warp("end", "The End", -503, 101, -275),
            new Warp("drag", "The End", -607, 22, -276),
            new Warp("void", "The End", -570, 7, -318),
            new Warp("crystals", "Crystal Hollows", 513, 106, 526),
            new Warp("garden", "Garden", -5, 71, 17),
            new Warp("winter", "Jerry's Workshop", -5, 76, 100),
            new Warp("rift", "The Rift", -45, 122, 69),
            new Warp("bayou", "Backwater Bayou", -13, 74, 10),
            new Warp("galatea", "Galatea", -548, 109, -22),
            new Warp("murkwater", "Galatea", -645, 90, 2),
            new Warp("lotus", "Lotus Atoll", -16, 71, 1),
            new Warp("torrhus", "Torrhus Canyon", -720, 94, 141),
            new Warp("spring", "Torrhus Canyon", -635, 161, 162)
    );

    private static String lastAttempt = null;
    private static long lastAttemptTime = 0L;
    private static String warpAgainTo = null;
    private static long warpAgainTime = 0L;
    private static ClientLevel lastLevel = null;
    private static boolean keyWasDown = false;

    private NpcWarps() {}

    public static void register() {
        com.teslamaps.registry.Feature.of("NpcWarps")
                .section("Render")
                .when(() -> TeslaMapsConfig.get().npcFinder)
                .tick(NpcWarps::tick)
                .serverChat(NpcWarps::onChat);
    }

    private static String island(String npcIsland) {
        return npcIsland.equals("Glacite Tunnels") ? "Dwarven Mines" : npcIsland;
    }

    private static Set<String> locked() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String key = SbProfile.key();
        return c.npcLockedWarps.computeIfAbsent(key == null ? "" : key, k -> new HashSet<>());
    }

    private static Warp closest(NpcData npc) {
        String isl = island(npc.island());
        Set<String> locked = locked();
        Warp best = null;
        double bestDist = 0;
        for (Warp w : WARPS) {
            if (!w.island.equals(isl) || locked.contains(w.name)) continue;
            double d = dist(w, npc.x(), npc.y(), npc.z());
            if (best == null || d < bestDist) { best = w; bestDist = d; }
        }
        return best;
    }

    private static double dist(Warp w, double x, double y, double z) {
        double dx = w.x - x, dy = w.y - y, dz = w.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Warp to use for an NPC on another island. */
    public static String warpFor(NpcData npc) {
        if (!TeslaMapsConfig.get().npcNearestWarp) return npc.warpCommand();
        Warp w = closest(npc);
        return w == null ? npc.warpCommand() : w.name;
    }

    public static void warp(String name, boolean otherIsland) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        lastAttempt = name;
        lastAttemptTime = System.currentTimeMillis();
        if (otherIsland && TeslaMapsConfig.get().npcWarpTwice) {
            warpAgainTo = name;
            warpAgainTime = lastAttemptTime;
            lastLevel = mc.level;
        }
        mc.getConnection().sendCommand("warp " + name);
    }

    /** Shift+key: nearest unlocked warp to the tracked NPC (NEU useWarpCommand). */
    private static void useWarp() {
        Minecraft mc = Minecraft.getInstance();
        NpcData npc = NpcFinder.firstTarget();
        if (npc == null || mc.player == null) return;
        Warp w = closest(npc);
        if (w == null) {
            msg("§cNo unlocked warp for " + npc.island());
            return;
        }
        boolean other = !NpcFinder.isOnTargetIsland(npc);
        if (!other && mc.player.position().distanceToSqr(npc.x(), npc.y(), npc.z()) < dist(w, npc.x(), npc.y(), npc.z())) {
            msg("§7You are already closer than the nearest warp");
            return;
        }
        warp(w.name, other);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean down = mc.screen == null && c.npcWaypointKey != -1 && KeyNames.isDown(c.npcWaypointKey);
        if (down && !keyWasDown) {
            if (shiftDown()) useWarp();
            else mc.setScreen(new com.teslamaps.screen.NpcFinderScreen(null));
        }
        keyWasDown = down;

        if (warpAgainTo != null) {
            if (System.currentTimeMillis() - warpAgainTime > 5000) warpAgainTo = null;
            else if (mc.level != lastLevel) {
                String w = warpAgainTo;
                warpAgainTo = null;
                warp(w, false);
            }
        }
    }

    /** Key over a slot (Abiphone contact, NPC item): track that NPC, Shift = also warp. */
    public static boolean onContainerKey(int key, Slot hovered) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.npcFinder || c.npcWaypointKey == -1 || key != c.npcWaypointKey) return false;
        if (hovered == null || !hovered.hasItem()) return false;
        String name = StringUtil.strip(hovered.getItem().getHoverName().getString()).trim();
        NpcData npc = null;
        for (NpcData n : NpcDatabase.getUnique()) {
            if (n.hasPosition() && n.name().equalsIgnoreCase(name)) { npc = n; break; }
        }
        if (npc == null) return false;
        List<NpcData> positions = NpcDatabase.getPositions(npc.name(), npc.island());
        NpcFinder.setTargets(positions);
        msg("§7Tracking §f" + npc.name() + " §7on §f" + npc.island());
        if (shiftDown()) {
            Minecraft.getInstance().setScreen(null);
            useWarp();
        }
        keyWasDown = true;
        return true;
    }

    private static void onChat(String raw) {
        String text = StringUtil.strip(raw).trim();
        if (text.startsWith("You may now fast travel to")) {
            locked().clear();
            TeslaMapsConfig.save();
            return;
        }
        if (!text.equals("You haven't unlocked this fast travel destination!")) return;
        if (!TeslaMapsConfig.get().npcNearestWarp) return;
        if (lastAttempt == null || System.currentTimeMillis() - lastAttemptTime > 3000) return;
        locked().add(lastAttempt);
        TeslaMapsConfig.save();
        msg("§eWarp §f" + lastAttempt + " §emarked as not unlocked. Navigate again for the next one. §7(/tmap npc clearwarps)");
        lastAttempt = null;
        warpAgainTo = null;
    }

    public static void clearLocked() {
        locked().clear();
        TeslaMapsConfig.save();
    }

    private static boolean shiftDown() {
        return KeyNames.isDown(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) || KeyNames.isDown(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
