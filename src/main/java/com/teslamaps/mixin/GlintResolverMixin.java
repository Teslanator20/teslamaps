package com.teslamaps.mixin;

import com.teslamaps.features.customize.GlintColors;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class GlintResolverMixin {

    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void teslamaps$glintStart(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, Level level,
                                      ItemOwner owner, int seed, CallbackInfo ci) {
        GlintColors.extracting = GlintColors.colorFor(stack);
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void teslamaps$glintEnd(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, Level level,
                                    ItemOwner owner, int seed, CallbackInfo ci) {
        GlintColors.extracting = 0;
    }
}
