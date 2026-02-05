package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
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

/**
 * Mixin to handle auto-close chests and close-on-input features.
 * Uses GLFW polling for input detection (1.21.10 compatible).
 */
@Mixin(HandledScreen.class)
public abstract class ChestAutoCloseMixin {

    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    @Shadow protected int x;
    @Shadow protected int y;

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
        if (!(((Object) this) instanceof GenericContainerScreen screen)) {
            return false;
        }

        // Only in dungeon
        if (!DungeonManager.isInDungeon()) {
            return false;
        }

        // Only chests with no title (empty or just "Chest")
        Text title = screen.getTitle();
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
        // Check if any key in currentKeys was NOT in previousKeys (new press)
        for (int key : currentKeys) {
            if (!previousKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle auto-close and close-on-input in render tick using GLFW polling.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderTick(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isChestScreen()) return;
        if (hasScheduledClose) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;

        long windowHandle = mc.getWindow().getHandle();

        // Initialize on first render (calculate random offset and capture initial key state)
        if (!initialized) {
            initialized = true;
            if (config.autoCloseRandomization > 0) {
                randomCloseOffset = random.nextInt(config.autoCloseRandomization + 1);
            }
            // Capture currently pressed keys so we don't trigger on keys held when opening
            previouslyPressedKeys = getPressedKeys(windowHandle);
        }

        // Auto-close chests after delay (with randomization)
        if (config.autoCloseChests) {
            ticksSinceOpen++;
            int totalDelay = config.autoCloseDelay + randomCloseOffset;
            if (ticksSinceOpen >= totalDelay) {
                hasScheduledClose = true;
                ((HandledScreen<?>) (Object) this).close();
                return;
            }
        }

        // Close on any NEW key press (keys that weren't pressed before)
        if (config.closeChestOnInput) {
            Set<Integer> currentKeys = getPressedKeys(windowHandle);

            // Close if any new key was pressed (wasn't pressed in previous frame)
            if (hasNewKeyPress(currentKeys, previouslyPressedKeys)) {
                hasScheduledClose = true;
                ((HandledScreen<?>) (Object) this).close();
                return;
            }

            previouslyPressedKeys = currentKeys;
        }
    }
}
