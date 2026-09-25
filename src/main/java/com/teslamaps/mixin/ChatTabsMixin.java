package com.teslamaps.mixin;

// Ported from Skysoft's ChatComponentMixin tab hooks
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.teslamaps.features.chattabs.ChatTabs;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(ChatComponent.class)
public class ChatTabsMixin {

    /**
     * Force vanilla to STORE every message even when the tab filter rejects it,
     * so switching tabs later can reveal it. The real verdict is shared with the
     * display-queue wrap below.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    private boolean teslamaps$storeFiltered(Predicate predicate, Object message, Operation<Boolean> original,
                                            @Share("tmTabVisible") LocalBooleanRef visible) {
        boolean v = original.call(predicate, message);
        visible.set(v);
        return v || ChatTabs.isFilterApplied() || com.teslamaps.features.ChatSearch.active();
    }

    @WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
    private void teslamaps$skipDisplay(ChatComponent chat, GuiMessage message, Operation<Void> original,
                                       @Share("tmTabVisible") LocalBooleanRef visible) {
        if (visible.get()) original.call(chat, message);
    }
}
