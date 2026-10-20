package com.teslamaps.adm.leap.mixin;

import com.teslamaps.adm.leap.AdmLeapMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ADM leap menu takes over the Spirit Leap screen: its own drawing, clicks and keys. */
@Mixin(value = AbstractContainerScreen.class, priority = 900)
public class AdmLeapScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void adm$click(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!AdmLeapMenu.active(self)) return;
        if (click.button() == 0) AdmLeapMenu.click(self, click.x(), click.y(), false);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void adm$release(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!AdmLeapMenu.active(self)) return;
        if (click.button() == 0) AdmLeapMenu.click(self, click.x(), click.y(), true);
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void adm$key(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!AdmLeapMenu.active(self)) return;
        if (AdmLeapMenu.key(self, event.key())) cir.setReturnValue(true);
    }
}
