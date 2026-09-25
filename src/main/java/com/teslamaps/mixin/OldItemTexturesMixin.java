package com.teslamaps.mixin;

import com.teslamaps.features.OldItemTextures;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public class OldItemTexturesMixin {

    @ModifyVariable(method = {"updateForTopItem", "updateForLiving", "updateForNonLiving", "appendItemLayers"},
            at = @At("HEAD"), argsOnly = true)
    private ItemStack teslamaps$oldTexture(ItemStack stack) {
        return OldItemTextures.forRender(stack);
    }
}
