package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EspStyle;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockOverlay {

    public static final String[] STYLES = {EspStyle.OUTLINE, EspStyle.FILLED, EspStyle.BOTH};

    public static void register() {
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, outline) -> {
            TeslaMapsConfig c = TeslaMapsConfig.get();
            return !(c.blockOverlay && c.blockOverlayHideVanilla && c.section("Render")) || com.teslamaps.features.GlobalRender.off();
        });
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.blockOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK)
            return;
        if (c.blockOverlayHideEtherwarp && Etherwarp.previewShown()) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getShape(mc.level, pos);
        String style = c.blockOverlayStyle == null ? EspStyle.OUTLINE : c.blockOverlayStyle;
        boolean fill = EspStyle.FILLED.equals(style) || EspStyle.BOTH.equals(style);
        boolean line = !EspStyle.FILLED.equals(style);
        int color = TeslaMapsConfig.parseColor(c.blockOverlayColor);
        int fillColor = TeslaMapsConfig.parseColor(c.blockOverlayFillColor);
        boolean through = c.blockOverlayThroughWalls; // default depth-tested, sits on the block

        if (c.blockOverlayExactShape && !shape.isEmpty()) {
            for (AABB part : shape.toAabbs()) {
                AABB box = part.move(pos.getX(), pos.getY(), pos.getZ());
                if (fill) ESPRenderer.drawFilledBox(matrices, box, fillColor, cameraPos, through);
                if (line) ESPRenderer.drawBoxOutline(matrices, box, color, c.blockOverlayLineWidth, cameraPos, through);
            }
            return;
        }
        AABB box = shape.isEmpty() ? new AABB(pos)
                : shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        if (fill) ESPRenderer.drawFilledBox(matrices, box, fillColor, cameraPos, through);
        if (line) ESPRenderer.drawBoxOutline(matrices, box, color, c.blockOverlayLineWidth, cameraPos, through);
    }
}
