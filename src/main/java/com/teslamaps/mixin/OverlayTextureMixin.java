package com.teslamaps.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.render.DragonTintHolder;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// unused overlay row gets the dragon tints (Devonian's RecolorDragons trick)
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {
    @Shadow @Final private DynamicTexture texture;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void teslamaps$dragonTints(CallbackInfo ci) {
        NativeImage img = texture.getPixels();
        if (img == null) return;
        int[] tints = DragonTintHolder.TINTS;
        for (int i = 0; i < tints.length; i++) img.setPixel(DragonTintHolder.TINT_U + i, DragonTintHolder.TINT_V, tints[i]);
    }
}
