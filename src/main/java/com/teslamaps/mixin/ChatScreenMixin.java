package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Right-click a chat line (while the chat is open) to copy its full message text to the clipboard.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void teslamaps$copyOnRightClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().chatCopyEnabled) return;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.teslamaps$getTrimmedMessages();
        if (lines.isEmpty()) return;

        double scale = acc.teslamaps$getScale();
        int lineHeight = acc.teslamaps$getLineHeight();
        if (scale <= 0 || lineHeight <= 0) return;

        // Chat is anchored at the bottom-left; lines stack upward. Convert the cursor's
        // scaled-GUI Y into a line index (index 0 = newest/bottom line).
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        double aboveBottom = (guiHeight - 40) - event.y();
        if (aboveBottom < 0) return;
        int lineFromBottom = (int) (aboveBottom / (scale * lineHeight));
        int index = lineFromBottom + acc.teslamaps$getChatScrollbarPos();
        if (index < 0 || index >= lines.size()) return;

        String text = lines.get(index).parent().content().getString();
        if (text.isEmpty()) return;

        mc.keyboardHandler.setClipboard(text);
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Copied: §f" + text));
        cir.setReturnValue(true); // consume the right-click
    }
}
