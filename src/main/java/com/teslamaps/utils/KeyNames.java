package com.teslamaps.utils;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class KeyNames {

    /**
     * Mouse buttons live in the same int as keyboard keys, so they need a range of their own:
     * GLFW numbers its mouse buttons 0..7, which would collide with real key codes. Anything
     * at or below {@link #MOUSE_BASE} is a mouse button, -1 stays "unbound".
     */
    public static final int MOUSE_BASE = -100;

    public static int forMouse(int button) {
        return MOUSE_BASE - button;
    }

    public static boolean isMouse(int key) {
        return key <= MOUSE_BASE;
    }

    public static int mouseButton(int key) {
        return MOUSE_BASE - key;
    }

    /** One place that answers "is this bind held down", whichever device it belongs to. */
    public static boolean isDown(int key) {
        if (key == -1) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long handle = mc.getWindow().handle();
        return isMouse(key)
                ? GLFW.glfwGetMouseButton(handle, mouseButton(key)) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    public static String display(int key) {
        if (key == -1) return "Unbound";
        if (isMouse(key)) return mouseName(mouseButton(key));
        if (key < 0) return "Unbound";

        String numpad = numpadName(key);
        if (numpad != null) return numpad;

        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    private static String mouseName(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Mouse Left";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Mouse Right";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Mouse Middle";
            default -> "Mouse " + (button + 1);
        };
    }

    // InputConstants has no entries for KP_DIVIDE..KP_ADD, so getOrCreate would hand back
    // "key.keyboard.331". Name the whole pad here so it stays consistent.
    private static String numpadName(int key) {
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return "Num " + (key - GLFW.GLFW_KEY_KP_0);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Num .";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Num /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num -";
            case GLFW.GLFW_KEY_KP_ADD -> "Num +";
            case GLFW.GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW.GLFW_KEY_KP_EQUAL -> "Num =";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock";
            default -> null;
        };
    }
}
