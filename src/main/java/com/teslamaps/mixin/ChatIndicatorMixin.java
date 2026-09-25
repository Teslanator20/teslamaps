package com.teslamaps.mixin;

// Ported from Skysoft's GuiMessageLineMixin
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.features.SmoothChat;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiMessage.Line.class)
public class ChatIndicatorMixin {

    @ModifyReturnValue(method = "tag", at = @At("RETURN"))
    private GuiMessageTag teslamaps$hideIndicator(GuiMessageTag original) {
        return original != null && SmoothChat.hideIndicator() ? null : original;
    }
}
