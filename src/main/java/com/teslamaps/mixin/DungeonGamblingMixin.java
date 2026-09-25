package com.teslamaps.mixin;

import com.teslamaps.features.DungeonGambling;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class, priority = 500)
public class DungeonGamblingMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$gambling(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (DungeonGambling.render((AbstractContainerScreen<?>) (Object) this, g)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void teslamaps$gamblingClick(MouseButtonEvent click, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
        if (DungeonGambling.blocking()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void teslamaps$gamblingRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (DungeonGambling.blocking()) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$gamblingKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!DungeonGambling.blocking()) return;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) DungeonGambling.cancel();
        cir.setReturnValue(true);
    }
}
