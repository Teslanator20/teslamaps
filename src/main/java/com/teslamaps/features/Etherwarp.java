/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Etherwarp {

    public record EtherPos(boolean succeeded, BlockPos pos) {
        static final EtherPos NONE = new EtherPos(false, null);
    }

    private static BlockPos lastDebugPos = null; // throttles [EtherDbg] logging to pos changes

    private static CompoundTag etherwarpData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        boolean isEther = tag.getInt("ethermerge").orElse(0) == 1
                || "ETHERWARP_CONDUIT".equals(tag.getString("id").orElse(""));
        return isEther ? tag : null;
    }

    public static String[] soundKeys() {
        return com.teslamaps.utils.SoundOptions.keys();
    }

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

        if (config.debugMode && !ether.pos().equals(lastDebugPos)) {
            lastDebugPos = ether.pos();
            BlockState dbg = mc.level.getBlockState(ether.pos());
            com.teslamaps.TeslaMaps.LOGGER.info("[EtherDbg] hit={} pos={} success={} eyeY={} blockTop={}",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dbg.getBlock()),
                    ether.pos(), ether.succeeded(),
                    String.format("%.2f", player.position().y + (player.isShiftKeyDown() ? 1.27 : 1.62)),
                    dbg.getShape(mc.level, ether.pos()).isEmpty() ? "empty"
                            : String.format("%.2f", dbg.getShape(mc.level, ether.pos()).max(net.minecraft.core.Direction.Axis.Y)));
        }

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

        double eyeHeight = (player.isShiftKeyDown() ? 1.27 : 1.62) + TeslaMapsConfig.get().etherwarpEyeOffset;
        Vec3 start = position.add(0, eyeHeight, 0);
        Vec3 look = player.getLookAngle();
        Vec3 end = look.multiply(distance, distance, distance).add(start);
        return traverseVoxels(start, end);
    }

    private static boolean passable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return true;
        Block b = s.getBlock();
        return b instanceof ButtonBlock
                || b instanceof SkullBlock || b instanceof WallSkullBlock || b instanceof LadderBlock
                || b instanceof SaplingBlock || b instanceof FlowerBlock || b instanceof StemBlock
                || b instanceof CropBlock || b instanceof BaseRailBlock || b instanceof SnowLayerBlock
                || b instanceof BubbleColumnBlock || b instanceof TripWireBlock || b instanceof TripWireHookBlock
                || b instanceof FireBlock || b instanceof TorchBlock || b instanceof FlowerPotBlock
                || b instanceof TallFlowerBlock || b instanceof TallGrassBlock || b instanceof BushBlock
                || b instanceof SeagrassBlock || b instanceof TallSeagrassBlock || b instanceof SugarCaneBlock
                || b instanceof LiquidBlock || b instanceof VineBlock || b instanceof MushroomBlock
                || b instanceof WebBlock || b instanceof DryVegetationBlock || b instanceof SmallDripleafBlock
                || b instanceof LeverBlock || b instanceof NetherWartBlock || b instanceof NetherPortalBlock
                || b instanceof RedStoneWireBlock || b instanceof ComparatorBlock || b instanceof RedstoneTorchBlock
                || b instanceof RepeaterBlock;
    }

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
