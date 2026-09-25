package com.teslamaps.mixin;

import com.teslamaps.features.FullChatChroma;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"})
public class FullChatChromaMixin {

    @ModifyVariable(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence teslamaps$fullChroma(FormattedCharSequence text) {
        return FullChatChroma.apply(text);
    }
}
