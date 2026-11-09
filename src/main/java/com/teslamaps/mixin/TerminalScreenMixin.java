package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TermGui;
import com.teslamaps.features.TerminalSolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class TerminalScreenMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Custom GUI replaces the window entirely, so nothing vanilla gets drawn.
        if (TermGui.renderActive(graphics, mouseX, mouseY)) {
            ci.cancel();
            return;
        }
        HandledScreenAccessor acc = (HandledScreenAccessor) this;
        TerminalSolver.onRender(graphics, acc.getX(), acc.getY(), acc.getImageWidth(), acc.getImageHeight());
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onDrawSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (TerminalSolver.onRenderSlot(graphics, slot, (AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onSlotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (TerminalSolver.onSlotClick(slotId, button)) ci.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onDrawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (TerminalSolver.onDrawTooltip(graphics)) ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (TerminalSolver.onTerminalKey(input.key(), ((HandledScreenAccessor) this).getFocusedSlot())
                || TermGui.keyActive(input)) cir.setReturnValue(true);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (TerminalSolver.hideClutter()) ci.cancel();
    }

    @Inject(method = "extractCarriedItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideCarried(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (TerminalSolver.hideClutter()) ci.cancel();
    }

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideHighlightBack(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (TerminalSolver.hideClutter()) ci.cancel();
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideHighlightFront(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (TerminalSolver.hideClutter()) ci.cancel();
    }
}
