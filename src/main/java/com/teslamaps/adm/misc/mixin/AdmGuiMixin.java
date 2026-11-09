package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class AdmGuiMixin {
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admHideItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (RandomStuff.hidesItemNames()) ci.cancel();
    }

    // drawn only: every action bar parser still gets the line
    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admHideActionBar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (RandomStuff.hidesActionBar()) ci.cancel();
    }
}
