package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEnterMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$signEnter(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().signEnterKey) return;
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            ((AbstractSignEditScreen) (Object) this).onClose();
            cir.setReturnValue(true);
        }
    }
}
