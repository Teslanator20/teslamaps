package com.teslamaps.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Exposes the chat internals needed to find the message line under the cursor. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> teslamaps$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int teslamaps$getChatScrollbarPos();

    @Invoker("getScale")
    double teslamaps$getScale();

    @Invoker("getLineHeight")
    int teslamaps$getLineHeight();
}
