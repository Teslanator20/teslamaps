package com.teslamaps.utils;

import net.minecraft.client.Minecraft;

/** Hit test for the inline [Pause]/[Reset] buttons the tracker panels put on their last line. */
public class PanelButtons {

    /** True when localX (panel space, already unscaled) sits on that token inside the line. */
    public static boolean hit(String line, String token, double localX) {
        int cut = line.indexOf(token);
        if (cut < 0) return false;
        var font = Minecraft.getInstance().font;
        int left = font.width(line.substring(0, cut));
        return localX >= left - 2 && localX <= left + font.width(token) + 2;
    }
}
