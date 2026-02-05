package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TerminalGuiManager;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to render custom terminal GUI overlay on container screens.
 */
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> {

    public GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    /**
     * Inject at the end of render to draw custom terminal GUI overlay.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TerminalGuiManager.shouldRenderCustomGui()) {
            TerminalGuiManager.render(context);
        }

        // Render leap overlay
        if (LeapOverlay.shouldRender()) {
            LeapOverlay.render(context, mouseX, mouseY);
        }
    }

    /**
     * Inject at the start of drawBackground to hide the background when custom GUI is active.
     */
    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void onDrawBackground(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (TerminalGuiManager.shouldRenderCustomGui()) {
            // Cancel drawing the default container background
            ci.cancel();
            return;
        }
        if (LeapOverlay.shouldRender()) {
            // Cancel drawing the default container background for leap overlay
            ci.cancel();
        }
    }

}
