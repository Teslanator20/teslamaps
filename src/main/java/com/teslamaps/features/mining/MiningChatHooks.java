package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.network.chat.Component;

/** Mining chat hides/rewrites, called from ChatFilter.process. */
public final class MiningChatHooks {

    private MiningChatHooks() {}

    /** TRUE = hide, FALSE = rewritten (stop), null = continue. */
    public static Boolean handle(Component message, String text, String stripped) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.section("Mining")) return null;
        String t = stripped.trim();
        if (c.mineshaftAnnounce && MineshaftAnnounce.hideEcho(t)) return true;
        if (MiningEvents.hideChat(t)) return true;
        return PowderMiningFilter.handle(message, text, stripped);
    }
}
