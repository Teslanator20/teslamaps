package com.teslamaps.profileviewer.data;

/**
 * Data for a single pet.
 */
public class PetData {
    private final String type;
    private final String tier;  // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    private final double exp;
    private final boolean active;
    private final String heldItem;
    private final String skin;
    private final int level;
    private final double progress;

    // Pet XP table (per rarity offset)
    private static final int[] RARITY_OFFSET = {0, 0, 6, 11, 16, 20, 20};
    private static final double[] PET_XP_TABLE = {
            0, 100, 210, 330, 460, 605, 765, 940, 1130, 1340, 1570, 1820, 2095, 2395, 2725, 3085, 3485, 3925, 4415,
            4955, 5555, 6215, 6945, 7745, 8625, 9585, 10635, 11785, 13045, 14425, 15935, 17585, 19385, 21345, 23475,
            25785, 28285, 30985, 33905, 37065, 40485, 44185, 48185, 52535, 57285, 62485, 68185, 74485, 81485, 89285,
            97985, 107685, 118485, 130485, 143785, 158485, 174685, 192485, 211985, 233285, 256485, 281685, 309085,
            338885, 371285, 406485, 444685, 486085, 530885, 579285, 631485, 687685, 748085, 812885, 882285, 956485,
            1035685, 1120385, 1211085, 1308285, 1412485, 1524185, 1643885, 1772085, 1909285, 2055985, 2212685,
            2380385, 2560085, 2752785, 2959485, 3181185, 3418885, 3673585, 3946285, 4237985, 4549685, 4883385,
            5765240
    };

    public PetData(String type, String tier, double exp, boolean active, String heldItem, String skin) {
        this.type = type;
        this.tier = tier;
        this.exp = exp;
        this.active = active;
        this.heldItem = heldItem;
        this.skin = skin;

        // Calculate level
        int rarityIndex = getRarityOrdinal();
        int offset = rarityIndex < RARITY_OFFSET.length ? RARITY_OFFSET[rarityIndex] : 0;
        int maxLevel = 100;

        int lvl = 1;
        double xpForLevel = 0;
        for (int i = offset; i < PET_XP_TABLE.length && lvl < maxLevel; i++) {
            xpForLevel += PET_XP_TABLE[i];
            if (exp >= xpForLevel) {
                lvl++;
            } else {
                break;
            }
        }
        this.level = lvl;

        // Calculate progress
        if (lvl < maxLevel && offset + lvl < PET_XP_TABLE.length) {
            double prevLevelXp = 0;
            for (int i = offset; i < offset + lvl - 1 && i < PET_XP_TABLE.length; i++) {
                prevLevelXp += PET_XP_TABLE[i];
            }
            double nextLevelXp = prevLevelXp + PET_XP_TABLE[offset + lvl - 1];
            this.progress = (exp - prevLevelXp) / (nextLevelXp - prevLevelXp);
        } else {
            this.progress = 1.0;
        }
    }

    public String getType() { return type; }
    public String getTier() { return tier; }
    public double getExp() { return exp; }
    public boolean isActive() { return active; }
    public String getHeldItem() { return heldItem; }
    public String getSkin() { return skin; }
    public int getLevel() { return level; }
    public double getProgress() { return progress; }

    /**
     * Get display name (formatted type).
     */
    public String getDisplayName() {
        // Convert GOLDEN_DRAGON to Golden Dragon
        String[] parts = type.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Get rarity ordinal for sorting.
     * COMMON=0, UNCOMMON=1, RARE=2, EPIC=3, LEGENDARY=4, MYTHIC=5
     */
    public int getRarityOrdinal() {
        return switch (tier.toUpperCase()) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    /**
     * Get rarity color (ARGB).
     */
    public int getRarityColor() {
        return switch (tier.toUpperCase()) {
            case "COMMON" -> 0xFFAAAAAA;
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            default -> 0xFFFFFFFF;
        };
    }
}
