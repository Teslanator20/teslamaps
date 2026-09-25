package com.teslamaps.mixin;

import com.teslamaps.features.StreamerMode;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class StreamerFontMixin {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private String teslamaps$streamerString(String text) {
        return StreamerMode.on ? StreamerMode.replace(text) : text;
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence teslamaps$streamerSequence(FormattedCharSequence text) {
        return StreamerMode.on ? StreamerMode.replace(text) : text;
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String teslamaps$streamerWidthString(String text) {
        return StreamerMode.on ? StreamerMode.replace(text) : text;
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence teslamaps$streamerWidthSequence(FormattedCharSequence text) {
        return StreamerMode.on ? StreamerMode.replace(text) : text;
    }
}
