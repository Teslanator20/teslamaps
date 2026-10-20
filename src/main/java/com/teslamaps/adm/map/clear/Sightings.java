package com.teslamaps.adm.map.clear;

// Ported from ADM's clearbreakdown/Sightings.kt (no party relay: local sightings only)
import com.teslamaps.adm.map.AdmMapScan;
import com.teslamaps.player.PlayerTracker;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

public final class Sightings {
    private static final double REACH_SQ = 6.0 * 6.0;
    private static final long CLICK_MS = 1000L;
    private static final int SKULL_CHUNKS = 12;
    private static final String WITHER_ESSENCE_ID = "2865274b-3097-394e-8149-ec629c72d850";
    private static final List<String> SECRET_ITEMS = List.of(
            "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion", "Healing 8 Splash Potion",
            "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
            "Secret Dye", "Candycomb");

    private static final Set<BlockPos> essence = new HashSet<>();
    private static final Map<BlockPos, Long> clicks = new HashMap<>();
    private static int scanTick;

    private Sightings() {}

    static void reset() {
        essence.clear();
        clicks.clear();
    }

    private static boolean tracking() {
        return ClearBreakdown.active && AdmMapScan.inClear();
    }

    public static void onClick(BlockPos pos) {
        if (tracking()) clicks.put(pos.immutable(), System.currentTimeMillis());
    }

    public static void onPickup(int itemId, int playerId) {
        if (!tracking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity ie = mc.level.getEntity(itemId);
        if (!(ie instanceof ItemEntity item)) return;
        String name = item.getItem().getHoverName().getString();
        boolean secret = false;
        for (String s : SECRET_ITEMS) if (name.toLowerCase().contains(s.toLowerCase())) { secret = true; break; }
        if (!secret) return;
        Entity pe = mc.level.getEntity(playerId);
        String by = pe instanceof Player p ? p.getName().getString() : null;
        if (by != null && ClearBreakdown.roster.containsKey(by)) seen("item:" + itemId, item.getX(), item.getZ(), Set.of(by), true);
        else seen("item:" + itemId, item.getX(), item.getZ(), near(item.getX(), item.getY(), item.getZ()), false);
    }

    public static void onBlockEvent(BlockPos pos, net.minecraft.world.level.block.Block block, int b0, int b1) {
        if (b0 != 1 || b1 <= 0 || (block != Blocks.CHEST && block != Blocks.TRAPPED_CHEST)) return;
        if (tracking()) seenBlock("chest", pos.immutable());
    }

    public static void onBat(double x, double y, double z) {
        if (!tracking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Bat bat = null;
        double best = Double.MAX_VALUE;
        for (Bat b : mc.level.getEntitiesOfClass(Bat.class, new AABB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3))) {
            double d = b.distanceToSqr(x, y, z);
            if (d < best) { best = d; bat = b; }
        }
        String key = bat != null ? "bat:" + bat.getId()
                : "bat:" + Math.floorDiv((int) x, 2) + "," + Math.floorDiv((int) y, 2) + "," + Math.floorDiv((int) z, 2) + ":" + System.currentTimeMillis() / 1000;
        seen(key, x, z, near(x, y, z), false);
    }

    /** Every tick of a clear: a twentieth of the chunks around you searched for essence skulls; a gone skull is a secret. */
    static void tick() {
        if (!tracking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Iterator<BlockPos> it = essence.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!mc.level.isLoaded(p)) continue;
            if (!(mc.level.getBlockState(p).getBlock() instanceof SkullBlock)) {
                it.remove();
                seenBlock("essence", p);
            }
        }
        int cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4;
        int slice = scanTick++ % 20;
        for (int x = cx - SKULL_CHUNKS; x <= cx + SKULL_CHUNKS; x++) for (int z = cz - SKULL_CHUNKS; z <= cz + SKULL_CHUNKS; z++) {
            if (Math.floorMod(x * 7 + z, 20) != slice) continue;
            if (!(mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (!(be instanceof SkullBlockEntity skull) || skull.getOwnerProfile() == null) continue;
                if (WITHER_ESSENCE_ID.equals(String.valueOf(skull.getOwnerProfile().partialProfile().id()))) essence.add(be.getBlockPos().immutable());
            }
        }
    }

    private static void seenBlock(String kind, BlockPos pos) {
        String key = kind + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Long at = clicks.get(pos);
        boolean clicked = at != null && System.currentTimeMillis() - at <= CLICK_MS;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (clicked) seen(key, x, z, Set.of(ClearBreakdown.me()), true);
        else seen(key, x, z, near(x, y, z), false);
    }

    private static void seen(String key, double x, double z, Set<String> candidates, boolean exact) {
        if (candidates.isEmpty()) return;
        AdmMapScan.Room room = AdmMapScan.roomAt(x, z);
        if (room == null) return;
        ClearBreakdown.secrets.sighting(key, room.key(), room.displayName(), candidates, exact, System.currentTimeMillis());
    }

    private static Set<String> near(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        Set<String> out = new HashSet<>();
        if (mc.level == null) return out;
        for (String name : ClearBreakdown.roster.keySet()) {
            PlayerTracker.DungeonPlayer p = PlayerTracker.getPlayer(name).orElse(null);
            if (p == null || !p.isAlive()) continue;
            Player e = ClearBreakdown.entityOf(name);
            if (e != null && e.distanceToSqr(x, y, z) <= REACH_SQ) out.add(name);
        }
        return out;
    }
}
