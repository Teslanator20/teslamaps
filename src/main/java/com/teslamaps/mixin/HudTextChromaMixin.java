package com.teslamaps.mixin;

import com.teslamaps.render.gui.ChromaColors;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public class HudTextChromaMixin {

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence teslamaps$hudChroma(FormattedCharSequence seq) {
        int mode = HudStyle.textChroma;
        if (mode == 0 || seq == null) return seq;
        boolean all = mode == 2;
        return sink -> seq.accept((i, style, cp) -> sink.accept(i,
                all || style.getColor() == null ? style.withColor(ChromaColors.CHROMA) : style, cp));
    }
}
