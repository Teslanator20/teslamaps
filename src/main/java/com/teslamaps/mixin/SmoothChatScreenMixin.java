package com.teslamaps.mixin;

// Ported from Skysoft's ChatScreenMixin input motion
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.teslamaps.features.SmoothChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class SmoothChatScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void teslamaps$startOpen(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        SmoothChat.onChatOpened(player != null && player.isSleeping());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void teslamaps$stopOpen(CallbackInfo ci) {
        SmoothChat.onChatClosed();
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void teslamaps$slideInputBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                                Operation<Void> original, @Share("tmOpenDy") LocalFloatRef dyRef) {
        float dy = SmoothChat.inputOffset(Minecraft.getInstance().getWindow().getGuiScaledHeight());
        dyRef.set(dy);
        if (dy == 0f) {
            original.call(graphics, x0, y0, x1, y1, color);
            return;
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(0f, dy);
            original.call(graphics, x0, y0, x1, y1, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void teslamaps$slideInput(ChatScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                      Operation<Void> original, @Share("tmOpenDy") LocalFloatRef dyRef) {
        float dy = dyRef.get();
        if (dy == 0f) {
            original.call(screen, graphics, mouseX, mouseY, delta);
            return;
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(0f, dy);
            original.call(screen, graphics, mouseX, mouseY, delta);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
