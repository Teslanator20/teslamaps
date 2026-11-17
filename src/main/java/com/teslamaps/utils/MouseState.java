package com.teslamaps.utils;

// 26.3 only updates MouseHandler.isLeftPressed while no screen is open; this one tracks every press
public class MouseState {
    private static boolean left, right;

    public static boolean left() { return left; }
    public static boolean right() { return right; }

    public static void onButton(int button, boolean down) {
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) left = down;
        else if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) right = down;
    }
}
