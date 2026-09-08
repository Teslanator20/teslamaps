package com.teslamaps.mixin;

import com.teslamaps.dungeon.termgui.TermGui;
import com.teslamaps.features.LeapOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class HandledScreenClickMixin {

    @Unique
    private static void teslamaps$ate(String who, int button) {
        com.teslamaps.TeslaMaps.debug("[ClickDbg] button {} swallowed by {}", button, who);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        com.teslamaps.features.AuctionPriceInput.onSlotClick(
                (AbstractContainerScreen<?>) (Object) this, ((HandledScreenAccessor) (Object) this).getFocusedSlot());

        // A hotbar key bound to a mouse button produces no KeyEvent at all, so the wardrobe
        // picker never saw it from ContainerKeyMixin. Same logic, mouse entry point.
        if (com.teslamaps.features.WardrobeKeys.handle((AbstractContainerScreen<?>) (Object) this,
                com.teslamaps.features.WardrobeKeys.wantedFromMouse(click))) {
            teslamaps$ate("WardrobeKeys", button);
            cir.setReturnValue(true);
            return;
        }

        // Binds live in one int for both devices, so a mouse-bound slot bind or slot lock has to
        // reach the same handlers ContainerKeyMixin calls for keyboard binds.
        int asBind = com.teslamaps.utils.KeyNames.forMouse(button);
        if (com.teslamaps.features.SlotBinds.onKey(asBind)
                || com.teslamaps.features.SlotLock.onKey(asBind,
                        ((HandledScreenAccessor) (Object) this).getFocusedSlot())
                || com.teslamaps.features.GetFromSacks.onKey(asBind,
                        ((HandledScreenAccessor) (Object) this).getFocusedSlot())) {
            teslamaps$ate("SlotBinds/SlotLock/GetFromSacks", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.handleClick(mouseX, mouseY, button)) {
                teslamaps$ate("StorageOverlay.handleClick", button);
                cir.setReturnValue(true);
            }
            return;
        }
        if (com.teslamaps.features.StorageOverlay.handleEnableClick(mouseX, mouseY)) {
            teslamaps$ate("StorageOverlay.handleEnableClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (TermGui.clickActive(mouseX, mouseY, button)) {
            teslamaps$ate("TermGui.clickActive", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.Searchbar.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("Searchbar.handleClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.SlotLock.blockSlot(((HandledScreenAccessor) (Object) this).getFocusedSlot())) {
            teslamaps$ate("SlotLock.blockSlot", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.ItemProtection.blockCursorDrop(
                (AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot() != null)) {
            teslamaps$ate("ItemProtection.blockCursorDrop", button);
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.ItemProtection.blockDestructiveClick(
                (AbstractContainerScreen<?>) (Object) this,
                ((HandledScreenAccessor) (Object) this).getFocusedSlot(),
                net.minecraft.client.Minecraft.getInstance().player != null
                        && net.minecraft.client.Minecraft.getInstance().player.isShiftKeyDown())) {
            teslamaps$ate("ItemProtection.blockDestructiveClick", button);
            cir.setReturnValue(true);
            return;
        }

        if (LeapOverlay.handleClick(mouseX, mouseY, button)) {
            teslamaps$ate("LeapOverlay.handleClick", button);
            cir.setReturnValue(true);
        }
    }
}
