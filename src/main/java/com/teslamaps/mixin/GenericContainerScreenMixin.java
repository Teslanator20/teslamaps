package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TerminalGuiManager;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to render custom terminal GUI overlay on container screens.
 */
@Mixin(ContainerScreen.class)
public abstract class GenericContainerScreenMixin extends AbstractContainerScreen<ChestMenu> {

    public GenericContainerScreenMixin(ChestMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    /**
     * Inject at the end of render to draw custom terminal GUI overlay.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void onDrawBackground(GuiGraphicsExtractor context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
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
