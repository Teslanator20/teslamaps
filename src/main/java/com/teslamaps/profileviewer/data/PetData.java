package com.teslamaps.profileviewer.data;

public class PetData {
    private final String type;
    private final String tier;  // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    private final double exp;
    private final boolean active;
    private final String heldItem;
    private final String skin;

    // common, uncommon, rare, epic, legendary, mythic - where each rarity starts in the table
    private static final int[] RARITY_OFFSET = {0, 6, 11, 16, 20, 20};
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

    private final int candyUsed;

    public PetData(String type, String tier, double exp, boolean active, String heldItem, String skin, int candyUsed) {
        this.candyUsed = candyUsed;
        this.type = type;
        this.tier = tier;
        this.exp = exp;
        this.active = active;
        this.heldItem = heldItem;
        this.skin = skin;
        PetRepo.load();
    }

    private int cachedLevel = -1;
    private double cachedProgress;
    private boolean cachedFromRepo;

    /** Levels off the repo table; recomputed once the repo finished loading. */
    private void ensureLevel() {
        boolean repo = PetRepo.isLoaded();
        if (cachedLevel >= 0 && cachedFromRepo == repo) return;

        int max = repo ? PetRepo.maxLevel(type) : 100;
        double remaining = exp;
        int level = 1;
        long cost = 0;

        while (level < max) {
            cost = repo ? PetRepo.costFor(type, tier, level) : fallbackCost(level);
            if (cost <= 0 || remaining < cost) break;
            remaining -= cost;
            level++;
        }

        cachedLevel = level;
        cachedProgress = (level >= max || cost <= 0) ? 1.0 : remaining / cost;
        cachedFromRepo = repo;
    }

    private long fallbackCost(int level) {
        int index = RARITY_OFFSET[Math.min(getRarityOrdinal(), RARITY_OFFSET.length - 1)] + level - 1;
        return index >= 0 && index < PET_XP_TABLE.length ? (long) PET_XP_TABLE[index] : 0;
    }

    public int getMaxLevel() {
        return PetRepo.isLoaded() ? PetRepo.maxLevel(type) : 100;
    }

    public String getType() { return type; }
    public String getTier() { return tier; }
    public double getExp() { return exp; }
    public boolean isActive() { return active; }
    public String getHeldItem() { return heldItem; }
    public String getSkin() { return skin; }
    public int getLevel() { ensureLevel(); return cachedLevel; }
    public double getProgress() { ensureLevel(); return cachedProgress; }

    public String getDisplayName() {
        String[] parts = type.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

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

    public int getCandyUsed() { return candyUsed; }

    /** The paragraph colour hypixel uses for this rarity. */
    public String getRarityCode() {
        return switch (tier.toUpperCase()) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            default -> "§f";
        };
    }


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
