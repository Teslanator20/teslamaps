package com.teslamaps.mixin;

import com.teslamaps.dungeon.terminals.TerminalUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class TerminalScaleGuiMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void teslamaps$onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen != null) TerminalUtils.onScreenOpen(screen);
        else TerminalUtils.onScreenClose();
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void teslamaps$afterSetScreen(Screen screen, CallbackInfo ci) {
        com.teslamaps.features.StorageGuiScale.onScreenSet(screen);
        com.teslamaps.features.InventoryScale.onScreenSet(screen);
    }
}
