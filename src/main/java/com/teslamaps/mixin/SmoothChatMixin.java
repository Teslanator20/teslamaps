package com.teslamaps.mixin;

// Ported from Skysoft's ChatComponentMixin message motion
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.features.SmoothChat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class SmoothChatMixin {

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"))
    private void teslamaps$slideMessages(ChatComponent chat, ChatComponent.ChatGraphicsAccess access, int screenHeight, int ticks,
                                         ChatComponent.DisplayMode mode, Operation<Void> original,
                                         @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        float dy = SmoothChat.messageOffset(acc.teslamaps$getLineHeight(), acc.teslamaps$getChatScrollbarPos());
        if (dy == 0f) {
            original.call(chat, access, screenHeight, ticks, mode);
            return;
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(0f, dy);
            original.call(chat, access, screenHeight, ticks, mode);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
    private void teslamaps$messageArrived(CallbackInfo ci) {
        SmoothChat.onMessageAdded();
    }
}
