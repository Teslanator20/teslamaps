package com.teslamaps.features.foraging;

// Ported from Skyblocker's FloorDrops.java and GanKura's FloorDropHandler.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.safari.SafariData;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Floor drops on Galatea, Torrhus Canyon and the Critter Safari: a block holding exactly 3 string item displays. */
public final class FloorDrops {

    private static final int DISPLAYS = 3;
    private static final long RECHECK_MS = 2000L;
    private static final long EXPIRE_MS = 5000L;
    private static final long DISMISS_MAX_MS = 30_000L;
    private static final int MAX_PENDING = 256;

    private static final Queue<BlockPos> pending = new ConcurrentLinkedQueue<>();
    private static final Map<BlockPos, Long> nodes = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> dismissed = new ConcurrentHashMap<>();
    private static volatile boolean active;
    private static Object lastLevel;

    private FloorDrops() {}

    public static void register() {
        Feature.of("FloorDrops").section("Foraging").when(() -> cfg().floorDrops)
                .tick(FloorDrops::tick)
                .world(FloorDrops::render);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (active) dismiss(pos);
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (active) dismiss(hit.getBlockPos());
            return InteractionResult.PASS;
        });
    }

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    /** Only note the position, the world is checked in the tick. */
    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (!active || !cfg().floorDrops || packet.getParticle().getType() != ParticleTypes.HAPPY_VILLAGER) return;
        if (pending.size() >= MAX_PENDING) return;
        pending.add(BlockPos.containing(packet.getX(), packet.getY() - 1, packet.getZ()));
    }

    private static boolean onIsland() {
        return Foraging.onForagingIsland() || SafariData.inSafari();
    }

    private static void reset() {
        pending.clear();
        nodes.clear();
        dismissed.clear();
    }

    private static void dismiss(BlockPos pos) {
        if (nodes.remove(pos) == null) return;
        dismissed.put(pos.immutable(), System.currentTimeMillis());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        active = onIsland();
        if (!active) {
            if (!pending.isEmpty() || !nodes.isEmpty()) reset();
            return;
        }
        long now = System.currentTimeMillis();
        SafariData.Biome here = cfg().floorDropsSafariBiome && SafariData.inSafari() ? SafariData.currentBiome() : null;

        BlockPos pos;
        while ((pos = pending.poll()) != null) {
            if (nodes.containsKey(pos) || dismissed.containsKey(pos)) continue;
            if (here != null && SafariData.biomeAt(pos) != here) continue;
            if (isFloorDrop(mc, pos)) nodes.put(pos, now);
        }

        Iterator<Map.Entry<BlockPos, Long>> it = nodes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (here != null && SafariData.biomeAt(e.getKey()) != here) {
                it.remove();
                continue;
            }
            long last = e.getValue();
            if (last + RECHECK_MS > now) continue;
            if (isFloorDrop(mc, e.getKey())) e.setValue(now);
            else if (last + EXPIRE_MS <= now) it.remove();
        }

        dismissed.entrySet().removeIf(e -> e.getValue() + DISMISS_MAX_MS <= now || !isFloorDrop(mc, e.getKey()));
    }

    private static boolean isFloorDrop(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        AABB box = AABB.ofSize(Vec3.atCenterOf(pos), 1.0, 1.0, 1.0);
        int strings = 0;
        for (Display.ItemDisplay d : mc.level.getEntitiesOfClass(Display.ItemDisplay.class, box)) {
            Display.ItemDisplay.ItemRenderState state = d.itemRenderState();
            if (state == null) continue;
            ItemStack stack = state.itemStack();
            if (!stack.isEmpty() && stack.getItem() == Items.STRING) strings++;
        }
        return strings == DISPLAYS;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (nodes.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().floorDropsColor);
        for (BlockPos p : nodes.keySet()) {
            AABB box = new AABB(p);
            ESPRenderer.drawFilledBox(pose, box, color & 0x00FFFFFF, cam, false);
            ESPRenderer.drawBoxOutline(pose, box, color | 0xFF000000, 2f, cam, false);
        }
    }
}
