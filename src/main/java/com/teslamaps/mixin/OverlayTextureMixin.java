package com.teslamaps.mixin;

import com.teslamaps.render.DragonTintTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// unused overlay row gets the dragon tints (Devonian's RecolorDragons trick), written on first use
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {
    @Shadow @Final private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void teslamaps$dragonTints(CallbackInfo ci) {
        DragonTintTexture.init(texture);
    }
}
