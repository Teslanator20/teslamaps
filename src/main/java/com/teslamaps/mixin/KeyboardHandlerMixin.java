package com.teslamaps.mixin;

import com.teslamaps.features.ConfigHotkey;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// High priority so the cancel lands before other mods get to look at the key.
@Mixin(value = KeyboardHandler.class, priority = 1500)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$configKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (ConfigHotkey.handleKey(window, action, event.key())) ci.cancel();
    }
}
