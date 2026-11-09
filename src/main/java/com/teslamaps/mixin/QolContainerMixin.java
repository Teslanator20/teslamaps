package com.teslamaps.mixin;

import com.teslamaps.features.HarpHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class QolContainerMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$harpKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (HarpHelper.onKey((AbstractContainerScreen<?>) (Object) this, event.key())) cir.setReturnValue(true);
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$harpRestart(Slot slot, int slotId, int button, ContainerInput type, CallbackInfo ci) {
        if (HarpHelper.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot, button, type)) ci.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void teslamaps$harpTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (HarpHelper.hideTooltip((AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot())) ci.cancel();
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void teslamaps$harpLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HarpHelper.render(ctx);
    }
}
