package com.teslamaps.mixin;

import com.teslamaps.features.NoCursorReset;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class NoCursorResetMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void teslamaps$cursorRemoved(CallbackInfo ci) {
        NoCursorReset.onContainerRemoved();
    }
}
