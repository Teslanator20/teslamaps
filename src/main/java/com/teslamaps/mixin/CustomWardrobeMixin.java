package com.teslamaps.mixin;

import com.teslamaps.features.CustomWardrobe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class, priority = 500)
public class CustomWardrobeMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wardrobe(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (CustomWardrobe.render((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY)
                || com.teslamaps.features.FancyWarpMenu.render((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY)
                || com.teslamaps.features.FancyPartyFinder.render((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY)
                || com.teslamaps.features.RadialMenus.render((AbstractContainerScreen<?>) (Object) this, g, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wardrobeRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (CustomWardrobe.blocking((AbstractContainerScreen<?>) (Object) this)
                || com.teslamaps.features.FancyWarpMenu.blocking((AbstractContainerScreen<?>) (Object) this)
                || com.teslamaps.features.FancyPartyFinder.blocking((AbstractContainerScreen<?>) (Object) this)
                || com.teslamaps.features.RadialMenus.blocking((AbstractContainerScreen<?>) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wardrobeDrag(MouseButtonEvent click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (CustomWardrobe.blocking((AbstractContainerScreen<?>) (Object) this)
                || com.teslamaps.features.FancyWarpMenu.blocking((AbstractContainerScreen<?>) (Object) this)
                || com.teslamaps.features.FancyPartyFinder.blocking((AbstractContainerScreen<?>) (Object) this)
                || com.teslamaps.features.RadialMenus.blocking((AbstractContainerScreen<?>) (Object) this)) cir.setReturnValue(true);
    }
}
