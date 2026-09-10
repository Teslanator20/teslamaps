package com.teslamaps.features;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// ESP on user-defined heads, matched on the skin texture hash. Covers both skull
// blocks and heads worn by armor stands (how Hypixel places most decoration heads).
public class HeadESP {

    // Golden Dragon pet skin (lvl 1-99), which is what the Crystal Hollows nest eggs use.
    // Hash from NEU's GOLDEN_DRAGON;4 item.
    private static final String[] GOLDEN_DRAGON_EGG = {
        "2e9f9b1fc014166cb46a093e5349b2bf6edd201b680d62e48dbf3af9b0459116",
    };

    private record BlockHit(BlockPos pos, String name, int color) {}
    private record StandHit(ArmorStand stand, String name, int color) {}

    private static final List<BlockHit> blocks = new ArrayList<>();
    private static final List<StandHit> stands = new ArrayList<>();
    private static int ticks = 0;

    private static boolean enabled() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.section("ESP")) return false;
        if (cfg.headGoldenDragonEgg) return true;
        return cfg.headESP && cfg.headTargets != null && !cfg.headTargets.isEmpty();
    }

    public static void tick() {
        if (!enabled()) {
            if (!blocks.isEmpty()) blocks.clear();
            if (!stands.isEmpty()) stands.clear();
            return;
        }
        if (++ticks < 20) return;
        ticks = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        blocks.clear();
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        int r = Math.min(mc.options.renderDistance().get(), 12);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = e.getValue();
                    if (!(be instanceof SkullBlockEntity skull)) continue;
                    if (!(be.getBlockState().getBlock() instanceof SkullBlock)) continue;
                    TeslaMapsConfig.HeadTarget t = match(hashOf(skull.getOwnerProfile()));
                    if (t != null) {
                        blocks.add(new BlockHit(e.getKey().immutable(), t.name, TeslaMapsConfig.parseColor(t.color)));
                    }
                }
            }
        }

        stands.clear();
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class,
                mc.player.getBoundingBox().inflate(128, 128, 128))) {
            TeslaMapsConfig.HeadTarget t = match(hashOf(stand.getItemBySlot(EquipmentSlot.HEAD)));
            if (t != null) stands.add(new StandHit(stand, t.name, TeslaMapsConfig.parseColor(t.color)));
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!enabled() || (blocks.isEmpty() && stands.isEmpty())) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();

        for (BlockHit h : blocks) {
            AABB box = new AABB(h.pos.getX() + 0.15, h.pos.getY(), h.pos.getZ() + 0.15,
                    h.pos.getX() + 0.85, h.pos.getY() + 0.75, h.pos.getZ() + 0.85);
            draw(matrices, cameraPos, cfg, box, h.name, h.color, h.pos);
        }
        for (StandHit h : stands) {
            if (!h.stand.isAlive()) continue;
            Vec3 p = h.stand.position();
            AABB box = new AABB(p.x - 0.35, p.y + 1.1, p.z - 0.35, p.x + 0.35, p.y + 1.8, p.z + 0.35);
            draw(matrices, cameraPos, cfg, box, h.name, h.color, BlockPos.containing(p));
        }
    }

    private static void draw(PoseStack matrices, Vec3 cameraPos, TeslaMapsConfig cfg,
                             AABB box, String name, int color, BlockPos beamPos) {
        ESPRenderer.drawBoxOutline(matrices, box, color, 1.5f, cameraPos);
        Vec3 center = box.getCenter();
        if (cfg.headESPName) {
            int dist = (int) cameraPos.distanceTo(center);
            ESPRenderer.drawText(matrices, "§f" + name + " §7" + dist + "m",
                    new Vec3(center.x, box.maxY + 0.5, center.z), 1.5f, cameraPos);
        }
        if (cfg.headESPTracers) ESPRenderer.drawTracerFromCamera(matrices, center, color, cameraPos);
        if (cfg.headESPBeam) ESPRenderer.drawBeaconBeam(matrices, beamPos, color, cameraPos);
    }

    private static TeslaMapsConfig.HeadTarget match(String hash) {
        if (hash == null) return null;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (cfg.headGoldenDragonEgg) {
            for (String known : GOLDEN_DRAGON_EGG) {
                if (known.equalsIgnoreCase(hash)) {
                    return new TeslaMapsConfig.HeadTarget("Golden Dragon Egg", hash, cfg.headGoldenDragonColor);
                }
            }
        }
        if (!cfg.headESP) return null;
        for (TeslaMapsConfig.HeadTarget t : cfg.headTargets) {
            if (t.hash != null && t.hash.equalsIgnoreCase(hash)) return t;
        }
        return null;
    }

    public static String hashOf(ItemStack head) {
        if (head == null || head.isEmpty()) return null;
        return hashOf(head.get(DataComponents.PROFILE));
    }

    // the texture hash is the last path segment of the skin url inside the base64 blob
    public static String hashOf(ResolvableProfile profile) {
        if (profile == null) return null;
        Collection<Property> props = profile.partialProfile().properties().get("textures");
        if (props.isEmpty()) return null;
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(props.iterator().next().value()));
        } catch (IllegalArgumentException e) {
            return null;
        }
        int i = decoded.indexOf("/texture/");
        if (i < 0) return null;
        int start = i + 9;
        int end = start;
        while (end < decoded.length() && isHex(decoded.charAt(end))) end++;
        return end > start ? decoded.substring(start, end) : null;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
