package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the blur line at stratum 0 of every GUI state started while the frame's GUI is built
@Mixin(GuiRenderState.class)
public class AdmGuiRenderStateBlurMixin {
    @Inject(method = "reset", at = @At("TAIL"))
    private void teslamaps$admBlurBehindGui(CallbackInfo ci) {
        if (RandomStuff.blursAfterReset()) ((GuiRenderState) (Object) this).blurBeforeThisStratum();
    }
}
