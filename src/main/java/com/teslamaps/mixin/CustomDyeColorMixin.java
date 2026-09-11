package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.AnimatedDye;
import com.teslamaps.features.customize.CustomArmorAnimatedDyes;
import com.teslamaps.features.customize.Customize;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Custom (and animated) leather dye colours from `/tmap customize`.
@Mixin(DyedItemColor.class)
public class CustomDyeColorMixin {

    @ModifyReturnValue(method = "getOrDefault", at = @At("RETURN"))
    private static int teslamaps$customDyeColor(int original, @Local(argsOnly = true) ItemStack stack) {
        if (!Customize.active()) return original;

        String uuid = Customize.uuid(stack);
        if (uuid.isEmpty()) return original;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        AnimatedDye animated = config.customAnimatedDyes.get(uuid);
        if (animated != null) return ARGB.opaque(CustomArmorAnimatedDyes.animateColorTransition(animated));

        Integer color = config.customDyeColors.get(uuid);
        if (color != null) return ARGB.opaque(color);

        return original;
    }
}
