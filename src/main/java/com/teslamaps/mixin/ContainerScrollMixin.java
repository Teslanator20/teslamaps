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
        if (com.teslamaps.config.TeslaMapsConfig.get().section("Shards")
                && com.teslamaps.features.hunting.ShardOverlay.mouseScrolled(scrollY)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.FancyAuctionHouse.scrolled(scrollY)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.accessories.AccessoryHelper.mouseScrolled(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.EcoOverlays.scroll(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
            return;
        }
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        if (com.teslamaps.features.PageScroll.onScroll(cs, ((HandledScreenAccessor) (Object) this).getFocusedSlot(), scrollY)) {
            cir.setReturnValue(true);
            return;
        }
        if (ScrollableTooltip.onScroll(scrollY)) cir.setReturnValue(true);
    }
}
