package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class ContainerKeyMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$containerKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (com.teslamaps.features.HoppityHelpers.blockClose(cs, event)) { cir.setReturnValue(true); return; }

        if (com.teslamaps.features.bazaar.PurchaseGuard.onKey(event.key())) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.choco.ChocoStrays.blockKey(event)) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.FancyAuctionHouse.keyPressed(cs, event.key())) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.keyPressed(event.key(), event.scancode())) cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.FocusMode.onKey(event.key())) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.hunting.ShardOverlay.onKeyPressed(event.key())) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.SearchOverlay.onContainerKey(cs, event.key())) {
            cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.Searchbar.onKeyPressed(event.key(), event.scancode())) {
            cir.setReturnValue(true);
            return;
        }

        Slot hovered = ((HandledScreenAccessor) (Object) this).getFocusedSlot();
        if (com.teslamaps.features.npc.VisitorLogbook.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.GardenMenuHelpers.key(event.key())
                || com.teslamaps.features.EcoOverlays.key(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.ItemHighlight.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.SlotLock.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.ItemProtection.onKey(event.key(), cs, hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.DevTools.onKey(event.key(), hovered)
                || com.teslamaps.features.inspector.Inspector.onContainerKey(event.key(), hovered)
                || com.teslamaps.features.customize.CustomizeKey.onKey(event.key(), cs, hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.npc.NpcWarps.onContainerKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.GetFromSacks.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.BackpackPreview.onKey(event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.SlotBinds.onKey(event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.AuctionPriceInput.onContainerKey(cs, event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (mc.options.keyDrop.matches(event) && com.teslamaps.features.SlotLock.blockSlot(hovered)) {
            cir.setReturnValue(true);
            return;
        }


        if (com.teslamaps.features.MiddleClickGui.onKey(cs, event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.hunting.FusionHelper.onKey(cs, event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.WardrobeKeys.handleKey(cs, event)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.pets.PetKeybinds.handleKey(cs, event)) {
            cir.setReturnValue(true);
            return;
        }

        if (c.pageKeybinds) {
            String want = switch (event.key()) {
                case com.mojang.blaze3d.platform.InputConstants.KEY_A -> "Previous Page";
                case com.mojang.blaze3d.platform.InputConstants.KEY_D -> "Next Page";
                case com.mojang.blaze3d.platform.InputConstants.KEY_W -> "Scroll Up";
                case com.mojang.blaze3d.platform.InputConstants.KEY_S -> "Scroll Down";
                default -> null;
            };
            if (want == null) return;
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (slot.getItem().isEmpty()) continue;
                if (slot.getItem().getHoverName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "").contains(want)) {
                    mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

}
