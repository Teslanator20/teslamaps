package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.features.VisualTweaks;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
public class ReequipAnimationMixin {
    @ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
    private boolean teslamaps$noReequip(boolean original, ItemStack from, ItemStack to, LocalPlayer player) {
        return original || VisualTweaks.instantReplace(from, to);
    }
}
