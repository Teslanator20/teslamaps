package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Etherwarp guess box (ported from Odin's Etherwarp). Shows where the etherwarp will land
 * when sneaking while holding an etherwarp item. (Odin's actual teleport hack is single-player
 * only, so on Hypixel only the guess box matters — no packet manipulation here.)
 */
public class Etherwarp {

    public record EtherPos(boolean succeeded, BlockPos pos) {
        static final EtherPos NONE = new EtherPos(false, null);
    }

    /** @return the item's custom_data tag if it is an etherwarp item, else null. */
    private static CompoundTag etherwarpData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        boolean isEther = tag.getInt("ethermerge").orElse(0) == 1
                || "ETHERWARP_CONDUIT".equals(tag.getString("id").orElse(""));
        return isEther ? tag : null;
    }

    /** Sound option keys for the config dropdown (shared list). */
    public static String[] soundKeys() {
        return com.teslamaps.utils.SoundOptions.keys();
    }

    /** Plays the configured custom etherwarp sound (called when the default sound is cancelled). */
    public static void playCustomSound() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        LoudSound.play(com.teslamaps.utils.SoundOptions.resolve(c.etherwarpSound), c.etherwarpSoundVolume, c.etherwarpSoundPitch);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.etherwarp) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isShiftKeyDown() || mc.screen != null) return;

        CompoundTag data = etherwarpData(player.getMainHandItem());
        if (data == null) return;

        double distance = 57.0 + data.getInt("tuned_transmission").orElse(0);
        EtherPos ether = getEtherPos(player.position(), distance);
        if (!ether.succeeded() && !config.etherwarpShowFail) return;
        if (ether.pos() == null) return;

        int color = ether.succeeded()
                ? TeslaMapsConfig.parseColor(config.colorEtherwarp)
                : 0xFFFF5555;
        AABB box = new AABB(ether.pos());
        if (config.etherwarpFilled) {
            ESPRenderer.drawFilledBox(matrices, box, color, cameraPos);
        } else {
            ESPRenderer.drawBoxOutline(matrices, box, color, 2.0f, cameraPos);
        }
    }

    private static EtherPos getEtherPos(Vec3 position, double distance) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || position == null) return EtherPos.NONE;

        // Use the sneak KEY (not the crouch pose) for eye height: Hypixel keys etherwarp off the
        // sneak key, and the box only renders while sneaking, so the lowered 1.54 eye must apply
        // even when the crouch pose hasn't fully engaged (otherwise the guess sits slightly off).
        double eyeHeight = player.isShiftKeyDown() ? 1.54 : 1.62;
        Vec3 start = position.add(0, eyeHeight, 0);
        Vec3 look = player.getLookAngle();
        Vec3 end = look.multiply(distance, distance, distance).add(start);
        return traverseVoxels(start, end);
    }

    private static boolean passable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /** Voxel DDA raycast from start to end, returns the etherwarp landing spot. */
    private static EtherPos traverseVoxels(Vec3 start, Vec3 end) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return EtherPos.NONE;

        double x0 = start.x, y0 = start.y, z0 = start.z;
        double x1 = end.x, y1 = end.y, z1 = end.z;

        int x = (int) Math.floor(x0), y = (int) Math.floor(y0), z = (int) Math.floor(z0);
        int endX = (int) Math.floor(x1), endY = (int) Math.floor(y1), endZ = (int) Math.floor(z1);

        double dirX = x1 - x0, dirY = y1 - y0, dirZ = z1 - z0;
        int stepX = (int) Math.signum(dirX), stepY = (int) Math.signum(dirY), stepZ = (int) Math.signum(dirZ);

        double invX = dirX != 0 ? 1.0 / dirX : Double.MAX_VALUE;
        double invY = dirY != 0 ? 1.0 / dirY : Double.MAX_VALUE;
        double invZ = dirZ != 0 ? 1.0 / dirZ : Double.MAX_VALUE;

        double tDeltaX = Math.abs(invX * stepX), tDeltaY = Math.abs(invY * stepY), tDeltaZ = Math.abs(invZ * stepZ);
        double tMaxX = Math.abs((x + Math.max(stepX, 0) - x0) * invX);
        double tMaxY = Math.abs((y + Math.max(stepY, 0) - y0) * invY);
        double tMaxZ = Math.abs((z + Math.max(stepZ, 0) - z0) * invZ);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 1000; i++) {
            cursor.set(x, y, z);
            // First non-passable (solid) block hit = the block you'd stand on.
            if (!passable(level, cursor)) {
                boolean feet = passable(level, new BlockPos(x, y + 1, z));
                boolean head = passable(level, new BlockPos(x, y + 2, z));
                return new EtherPos(feet && head, new BlockPos(x, y, z));
            }
            if (x == endX && y == endY && z == endZ) return EtherPos.NONE;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { tMaxX += tDeltaX; x += stepX; }
            else if (tMaxY <= tMaxZ) { tMaxY += tDeltaY; y += stepY; }
            else { tMaxZ += tDeltaZ; z += stepZ; }
        }
        return EtherPos.NONE;
    }
}
