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
import com.teslamaps.dungeon.DungeonManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;

@Mixin(AbstractContainerScreen.class)
public abstract class ChestAutoCloseMixin {

    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Unique
    private static final int[] KEYS_TO_CHECK = {
        GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
        GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
        GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9,
        GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_F,
        GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER
    };

    @Unique
    private static final Random random = new Random();

    @Unique
    private int ticksSinceOpen = 0;

    @Unique
    private boolean hasScheduledClose = false;

    @Unique
    private int randomCloseOffset = 0;

    @Unique
    private boolean initialized = false;

    @Unique
    private Set<Integer> previouslyPressedKeys = new HashSet<>();

    @Unique
    private boolean isChestScreen() {
        if (!(((Object) this) instanceof ContainerScreen screen)) {
            return false;
        }

        if (!DungeonManager.isInDungeon()) {
            return false;
        }

        Component title = screen.getTitle();
        if (title == null) {
            return true;
        }
        String titleStr = title.getString().trim();
        return titleStr.isEmpty() || titleStr.equals("Chest") || titleStr.equals("Large Chest");
    }

    @Unique
    private Set<Integer> getPressedKeys(long windowHandle) {
        Set<Integer> pressed = new HashSet<>();
        for (int key : KEYS_TO_CHECK) {
            if (GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS) {
                pressed.add(key);
            }
        }
        return pressed;
    }

    @Unique
    private boolean hasNewKeyPress(Set<Integer> currentKeys, Set<Integer> previousKeys) {
        for (int key : currentKeys) {
            if (!previousKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onRenderTick(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isChestScreen()) return;
        if (hasScheduledClose) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        long windowHandle = mc.getWindow().handle();

        if (!initialized) {
            initialized = true;
            if (config.autoCloseRandomization > 0) {
                randomCloseOffset = random.nextInt(config.autoCloseRandomization + 1);
            }
            previouslyPressedKeys = getPressedKeys(windowHandle);
        }

        if (config.autoCloseChests) {
            ticksSinceOpen++;
            int totalDelay = config.autoCloseDelay + randomCloseOffset;
            if (ticksSinceOpen >= totalDelay) {
                hasScheduledClose = true;
                ((AbstractContainerScreen<?>) (Object) this).onClose();
                return;
            }
        }

        if (config.closeChestOnInput) {
            Set<Integer> currentKeys = getPressedKeys(windowHandle);

            if (hasNewKeyPress(currentKeys, previouslyPressedKeys)) {
                hasScheduledClose = true;
                ((AbstractContainerScreen<?>) (Object) this).onClose();
                return;
            }

            previouslyPressedKeys = currentKeys;
        }
    }
}
