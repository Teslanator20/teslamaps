package com.teslamaps.debug;

import com.teslamaps.features.HeadESP;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Identifies heads in the world so their texture hash can be fed to HeadESP.
public class HeadProbe {

    public record Grab(String hash, String label) {}

    // lists every skull block, armor-stand head and dragon egg around the player
    public static List<String> dump(int radius) {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            out.add("§cNot in world");
            return out;
        }
        Vec3 me = mc.player.position();
        BlockPos origin = mc.player.blockPosition();

        int chunks = Math.max(1, radius / 16 + 1);
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    if (!(e.getValue() instanceof SkullBlockEntity skull)) continue;
                    BlockPos p = e.getKey();
                    if (p.distToCenterSqr(me) > (double) radius * radius) continue;
                    String hash = HeadESP.hashOf(skull.getOwnerProfile());
                    String owner = skull.getOwnerProfile() == null ? "-"
                            : skull.getOwnerProfile().name().orElse("-");
                    out.add("§7skull §f" + p.getX() + " " + p.getY() + " " + p.getZ()
                            + " §8owner=" + owner + " §b" + (hash == null ? "no texture" : hash));
                }
            }
        }

        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class,
                mc.player.getBoundingBox().inflate(radius, radius, radius))) {
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty()) continue;
            String hash = HeadESP.hashOf(head);
            Vec3 p = stand.position();
            out.add("§7stand §f" + (int) p.x + " " + (int) p.y + " " + (int) p.z
                    + " §8name=" + (stand.hasCustomName() ? stand.getName().getString() : "-")
                    + " item=" + head.getItem().toString()
                    + " §b" + (hash == null ? "no texture" : hash));
        }

        int eggs = 0;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (mc.level.getBlockState(p).is(Blocks.DRAGON_EGG)) {
                out.add("§7dragon_egg §f" + p.getX() + " " + p.getY() + " " + p.getZ());
                if (++eggs >= 20) break;
            }
        }

        if (out.isEmpty()) out.add("§7nothing found within " + radius + " blocks");
        return out;
    }

    // hash of the skull the crosshair is on, else the nearest armor-stand head, else the held item
    public static Grab grab() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;

        if (mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            if (mc.level.getBlockEntity(hit.getBlockPos()) instanceof SkullBlockEntity skull) {
                String hash = HeadESP.hashOf(skull.getOwnerProfile());
                if (hash != null) return new Grab(hash, "skull block");
            }
        }

        ArmorStand best = null;
        double bestDist = 36;
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class,
                mc.player.getBoundingBox().inflate(6, 6, 6))) {
            if (HeadESP.hashOf(stand.getItemBySlot(EquipmentSlot.HEAD)) == null) continue;
            double d = stand.position().distanceToSqr(mc.player.position());
            if (d < bestDist) { bestDist = d; best = stand; }
        }
        if (best != null) {
            return new Grab(HeadESP.hashOf(best.getItemBySlot(EquipmentSlot.HEAD)), "armor stand head");
        }

        String held = HeadESP.hashOf(mc.player.getMainHandItem());
        if (held != null) return new Grab(held, "held item");
        return null;
    }

    public static void chat(String line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(line));
    }
}
