package com.teslamaps.adm.leap.mixin;

import com.teslamaps.adm.leap.AdmLeapMenu;
import com.teslamaps.adm.leap.pov.PovPreviews;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Like Odin's leap menu: the whole Spirit Leap screen (background included) is replaced by the boxes. */
@Mixin(value = Screen.class, priority = 900)
public class AdmLeapRenderMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void adm$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!AdmLeapMenu.active(self)) return;
        PovPreviews.onScreenExtract(g);
        AdmLeapMenu.render(g, mouseX, mouseY);
        ci.cancel();
    }
}
