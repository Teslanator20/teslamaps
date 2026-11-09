package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public class NameTagTweaksMixin {
    private static final String NAME_TAG = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;";

    @ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = NAME_TAG), index = 6)
    private int teslamaps$noBackground(int color) {
        return TeslaMapsConfig.get().nametagNoBackground ? 0 : color;
    }
}
