package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TermGui;
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

@Mixin(ContainerScreen.class)
public abstract class GenericContainerScreenMixin extends AbstractContainerScreen<ChestMenu> {

    public GenericContainerScreenMixin(ChestMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void onDrawBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TermGui.active() != null) {
            ci.cancel();
            return;
        }
        if (LeapOverlay.shouldRender() || com.teslamaps.features.TerminalSolver.hideClutter()) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.features.BetterContainers.replaceBackground(context, this)) ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void teslamaps$fancyCrafting(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        com.teslamaps.features.FancyCraftingTable.drawBackground(context, this);
        com.teslamaps.features.BetterContainers.drawOverVanilla(context, this);
    }
}
