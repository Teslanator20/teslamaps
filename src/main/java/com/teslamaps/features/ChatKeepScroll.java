package com.teslamaps.features;

// Idea from Devonian's DisableChatAutoScroll
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;

/** Reopening chat lands where it was scrolled to instead of the newest line. */
public final class ChatKeepScroll {

    private static int saved;
    private static int added;

    private ChatKeepScroll() {}

    private static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.chatKeepScroll && c.section("Chat");
    }

    /** ChatScreen.removed, before vanilla resets the scroll. */
    public static void onClose(ChatComponent chat) {
        saved = on() ? ((ChatComponentAccessor) chat).teslamaps$getChatScrollbarPos() : 0;
        added = 0;
    }

    /** One wrapped line added while chat is closed. */
    public static void onLine(ChatComponent chat) {
        if (saved > 0 && !chat.isChatFocused()) added++;
    }

    /** ChatScreen.init. */
    public static void onOpen(ChatComponent chat) {
        int pos = saved + added;
        saved = 0;
        added = 0;
        if (pos > 0 && on()) chat.scrollChat(pos);
    }

    /** Lines rebuilt (resize, tab switch): the old offset means nothing now. */
    public static void reset() {
        saved = 0;
        added = 0;
    }
}
