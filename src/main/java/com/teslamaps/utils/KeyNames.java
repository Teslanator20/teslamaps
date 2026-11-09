package com.teslamaps.utils;

import com.mojang.blaze3d.platform.InputConstants;

public class KeyNames {

    /**
     * Mouse buttons live in the same int as keyboard keys, so they need a range of their own:
     * mouse buttons are numbered 1..8, which would collide with real key codes. Anything
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

    /** Mouse button (1 left, 3 right, 2 middle) -> container click button (0 left, 1 right, 2 middle), as vanilla maps it. */
    public static int toContainerButton(int mouseButton) {
        return mouseButton == InputConstants.MOUSE_BUTTON_LEFT ? 0 : mouseButton == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : mouseButton;
    }

    /** One place that answers "is this bind held down", whichever device it belongs to. */
    public static boolean isDown(int key) {
        if (key == -1) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        if (isMouse(key)) {
            int b = mouseButton(key);
            return switch (b) {
                case InputConstants.MOUSE_BUTTON_LEFT -> mc.mouseHandler.isLeftPressed();
                case InputConstants.MOUSE_BUTTON_RIGHT -> mc.mouseHandler.isRightPressed();
                case InputConstants.MOUSE_BUTTON_MIDDLE -> mc.mouseHandler.isMiddlePressed();
                default -> b > 0 && b <= 32 && (org.lwjgl.sdl.SDLMouse.nSDL_GetMouseState(0L, 0L) & (1 << (b - 1))) != 0;
            };
        }
        return key >= 0 && key < 512 && InputConstants.isKeyDown(key);
    }

    public static String display(int key) {
        if (key == -1) return "Unbound";
        if (isMouse(key)) return mouseName(mouseButton(key));
        if (key < 0) return "Unbound";

        String numpad = numpadName(key);
        if (numpad != null) return numpad;

        return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
    }

    private static String mouseName(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> "Mouse Left";
            case InputConstants.MOUSE_BUTTON_RIGHT -> "Mouse Right";
            case InputConstants.MOUSE_BUTTON_MIDDLE -> "Mouse Middle";
            default -> "Mouse " + button;
        };
    }

    private static String numpadName(int key) {
        if (key == InputConstants.KEY_NUMPAD0) return "Num 0";
        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
            return "Num " + (key - InputConstants.KEY_NUMPAD1 + 1);
        }
        return switch (key) {
            case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_PERIOD -> "Num .";
            case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_DIVIDE -> "Num /";
            case InputConstants.KEY_MULTIPLY -> "Num *";
            case org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS -> "Num -";
            case InputConstants.KEY_ADD -> "Num +";
            case InputConstants.KEY_NUMPADENTER -> "Num Enter";
            case InputConstants.KEY_NUMPADEQUALS -> "Num =";
            case InputConstants.KEY_NUMLOCK -> "Num Lock";
            default -> null;
        };
    }

    private static final int[] GLFW_TO_SDL = new int[349];

    static {
        java.util.Arrays.fill(GLFW_TO_SDL, -1);
        int[][] m = {
                {32, 44}, {39, 52}, {44, 54}, {45, 45}, {46, 55}, {47, 56}, {48, 39}, {59, 51}, {61, 46},
                {91, 47}, {92, 49}, {93, 48}, {96, 53}, {161, 100},
                {256, 41}, {257, 40}, {258, 43}, {259, 42}, {260, 73}, {261, 76}, {262, 79}, {263, 80},
                {264, 81}, {265, 82}, {266, 75}, {267, 78}, {268, 74}, {269, 77},
                {280, 57}, {281, 71}, {282, 83}, {283, 70}, {284, 72},
                {320, 98}, {330, 99}, {331, 84}, {332, 85}, {333, 86}, {334, 87}, {335, 88}, {336, 103},
                {340, 225}, {341, 224}, {342, 226}, {343, 227}, {344, 229}, {345, 228}, {346, 230}, {347, 231}, {348, 101}
        };
        for (int[] e : m) GLFW_TO_SDL[e[0]] = e[1];
        for (int i = 0; i < 9; i++) GLFW_TO_SDL[49 + i] = 30 + i;
        for (int i = 0; i < 26; i++) GLFW_TO_SDL[65 + i] = 4 + i;
        for (int i = 0; i < 12; i++) GLFW_TO_SDL[290 + i] = 58 + i;
        for (int i = 0; i < 12; i++) GLFW_TO_SDL[302 + i] = 104 + i;
        for (int i = 0; i < 9; i++) GLFW_TO_SDL[321 + i] = 89 + i;
    }

    /** Old (GLFW key / 0-based mouse) bind -> current scancode / 1-based mouse bind. -1 when it has no equivalent. */
    public static int fromGlfw(int key) {
        if (key == -1) return -1;
        if (isMouse(key)) {
            int b = mouseButton(key);
            return forMouse(b == 0 ? InputConstants.MOUSE_BUTTON_LEFT : b == 1 ? InputConstants.MOUSE_BUTTON_RIGHT
                    : b == 2 ? InputConstants.MOUSE_BUTTON_MIDDLE : b + 1);
        }
        return key >= 0 && key < GLFW_TO_SDL.length ? GLFW_TO_SDL[key] : -1;
    }

    /** Old GLFW modifier bits (1 shift, 2 ctrl, 4 alt, 8 super) -> InputConstants.MOD_*. */
    public static int modsFromGlfw(int mods) {
        return ((mods & 1) != 0 ? InputConstants.MOD_SHIFT : 0)
                | ((mods & 2) != 0 ? InputConstants.MOD_CONTROL : 0)
                | ((mods & 4) != 0 ? InputConstants.MOD_ALT : 0)
                | ((mods & 8) != 0 ? InputConstants.MOD_SUPER : 0);
    }
}
