package com.teslamaps.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the transform the chat is actually drawn with.
 * <p>
 * Vanilla leaves the pose alone and lays its lines out in plain gui space, but chat mods -
 * Polyfrost's Chatting turns the whole chat into a movable hud - translate and scale the pose
 * inside this method. Anything that wants to draw next to a chat line therefore cannot assume
 * the vanilla corner. The capture sits on the call to the private overload, which is past every
 * HEAD injection those mods add, so whatever they did to the pose is already in it.
 */
@Mixin(ChatComponent.class)
public class ChatGeometryMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"))
    private void teslamaps$captureChatPose(GuiGraphicsExtractor ctx, Font font, int tickCount, int mouseX, int mouseY,
                                           ChatComponent.DisplayMode mode, boolean focused, CallbackInfo ci) {
        if (!mode.foreground) return;
        com.teslamaps.features.ChatHover.beginFrame();
        com.teslamaps.features.SharedImageChatRenderer.captureGeometry(ctx.pose(), ctx.guiHeight());
    }
}
