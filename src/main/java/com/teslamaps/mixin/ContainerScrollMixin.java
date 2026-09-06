package com.teslamaps.mixin;

import com.teslamaps.features.ScrollableTooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class ContainerScrollMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void teslamaps$scrollTooltip(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.mouseScrolled(scrollY)) cir.setReturnValue(true);
            return;
        }
        if (ScrollableTooltip.onScroll(scrollY)) cir.setReturnValue(true);
    }
}
