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
        com.mojang.blaze3d.platform.InputConstants.KEY_W, com.mojang.blaze3d.platform.InputConstants.KEY_A, com.mojang.blaze3d.platform.InputConstants.KEY_S, com.mojang.blaze3d.platform.InputConstants.KEY_D,
        com.mojang.blaze3d.platform.InputConstants.KEY_SPACE, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL,
        com.mojang.blaze3d.platform.InputConstants.KEY_1, com.mojang.blaze3d.platform.InputConstants.KEY_2, com.mojang.blaze3d.platform.InputConstants.KEY_3, com.mojang.blaze3d.platform.InputConstants.KEY_4,
        com.mojang.blaze3d.platform.InputConstants.KEY_5, com.mojang.blaze3d.platform.InputConstants.KEY_6, com.mojang.blaze3d.platform.InputConstants.KEY_7, com.mojang.blaze3d.platform.InputConstants.KEY_8, com.mojang.blaze3d.platform.InputConstants.KEY_9,
        com.mojang.blaze3d.platform.InputConstants.KEY_Q, com.mojang.blaze3d.platform.InputConstants.KEY_E, com.mojang.blaze3d.platform.InputConstants.KEY_R, com.mojang.blaze3d.platform.InputConstants.KEY_F,
        com.mojang.blaze3d.platform.InputConstants.KEY_TAB, com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
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
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(key)) {
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
