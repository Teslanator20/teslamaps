package com.teslamaps.utils;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class KeyNames {

    public static String display(int key) {
        if (key < 0) return "Unbound";

        String numpad = numpadName(key);
        if (numpad != null) return numpad;

        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
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
