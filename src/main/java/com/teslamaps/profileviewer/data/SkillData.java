package com.teslamaps.profileviewer.data;

/**
 * Data for a single skill.
 */
public class SkillData {
    private final String displayName;
    private final double totalXp;
    private final String apiKey;
    private final int level;
    private final double progressXp;
    private final double xpToNext;
    private final double progress;

    // XP required for each level (cumulative)
    // Standard skill caps at 60, some at 50
    private static final double[] SKILL_XP_TABLE = {
            0, 50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425,
            9925, 14925, 22425, 32425, 47425, 67425, 97425, 147425, 222425, 322425,
            522425, 822425, 1222425, 1722425, 2322425, 3022425, 3822425, 4722425, 5722425, 6822425,
            8022425, 9322425, 10722425, 12222425, 13822425, 15522425, 17322425, 19222425, 21222425, 23322425,
            25522425, 27822425, 30222425, 32722425, 35322425, 38072425, 40972425, 44072425, 47472425, 51172425,
            55172425, 59472425, 64072425, 68972425, 74172425, 79672425, 85472425, 91572425, 97972425, 104672425,
            111672425
    };

    // Runecrafting/Social use different table
    private static final double[] RUNECRAFTING_XP_TABLE = {
            0, 50, 150, 275, 435, 635, 885, 1200, 1600, 2100,
            2725, 3510, 4510, 5760, 7325, 9325, 11825, 14950, 18950, 23950,
            30200, 38050, 47850, 60100, 75400, 94500
    };

    public SkillData(String displayName, double totalXp, String apiKey) {
        this.displayName = displayName;
        this.totalXp = totalXp;
        this.apiKey = apiKey;

        // Calculate level from XP
        double[] table = apiKey.equals("runecrafting") || apiKey.equals("social") ?
                RUNECRAFTING_XP_TABLE : SKILL_XP_TABLE;
        int maxLevel = table.length - 1;

        int lvl = 0;
        for (int i = 0; i < table.length; i++) {
            if (totalXp >= table[i]) {
                lvl = i;
            } else {
                break;
            }
        }
        this.level = lvl;

        // Calculate progress to next level
        if (lvl < maxLevel) {
            double currentLevelXp = table[lvl];
            double nextLevelXp = table[lvl + 1];
            this.progressXp = totalXp - currentLevelXp;
            this.xpToNext = nextLevelXp - currentLevelXp;
            this.progress = xpToNext > 0 ? progressXp / xpToNext : 1.0;
        } else {
            this.progressXp = 0;
            this.xpToNext = 0;
            this.progress = 1.0;
        }
    }

    public String getDisplayName() { return displayName; }
    public double getTotalXp() { return totalXp; }
    public String getApiKey() { return apiKey; }
    public int getLevel() { return level; }
    public double getProgressXp() { return progressXp; }
    public double getXpToNext() { return xpToNext; }
    public double getProgress() { return progress; }

    /**
     * Get formatted XP string (e.g., "1.2M / 5M").
     */
    public String getFormattedProgress() {
        if (xpToNext == 0) return "MAX";
        return formatNumber(progressXp) + " / " + formatNumber(xpToNext);
    }

    private static String formatNumber(double num) {
        if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000);
        } else {
            return String.format("%.0f", num);
        }
    }
}
