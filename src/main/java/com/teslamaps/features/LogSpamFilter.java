package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;

// Concept from Skyblocker's log mixins and Devonian's YggdrasilMinecraftSessionServiceMixin
/** Any thread: reads a config field and the current server address only. */
public final class LogSpamFilter {

    private LogSpamFilter() {}

    public static boolean quiet() {
        return TeslaMapsConfig.get().suppressLogSpam && SkyblockUtils.isOnHypixel();
    }
}
