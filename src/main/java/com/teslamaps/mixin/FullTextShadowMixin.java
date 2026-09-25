package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.teslamaps.features.TextVisuals;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Ported from SkyOcean's BakedGlyphMixin
@Mixin(BakedSheetGlyph.class)
public class FullTextShadowMixin {
    private static final String RENDER = "Lnet/minecraft/client/gui/font/glyphs/BakedSheetGlyph;render(ZFFFLorg/joml/Matrix4fc;Lcom/mojang/blaze3d/vertex/VertexConsumer;IZI)V";

    @WrapOperation(method = "renderChar", at = @At(value = "INVOKE", target = RENDER, ordinal = 0))
    private void teslamaps$fullShadow(BakedSheetGlyph self, boolean italic, float x, float y, float z, Matrix4fc pose,
                                      VertexConsumer buffer, int color, boolean bold, int light, Operation<Void> original,
                                      @Local(argsOnly = true) BakedSheetGlyph.GlyphInstance glyph) {
        if (!TextVisuals.fullShadow) {
            original.call(self, italic, x, y, z, pose, buffer, color, bold, light);
            return;
        }
        float o = glyph.shadowOffset();
        for (int j = -1; j <= 1; j++)
            for (int k = -1; k <= 1; k++)
                if (j != 0 || k != 0)
                    original.call(self, italic, glyph.x() + o * j, glyph.y() + o * k, z, pose, buffer, color, bold, light);
    }

    @WrapOperation(method = "renderChar", at = @At(value = "INVOKE", target = RENDER, ordinal = 1))
    private void teslamaps$fullShadowBold(BakedSheetGlyph self, boolean italic, float x, float y, float z, Matrix4fc pose,
                                          VertexConsumer buffer, int color, boolean bold, int light, Operation<Void> original,
                                          @Local(argsOnly = true) BakedSheetGlyph.GlyphInstance glyph) {
        if (!TextVisuals.fullShadow) {
            original.call(self, italic, x, y, z, pose, buffer, color, bold, light);
            return;
        }
        float o = glyph.shadowOffset();
        for (int j = -1; j <= 1; j++)
            for (int k = -1; k <= 1; k++)
                if (j != 0 || k != 0)
                    original.call(self, italic, glyph.x() + glyph.boldOffset() + o * j, glyph.y() + o * k, z, pose, buffer, color, bold, light);
    }
}
