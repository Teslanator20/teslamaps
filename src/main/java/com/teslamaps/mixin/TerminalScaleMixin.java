package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.features.TerminalSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class TerminalScaleMixin {

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

    @ModifyExpressionValue(method = "resizeGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object teslamaps$modifyGuiScale(Object original) {
        if (TerminalSolver.overridesGuiScale() && TerminalSolver.termSize() != (Integer) original) {
            return TerminalSolver.termSize();
        }
        int storage = com.teslamaps.features.StorageGuiScale.override();
        if (storage > 0) return storage != (Integer) original ? storage : original;
        int inventory = com.teslamaps.features.InventoryScale.override();
        if (inventory >= 0 && inventory != (Integer) original) return inventory;
        return original;
    }
}
