package com.teslamaps.features;

// Ported from Skysoft's ChatPeek.kt (display mode + focused height), SkyHanni's ChatPeek as concept
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;

/** Holding the key shows the chat as if it were open, without a chat screen. */
public final class ChatPeek {

    private ChatPeek() {}

    public static boolean active() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.chatPeekKey == -1 || !c.section("Chat")) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.player != null && KeyNames.isDown(c.chatPeekKey);
    }

    public static ChatComponent.DisplayMode mode(ChatComponent.DisplayMode mode) {
        return mode == ChatComponent.DisplayMode.BACKGROUND && active() ? ChatComponent.DisplayMode.FOREGROUND : mode;
    }

    public static int height(int original) {
        return active() ? ChatComponent.getHeight(Minecraft.getInstance().options.chatHeightFocused().get()) : original;
    }
}
