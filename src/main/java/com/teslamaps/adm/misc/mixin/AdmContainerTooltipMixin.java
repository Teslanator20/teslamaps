package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.pf.PartyFinderStats;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Party Finder Stats: member rows rewritten in place
@Mixin(AbstractContainerScreen.class)
public class AdmContainerTooltipMixin {
    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void teslamaps$admPartyFinderStats(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        List<Component> original = cir.getReturnValue();
        if (original == null) return;
        List<Component> replaced;
        try {
            replaced = PartyFinderStats.decorate((AbstractContainerScreen<?>) (Object) this, stack, original);
        } catch (Throwable t) {
            return;
        }
        if (replaced != original) cir.setReturnValue(replaced);
    }
}
