/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.keyPressed(event.key())) cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.Searchbar.onKeyPressed(event.key())) {
            cir.setReturnValue(true);
            return;
        }

        Slot hovered = ((HandledScreenAccessor) (Object) this).getFocusedSlot();
        if (com.teslamaps.features.SlotLock.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (mc.options.keyDrop.matches(event) && com.teslamaps.features.SlotLock.blockSlot(hovered)) {
            cir.setReturnValue(true);
            return;
        }

        String title = cs.getTitle().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (c.wardrobeKeybinds && title.startsWith("Wardrobe (")) {
            for (int i = 0; i < 9; i++) {
                boolean custom = c.wardrobeKeys[i] >= 0 && event.key() == c.wardrobeKeys[i];
                if (custom || mc.options.keyHotbarSlots[i].matches(event)) {
                    mc.gameMode.handleContainerInput(cs.getMenu().containerId, 36 + i, 0, ContainerInput.PICKUP, mc.player);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (c.pageKeybinds) {
            String want = event.key() == GLFW.GLFW_KEY_A ? "Previous Page"
                    : event.key() == GLFW.GLFW_KEY_D ? "Next Page" : null;
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
