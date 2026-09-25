package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.features.OldMasterStar;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class OldMasterStarMixin {

    @ModifyReturnValue(method = "getCustomName", at = @At("RETURN"))
    private Component teslamaps$oldMasterStar(Component original) {
        return OldMasterStar.transform(original);
    }
}
