package com.teslamaps.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class AnimatedDyeInventoryMixin {
    @Inject(method = "setItem", at = @At("TAIL"))
    private void teslamaps$rememberArmor(int slot, ItemStack stack, CallbackInfo ci) {
        com.teslamaps.features.AnimatedDyeFix.observe((Inventory) (Object) this, slot, stack);
    }
}
