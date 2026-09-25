package com.teslamaps.mixin;

import com.teslamaps.render.gui.HudTextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class HudTextStyleMixin {

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void teslamaps$hudText(Font font, FormattedCharSequence seq, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (HudTextStyle.onText((GuiGraphicsExtractor) (Object) this, font, seq, x, y, color, shadow)) ci.cancel();
    }
}
