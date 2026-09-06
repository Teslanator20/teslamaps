package com.teslamaps.utils;

// Skyblock skill leveling table plus Hypixel's overflow levels past the cap.
// Overflow: from 60 on, a level costs 7.6M and grows by 600k, the step doubling
// every 10 levels.
public final class SkillXp {

    // xp for the step INTO that level (index 1 = level 1)
    private static final long[] STEP = {
        0,
        50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
        5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
        300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
        1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
        2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
        4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000
    };

    public static final long XP_TO_60 = 111_672_425L;

    private SkillXp() {}

    /** level, xp into that level, xp the level needs, xp gained past the cap */
    public record Level(int level, long current, long needed, long overflowTotal) {}

    public static long xpRequiredForLevel(int desiredLevel) {
        if (desiredLevel <= 60) {
            long total = 0;
            for (int i = 1; i <= desiredLevel && i < STEP.length; i++) total += STEP[i];
            return total;
        }
        long total = XP_TO_60;
        long step = 7_600_000L;
        long slope = 600_000L;
        for (int level = 60; level < desiredLevel; ) {
            total += step;
            level++;
            step += slope;
            if (level % 10 == 0) slope *= 2;
        }
        return total;
    }

    private static long step(int level) {
        return level >= 1 && level < STEP.length ? STEP[level] : 4_000_000L;
    }

    /** Level incl. overflow for a total xp amount, capped at the skill's real cap. */
    public static Level calculate(long totalXp, int maxSkillCap) {
        long xp = totalXp;
        int level = 0;
        int maxLevel = Math.min(maxSkillCap, 60);
        while (level < maxLevel && xp >= step(level + 1)) {
            xp -= step(level + 1);
            level++;
        }

        long needed = step(level + 1);
        long overflowTotal = 0;

        if (level >= maxLevel) {
            long capXp = xpRequiredForLevel(maxLevel);
            if (totalXp >= capXp) {
                overflowTotal = totalXp - capXp;
                xp = overflowTotal;
                // levels between the cap and 60 still use the normal table
                long forNext = step(maxLevel) + (step(maxLevel + 1) - step(maxLevel));
                while (xp >= forNext && level < 60) {
                    level++;
                    xp -= forNext;
                    forNext += step(level + 1) - step(level);
                }
                if (level >= 60) {
                    long slope = 600_000L;
                    forNext = 7_600_000L;
                    while (xp >= forNext) {
                        level++;
                        xp -= forNext;
                        forNext += slope;
                        if (level % 10 == 0) slope *= 2;
                    }
                }
                needed = forNext;
            }
        }

        return new Level(level, xp, needed, overflowTotal);
    }
}
