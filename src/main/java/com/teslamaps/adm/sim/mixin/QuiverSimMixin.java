package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class QuiverSimMixin {
    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsQuiver(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack arrow = SimItems.quiverArrow((Player) (Object) this, heldWeapon);
        if (arrow != null) cir.setReturnValue(arrow);
    }
}
