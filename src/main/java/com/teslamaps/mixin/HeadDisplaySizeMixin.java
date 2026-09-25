package com.teslamaps.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.features.HeadDisplaySize;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiGraphicsExtractor.class)
public class HeadDisplaySizeMixin {
    @ModifyArg(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;<init>(Lorg/joml/Matrix3x2f;Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;IILnet/minecraft/client/gui/navigation/ScreenRectangle;)V"),
            index = 0)
    private Matrix3x2f teslamaps$headSize(Matrix3x2f pose, @Local(argsOnly = true) ItemStack stack,
                                          @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
        return HeadDisplaySize.scale(pose, stack, x, y);
    }
}
