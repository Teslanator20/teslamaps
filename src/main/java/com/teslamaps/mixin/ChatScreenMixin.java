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

    @Inject(method = "removed", at = @At("HEAD"))
    private void teslamaps$saveScroll(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.teslamaps.features.ChatKeepScroll.onClose(Minecraft.getInstance().gui.getChat());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void teslamaps$restoreScroll(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.teslamaps.features.ChatKeepScroll.onOpen(Minecraft.getInstance().gui.getChat());
    }

    @org.spongepowered.asm.mixin.Shadow protected net.minecraft.client.gui.components.EditBox input;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$chatSearchKeys(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (input != null && com.teslamaps.features.ChatSearch.onKey(event.key(), ctrl, input)) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void teslamaps$chatSearchEnd(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.teslamaps.features.ChatSearch.onClose();
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void teslamaps$confirmPrompt(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !cir.getReturnValue()) com.teslamaps.features.ConfirmPrompt.onEmptyChatClick();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void teslamaps$chatImagesOnTop(net.minecraft.client.gui.GuiGraphicsExtractor ctx,
                                           int mouseX, int mouseY, float delta,
                                           org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.teslamaps.features.SharedImageChatRenderer.render(ctx, true, mouseX, mouseY);
        com.teslamaps.features.ChatSearch.render(ctx, input);
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

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$filterHoveredLine(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.chatFilterLineKey < 0 || event.key() != c.chatFilterLineKey || !c.section("Chat")) return;
        GuiMessage msg = com.teslamaps.features.ChatHover.hovered();
        if (msg == null) return;
        com.teslamaps.features.ChatUserFilters.addLine(msg.content());
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void teslamaps$copyOnRightClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().chatCopyEnabled) return;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiMessage msg = teslamaps$lineAt(mc, event.y());
        if (msg == null) return;
        Component content = msg.content();
        String raw = content.getString();
        if (raw.isEmpty()) return;

        long handle = mc.getWindow().handle();
        boolean keepColors = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        String text = keepColors ? com.teslamaps.utils.ChatText.formatted(content) : com.teslamaps.utils.StringUtil.strip(raw);

        mc.keyboardHandler.setClipboard(text);
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Copied" + (keepColors ? " §8(with colors)§7" : "") + ": §f" + text));
        cir.setReturnValue(true); // consume the right-click
    }

    /** The hovered message; vanilla row math only when a chat mod bypasses the hover hook. */
    @org.spongepowered.asm.mixin.Unique
    private static GuiMessage teslamaps$lineAt(Minecraft mc, double mouseY) {
        if (com.teslamaps.features.ChatHover.works()) return com.teslamaps.features.ChatHover.hovered();

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = acc.teslamaps$getTrimmedMessages();
        if (lines.isEmpty()) return null;

        double scale = acc.teslamaps$getScale();
        int lineHeight = acc.teslamaps$getLineHeight();
        if (scale <= 0 || lineHeight <= 0) return null;

        int guiHeight = mc.getWindow().getGuiScaledHeight();
        double aboveBottom = (guiHeight - 40) - mouseY;
        if (aboveBottom < 0) return null;
        int lineFromBottom = (int) (aboveBottom / (scale * lineHeight));
        int index = lineFromBottom + acc.teslamaps$getChatScrollbarPos();
        if (index < 0 || index >= lines.size()) return null;
        return lines.get(index).parent();
    }
}
