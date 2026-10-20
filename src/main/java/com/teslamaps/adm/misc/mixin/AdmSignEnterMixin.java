package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Enter Confirms Sign: presses Done through onClose
@Mixin(AbstractSignEditScreen.class)
public class AdmSignEnterMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admEnterFinishesSign(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!RandomStuff.signEnterFinishes(event.key())) return;
        ((AbstractSignEditScreen) (Object) this).onClose();
        cir.setReturnValue(true);
    }
}
