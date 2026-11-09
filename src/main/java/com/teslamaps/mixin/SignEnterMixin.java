package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SignCalculator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignEnterMixin {
    @Shadow @Final private String[] messages;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$signEnter(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.signEnterKey) return;
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            if (c.signEnterShiftNewline && event.hasShiftDown()) return;
            ((AbstractSignEditScreen) (Object) this).onClose();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void teslamaps$signCalc(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!com.teslamaps.features.SpeedPresets.renderSign(g, messages)) SignCalculator.render(g, messages);
    }

    @Inject(method = "onDone", at = @At("HEAD"))
    private void teslamaps$signCalcDone(CallbackInfo ci) {
        com.teslamaps.features.SpeedPresets.onSignDone(messages);
        SignCalculator.onDone(messages);
    }
}
