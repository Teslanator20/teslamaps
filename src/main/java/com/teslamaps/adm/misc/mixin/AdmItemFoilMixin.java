package com.teslamaps.adm.misc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// GUI items are cached in the item atlas by model identity, so a hidden glint gets its own key
@Mixin({CuboidItemModelWrapper.class, SpecialModelWrapper.class})
public class AdmItemFoilMixin {
    private static final String TESLAMAPS_GLINT_HIDDEN = "teslamaps:adm_glint_hidden";

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean teslamaps$admNoItemGlint(ItemStack stack, Operation<Boolean> original,
                                             @Local(argsOnly = true) ItemStackRenderState renderState,
                                             @Local(argsOnly = true) ItemDisplayContext context) {
        if (RandomStuff.hidesGlint(context)) {
            renderState.appendModelIdentityElement(TESLAMAPS_GLINT_HIDDEN);
            return false;
        }
        return original.call(stack);
    }
}
