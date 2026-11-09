package com.teslamaps.features.theend;

// Ported from Skyblocker's EnderNodes.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Finds Ender Nodes from the portal + witch particles around them and marks the block. */
public final class EnderNodeFinder {

    private static final Direction[] DIRS = Direction.values();

    private static final class Node {
        final BlockPos pos;
        final int[] portal = new int[6], witch = new int[6];
        long confirmed;

        Node(BlockPos pos) { this.pos = pos; }
    }

    private static final Map<BlockPos, Node> nodes = new HashMap<>();
    private static List<AABB> boxes = List.of();
    private static boolean inEnd;
    private static int ticks;
    private static Object lastLevel;

    private EnderNodeFinder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnderNodeFinder").section("The End").when(() -> cfg().enderNodeFinder)
                .tick(EnderNodeFinder::tick)
                .world(EnderNodeFinder::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Ender Node Finder", () -> c.enderNodeFinder, v -> c.enderNodeFinder = v)
                            .desc("Marks Ender Nodes found by their particles. Gone 5 s after the last sighting or when you mine it.")
                            .tags("ender", "node", "end", "waypoint");
                    b.color("  Node Color", () -> c.enderNodeFinderColor, v -> c.enderNodeFinderColor = v);
                });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (inEnd) nodes.remove(pos);
            return InteractionResult.PASS;
        });
    }

    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!inEnd || !cfg().enderNodeFinder || !Minecraft.getInstance().isSameThread()) return;
        ParticleType<?> type = packet.particle().getType();
        boolean portal = type == ParticleTypes.PORTAL;
        if (!portal && type != ParticleTypes.WITCH) return;
        double x = packet.x(), y = packet.y(), z = packet.z();
        double xf = Mth.positiveModulo(x, 1), yf = Mth.positiveModulo(y, 1), zf = Mth.positiveModulo(z, 1);
        BlockPos pos;
        Direction dir;
        if (yf == 0.25) { pos = BlockPos.containing(x, y - 1, z); dir = Direction.UP; }
        else if (yf == 0.75) { pos = BlockPos.containing(x, y + 1, z); dir = Direction.DOWN; }
        else if (xf == 0.25) { pos = BlockPos.containing(x - 1, y, z); dir = Direction.EAST; }
        else if (xf == 0.75) { pos = BlockPos.containing(x + 1, y, z); dir = Direction.WEST; }
        else if (zf == 0.25) { pos = BlockPos.containing(x, y, z - 1); dir = Direction.SOUTH; }
        else if (zf == 0.75) { pos = BlockPos.containing(x, y, z + 1); dir = Direction.NORTH; }
        else return;
        if (nodes.size() > 256 && !nodes.containsKey(pos)) return;
        Node n = nodes.computeIfAbsent(pos, Node::new);
        if (portal) n.portal[dir.ordinal()]++;
        else n.witch[dir.ordinal()]++;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            nodes.clear();
            boxes = List.of();
        }
        if (++ticks % 20 != 0) return;
        inEnd = SkyblockUtils.isInArea("The End");
        if (!inEnd) {
            nodes.clear();
            boxes = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        List<AABB> out = new ArrayList<>();
        for (Iterator<Node> it = nodes.values().iterator(); it.hasNext(); ) {
            Node n = it.next();
            if (n.confirmed + 2000 <= now && complete(mc, n)) {
                n.confirmed = now;
                java.util.Arrays.fill(n.portal, 0);
                java.util.Arrays.fill(n.witch, 0);
            }
            if (n.confirmed + 5000 > now) out.add(new AABB(n.pos));
            else if (n.confirmed != 0) it.remove();
        }
        if (nodes.size() > 200) nodes.values().removeIf(n -> n.confirmed == 0);
        boxes = out;
    }

    private static boolean complete(Minecraft mc, Node n) {
        for (Direction d : DIRS) {
            int i = d.ordinal();
            if (n.portal[i] >= 5 && n.witch[i] >= 5) continue;
            if (!mc.level.getBlockState(n.pos.relative(d)).isAir()) continue;
            return false;
        }
        return true;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> b = boxes;
        if (b.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().enderNodeFinderColor);
        int fill = (color & 0x00FFFFFF) | 0x50000000;
        for (AABB box : b) {
            ESPRenderer.drawFilledBox(pose, box, fill, cam, true);
            ESPRenderer.drawBoxOutline(pose, box, color | 0xFF000000, 2f, cam, true);
        }
    }
}
