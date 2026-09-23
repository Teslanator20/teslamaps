package com.teslamaps.features;

import net.minecraft.client.multiplayer.chat.GuiMessage;

/**
 * The chat message under the mouse, as the chat renderer itself saw it (idea from Devonian's
 * CopyChat). Right whatever the chat position, scale, tabs or padding rows are.
 */
public final class ChatHover {

    private static GuiMessage hovered;
    private static boolean seen;

    private ChatHover() {}

    /** Start of every focused chat render. */
    public static void beginFrame() {
        hovered = null;
    }

    public static void onLine(GuiMessage.Line line) {
        hovered = line.parent();
        seen = true;
    }

    /** Null = the mouse is over no line. */
    public static GuiMessage hovered() {
        return hovered;
    }

    /** False until the hook fired once; a chat mod that replaces the renderer never calls it. */
    public static boolean works() {
        return seen;
    }
}
