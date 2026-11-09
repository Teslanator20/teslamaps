package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagTweaksMixin {
    private static final String DRAW = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V";

    @ModifyArg(method = "renderTranslucent", at = @At(value = "INVOKE", target = DRAW), index = 8)
    private int teslamaps$noBackground(int color) {
        return TeslaMapsConfig.get().nametagNoBackground ? 0 : color;
    }
}
