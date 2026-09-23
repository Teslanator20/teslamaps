package com.teslamaps.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.teslamaps.features.NoCursorReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class NoCursorResetMouseMixin {
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private boolean mouseGrabbed;
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void teslamaps$restoreCursor(CallbackInfo ci) {
        if (!mouseGrabbed) return;
        double[] pos = NoCursorReset.restorePos();
        if (pos == null) return;
        Window window = minecraft.getWindow();
        mouseGrabbed = false;
        xpos = pos[0];
        ypos = pos[1];
        InputConstants.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL, xpos, ypos);
        ci.cancel();
    }
}
