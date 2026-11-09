package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.features.TerminalSolver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class TerminalScaleMixin {

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
