package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ChatHoverMixin {

    @WrapOperation(method = "accept",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean teslamaps$trackHover(ChatComponent.ChatGraphicsAccess access, int y, float opacity, FormattedCharSequence text,
                                         Operation<Boolean> original, @Local(argsOnly = true) GuiMessage.Line line) {
        boolean hovered = original.call(access, y, opacity, text);
        if (hovered) com.teslamaps.features.ChatHover.onLine(line);
        return hovered;
    }
}
