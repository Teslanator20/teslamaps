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
 * Hide the default container background when a custom terminal GUI / leap overlay is active.
 *
 * 26.1.2: only ContainerScreen declares the background method (now extractBackground); the
 * "draw overlay on top" part lives in {@link ContainerOverlayMixin} on AbstractContainerScreen,
 * because ContainerScreen no longer declares a top-level render method to inject into.
 */
@Mixin(ContainerScreen.class)
public abstract class GenericContainerScreenMixin extends AbstractContainerScreen<ChestMenu> {

    public GenericContainerScreenMixin(ChestMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    /**
     * Inject at the start of extractBackground to hide the background when custom GUI is active.
     */
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void onDrawBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
