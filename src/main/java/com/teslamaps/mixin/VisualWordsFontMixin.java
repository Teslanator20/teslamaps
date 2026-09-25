package com.teslamaps.mixin;

import com.teslamaps.features.TextVisuals;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class VisualWordsFontMixin {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private String teslamaps$wordsString(String text) {
        return TextVisuals.on ? TextVisuals.replace(text) : text;
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence teslamaps$wordsSequence(FormattedCharSequence text) {
        return TextVisuals.on ? TextVisuals.replace(text) : text;
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String teslamaps$wordsWidthString(String text) {
        return TextVisuals.on ? TextVisuals.replace(text) : text;
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence teslamaps$wordsWidthSequence(FormattedCharSequence text) {
        return TextVisuals.on ? TextVisuals.replace(text) : text;
    }
}
