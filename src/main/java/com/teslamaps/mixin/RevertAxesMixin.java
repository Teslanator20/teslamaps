package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.features.RevertAxes;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public class RevertAxesMixin {

    @ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object teslamaps$revertAxes(Object original, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack) {
        return RevertAxes.model(stack, original);
    }
}
