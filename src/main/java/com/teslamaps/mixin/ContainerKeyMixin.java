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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class ContainerKeyMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wardrobeKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().wardrobeKeybinds) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        String title = cs.getTitle().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (!title.startsWith("Wardrobe (")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            if (mc.options.keyHotbarSlots[i].matches(event)) {
                mc.gameMode.handleContainerInput(cs.getMenu().containerId, 36 + i, 0, ContainerInput.PICKUP, mc.player);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
