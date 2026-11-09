package com.teslamaps.esp;

import com.teslamaps.utils.ScoreboardUtils;

public class CorpseESP {

    private static boolean mineshaft = false;
    private static long mineshaftTime = 0;

    static boolean inMineshaft() {
        long now = System.currentTimeMillis();
        if (now - mineshaftTime < 500) return mineshaft;
        mineshaftTime = now;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(line).contains("Mineshaft")) return mineshaft = true;
        }
        return mineshaft = false;
    }
}
