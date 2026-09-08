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

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void teslamaps$chatImagesOnTop(net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                                           int mouseX, int mouseY, float delta,
                                           org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.teslamaps.features.SharedImageChatRenderer.render(ctx, true, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void teslamaps$openSharedImage(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        var hit = com.teslamaps.features.SharedImageChatRenderer.hitAt(event.x(), event.y());
        if (hit == null) return;

        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(com.teslamaps.screen.ImageViewerScreen.forUrl(mc.screen, hit.sender(), hit.url()));
        cir.setReturnValue(true);
    }

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

        int guiHeight = mc.getWindow().getGuiScaledHeight();
        double aboveBottom = (guiHeight - 40) - event.y();
        if (aboveBottom < 0) return;
        int lineFromBottom = (int) (aboveBottom / (scale * lineHeight));
        int index = lineFromBottom + acc.teslamaps$getChatScrollbarPos();
        if (index < 0 || index >= lines.size()) return;

        String raw = lines.get(index).parent().content().getString();
        if (raw.isEmpty()) return;

        long handle = mc.getWindow().handle();
        boolean keepColors = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        String text = keepColors ? raw : raw.replaceAll("(?i)§[0-9A-FK-OR]", "");

        mc.keyboardHandler.setClipboard(text);
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Copied" + (keepColors ? " §8(with colors)§7" : "") + ": §f" + text));
        cir.setReturnValue(true); // consume the right-click
    }
}
