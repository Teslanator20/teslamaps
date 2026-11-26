package com.teslamaps.features.pets;

// Ported from Skysoft's ActivePetTracker/PetTabWidgetReader/PetXpEstimator/PetXpRules (trimmed)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SkillTracker;
import com.teslamaps.utils.SkillXp;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the equipped pet from the tab widget (primary xp source), chat summon,
 * autopet and level-up lines. Estimates pet xp from skill gains between widget
 * refreshes with the Skysoft multiplier table.
 */
public class PetTracker {

    static final Pattern SUMMON = Pattern.compile(
            "§aYou summoned your §r§(?<rarity>.)(?<pet>[^§]+)(?:§r(?<skin>§. ✦))?§r§a!");
    private static final Pattern DESPAWN = Pattern.compile("You despawned your (?<pet>.+)!");
    // plain-text fallbacks when the colored patterns miss a format change
    private static final Pattern SUMMON_PLAIN = Pattern.compile("You summoned your (?<pet>.+?)(?<skin> ✦)?!");
    private static final Pattern AUTOPET_PLAIN = Pattern.compile(
            "Autopet equipped your \\[Lvl (?<level>\\d+)] (?:\\[\\d+✦] )?(?<pet>.+?)(?<skin> ✦)?! VIEW RULE");
    static final Pattern AUTOPET = Pattern.compile(
            "§cAutopet §eequipped your §7\\[Lvl (?<level>\\d+)] "
                    + "(?:(?:§.)+\\[(?:§.)*\\d+(?:§.)*(?<altskin>§.✦)(?:§.)*] )?"
                    + "(?:§.)*§(?<rarity>.)(?<pet>[^§]+?)(?<skin>§. ✦)?§e! §a§lVIEW RULE");
    static final Pattern LEVEL_UP = Pattern.compile(
            "Your (?<pet>.+) leveled up to level (?<level>\\d+)!");
    private static final Pattern TAB_NAME = Pattern.compile(
            " \\[Lvl (?<level>[\\d,]+)] (?:\\[\\d+(?<altskin>✦)] )?(?<pet>[\\w -]+?)(?<skin> ✦)?$");
    private static final Pattern TAB_XP = Pattern.compile(
            " (?:(?<max>MAX LEVEL)|(?:\\+)?(?<current>[\\d,.kM]+)(?:/(?<next>[\\d,.kM]+))? XP(?: \\((?<percentage>[\\d.]+)%\\))?)");

    // current pet
    private static String displayName = null;
    private static String internalName = null;
    private static String rarity = null;      // COMMON..MYTHIC
    private static boolean skin = false;
    private static int level = 0;
    private static double totalXp = -1;       // -1 = unknown
    private static boolean maxed = false;
    private static String heldItemColored = null;
    private static String skinId = null;

    private static long lastEstimateAt = 0;

    // estimate calibration: the xp between two level-up lines is exactly one level's cost
    private static double rawSinceLevel;
    private static int anchorLevel = -1;
    private static final java.util.Map<String, double[]> calibs = new java.util.HashMap<>(); // pet id -> {factor, samples}
    private static double shareCalib = 1;
    private static int shareCalibSamples;
    private static final java.util.Map<PetStore.Pet, double[]> shareRaw = new java.util.IdentityHashMap<>();

    private static double[] calibEntry() {
        return internalName == null ? new double[]{1, 0} : calibs.computeIfAbsent(internalName, k -> new double[]{1, 0});
    }

    public static double calib() { return calibEntry()[0]; }
    public static int calibSamples() { return (int) calibEntry()[1]; }
    public static double shareCalib() { return shareCalib; }
    public static int shareCalibSamples() { return shareCalibSamples; }

    private static boolean usable(double ratio) {
        return ratio > 0.25 && ratio < 4;
    }

    private static void mainLevelUp(int newLevel) {
        if (anchorLevel == newLevel - 1 && rawSinceLevel > 0) {
            double ratio = PetConstants.costForLevel(internalName, rarity, newLevel - 1) / rawSinceLevel;
            double[] c = calibEntry();
            if (usable(ratio)) {
                c[0] = c[1] == 0 ? ratio : c[0] * 0.6 + ratio * 0.4;
                c[1]++;
            }
        }
        anchorLevel = newLevel;
        rawSinceLevel = 0;
    }

    /** Exp share pet hit a new level (from PetLevelTracker), before PetStore pulls its xp into the level. */
    static void shareLevelUp(PetStore.Pet p, int newLevel) {
        double[] r = shareRaw.get(p);
        if (r != null && (int) r[1] == newLevel - 1 && r[0] > 0) {
            double ratio = PetConstants.costForLevel(p.type, p.rarity, newLevel - 1) / r[0];
            if (usable(ratio)) {
                shareCalib = shareCalibSamples == 0 ? ratio : shareCalib * 0.6 + ratio * 0.4;
                shareCalibSamples++;
            }
        }
        shareRaw.put(p, new double[]{0, newLevel});
    }

    // xp samples for the rate, {millis, totalXp}
    private static final java.util.ArrayDeque<double[]> samples = new java.util.ArrayDeque<>();
    private static String lastPetLine = null;

    public static String skinId() { return skinId; }
    public static String lastPetLine() { return lastPetLine; }

    /** Pet xp per hour over the last 5 minutes, 0 when unknown or idle. */
    public static double xpPerHour() {
        if (samples.size() < 2) return 0;
        double[] a = samples.peekFirst(), b = samples.peekLast();
        double dt = (b[0] - a[0]) / 3_600_000.0;
        return dt > 0 ? Math.max(0, (b[1] - a[1]) / dt) : 0;
    }

    private static void sampleXp() {
        if (totalXp < 0) return;
        long now = System.currentTimeMillis();
        samples.addLast(new double[]{now, totalXp});
        while (samples.size() > 2 && now - samples.peekFirst()[0] > 300_000) samples.pollFirst();
    }

    /** Exact data from the Pets menu (PetStore). */
    static void applyStored(PetStore.Pet p) {
        if (p == null || p.type == null) return;
        if (internalName == null || !internalName.equals(p.type)) {
            set(PetConstants.displayName(p.type), p.rarity, p.skin != null, -1);
        }
        rarity = p.rarity;
        skinId = p.skin;
        skin = p.skin != null;
        totalXp = p.exp;
        level = PetConstants.xpToLevel(internalName, rarity, totalXp);
        maxed = level >= PetConstants.maxLevel(internalName);
        heldItemColored = p.heldItem == null ? null : com.teslamaps.profileviewer.data.PetIcons.heldItemName(p.heldItem);
        lastEstimateAt = System.currentTimeMillis();
    }

    public static boolean hasPet() {
        return displayName != null;
    }

    public static String displayName() { return displayName; }
    public static String internalName() { return internalName; }
    public static String rarity() { return rarity; }
    public static boolean hasSkin() { return skin; }
    public static int level() { return level; }
    public static double totalXp() { return totalXp; }
    public static boolean maxed() { return maxed; }

    public static String heldItem() {
        return heldItemColored == null ? null : StringUtil.strip(heldItemColored).trim();
    }

    /** Xp into the current level, needed for the level, and overflow at max. */
    public static double currentLevelXp() {
        if (totalXp < 0 || internalName == null) return 0;
        return totalXp - PetConstants.levelToXp(internalName, rarity, level);
    }

    public static double neededLevelXp() {
        if (internalName == null) return 0;
        return PetConstants.costForLevel(internalName, rarity, level);
    }

    public static double overflowXp() {
        if (totalXp < 0 || internalName == null || !maxed) return 0;
        return totalXp - PetConstants.levelToXp(internalName, rarity, PetConstants.maxLevel(internalName));
    }

    public static void onChatMessage(String raw) {
        PetTitles.onChat(raw);
        if (!enabled()) return;

        Matcher summon = SUMMON.matcher(raw.trim());
        if (summon.matches()) {
            set(summon.group("pet").trim(), rarityFromCode(summon.group("rarity")), summon.group("skin") != null, -1);
            return;
        }

        Matcher autopet = AUTOPET.matcher(raw.trim());
        if (autopet.find()) {
            set(autopet.group("pet").trim(), rarityFromCode(autopet.group("rarity")),
                    autopet.group("skin") != null || autopet.group("altskin") != null,
                    Integer.parseInt(autopet.group("level")));
            return;
        }

        String clean = StringUtil.strip(raw).trim();
        Matcher summonPlain = SUMMON_PLAIN.matcher(clean);
        if (summonPlain.matches()) {
            String pet = summonPlain.group("pet").trim();
            set(pet, rarityBefore(raw, pet), summonPlain.group("skin") != null, -1);
            return;
        }
        Matcher autopetPlain = AUTOPET_PLAIN.matcher(clean);
        if (autopetPlain.find()) {
            String pet = autopetPlain.group("pet").trim();
            set(pet, rarityBefore(raw, pet), autopetPlain.group("skin") != null, Integer.parseInt(autopetPlain.group("level")));
            return;
        }
        Matcher despawn = DESPAWN.matcher(clean);
        if (despawn.matches()) {
            clear();
            return;
        }

        Matcher levelUp = LEVEL_UP.matcher(clean);
        if (levelUp.matches()) {
            String type = PetConstants.internalName(levelUp.group("pet"));
            int newLevel = Integer.parseInt(levelUp.group("level"));
            // the same line comes for exp share pets, which can be the same pet type
            boolean main = type.equals(internalName) && PetLevelTracker.isMain(type, level, newLevel);
            if (main) {
                double floor = PetConstants.levelToXp(internalName, rarity, newLevel);
                double cost = PetConstants.costForLevel(internalName, rarity, newLevel);
                if (totalXp < floor || totalXp > floor + cost * 0.05) totalXp = floor;
                mainLevelUp(newLevel);
                level = newLevel;
                maxed = newLevel >= PetConstants.maxLevel(internalName);
            }
            PetLevelTracker.onLevelUp(levelUp.group("pet"), type, newLevel, main);
        }
    }

    private static int tickCounter = 0;

    public static void tick() {
        if (!enabled()) return;
        if (++tickCounter % 20 != 0) return;
        readTabWidget();
        sampleXp();
    }

    private static boolean widgetMissing;

    public static boolean widgetMissing() { return widgetMissing; }

    private static void readTabWidget() {
        List<String> lines = TabListUtils.getOrderedLines();
        int petIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (StringUtil.strip(lines.get(i)).trim().equals("Pet:")) {
                petIndex = i;
                break;
            }
        }
        widgetMissing = petIndex < 0 && !lines.isEmpty();
        if (petIndex < 0 || petIndex + 1 >= lines.size()) return;

        String petLine = StringUtil.strip(lines.get(petIndex + 1));
        if (petLine.trim().equals("No pet selected")) {
            clear();
            return;
        }
        Matcher name = TAB_NAME.matcher(petLine);
        if (!name.find()) return;

        String pet = name.group("pet").trim();
        int widgetLevel = Integer.parseInt(name.group("level").replace(",", ""));
        boolean widgetSkin = name.group("skin") != null || name.group("altskin") != null;

        String tabRarity = rarityBefore(lines.get(petIndex + 1), pet);
        if (displayName == null || !displayName.equalsIgnoreCase(pet)) {
            set(pet, tabRarity != null ? tabRarity : rarity, widgetSkin, widgetLevel);
        } else if (rarity == null && tabRarity != null) {
            rarity = tabRarity;
        }
        level = widgetLevel;
        skin = widgetSkin;

        // second line: xp, or the held item with xp on the third
        String second = petIndex + 2 < lines.size() ? StringUtil.strip(lines.get(petIndex + 2)) : "";
        Matcher xp = TAB_XP.matcher(second);
        if (!xp.find()) {
            if (!second.trim().isEmpty()) heldItemColored = lines.get(petIndex + 2).trim();
            String third = petIndex + 3 < lines.size() ? StringUtil.strip(lines.get(petIndex + 3)) : "";
            xp = TAB_XP.matcher(third);
            if (!xp.find()) return;
        } else {
            heldItemColored = null;
        }

        if (xp.group("max") != null) {
            maxed = true;
            double maxXp = PetConstants.levelToXp(internalName, rarity, PetConstants.maxLevel(internalName));
            if (totalXp < maxXp) totalXp = maxXp;
            return;
        }
        maxed = false;

        double current = parseCompact(xp.group("current"));
        boolean exact = !xp.group("current").toLowerCase(Locale.ROOT).contains("k")
                && !xp.group("current").contains("M");
        double tolerance = 0.02;
        String pct = xp.group("percentage");
        if (!exact && pct != null && neededLevelXp() > 0) {
            // "1.2M/5.5M (21.8%)": the percentage is ~20x finer than the rounded number
            try {
                current = Double.parseDouble(pct) / 100.0 * neededLevelXp();
                tolerance = 0.002;
            } catch (NumberFormatException ignored) {}
        }
        double read = PetConstants.levelToXp(internalName, rarity, level) + current;

        // stale-widget guard: an estimate within 3s beats a lower rounded read
        long now = System.currentTimeMillis();
        if (now - lastEstimateAt < 3000 && read + 1.0 < totalXp) return;
        if (!exact && totalXp >= 0 && Math.abs(read - totalXp) < neededLevelXp() * tolerance) return;
        totalXp = read;
    }

    // ---- estimation between widget reads (Skysoft PetXpRules, trimmed) ----

    public static void onSkillXp(String skill, double amount) {
        PetLevelTracker.onSkillXp(skill, amount);
        if (!enabled() || !hasPet() || totalXp < 0 || internalName == null) return;
        String type = PetConstants.petType(internalName);
        if (type == null) return;

        double base = baseMultiplier(type, skill);
        if (base <= 0) return;

        double taming = 1.0 + tamingLevel() / 100.0;
        double item = itemMultiplier(skill);
        double custom = PetConstants.customMultiplier(internalName);
        double diana = com.teslamaps.api.ElectionApi.hasPerk("Pet XP Buff") ? 1.35 : 1.0;
        double battle = skill.equalsIgnoreCase("COMBAT") ? 1.0 + attributeLevel("Battle Experience") / 100.0 : 1.0;
        double raw = amount * base * taming * item * custom * diana * PetStore.beastmaster() * battle;
        rawSinceLevel += raw;
        double gain = raw * calib();
        totalXp += gain;
        lastEstimateAt = System.currentTimeMillis();
        shareXp(skill, raw);

        if (!maxed) {
            int newLevel = PetConstants.xpToLevel(internalName, rarity, totalXp);
            if (newLevel > level) level = newLevel;
        }
    }

    /** Skysoft PetXpEstimator.updateExpSharePets: exp share pets get a cut of the active pet's gain. */
    private static void shareXp(String skill, double gain) {
        java.util.List<PetStore.Pet> share = PetStore.expSharePets();
        if (share.isEmpty()) return;
        double rate = tamingLevel() * 0.002
                + (com.teslamaps.api.ElectionApi.hasPerk("Sharing is Caring") ? 0.10 : 0)
                + attributeLevel("Why Not More") / 100.0;
        for (PetStore.Pet p : share) {
            // same type is fine (two Golden Dragons), only the equipped pet itself is skipped
            if (p.type == null || (p == PetStore.active() && p.type.equals(internalName))) continue;
            String type = PetConstants.petType(p.type);
            if (type == null) continue;
            double r = rate + ("PET_ITEM_EXP_SHARE".equals(p.heldItem) && canHoldItem(p.type, p.level) ? 0.25 : 0);
            double base = baseMultiplier(type, skill);
            if (r <= 0 || base <= 0) continue;
            double cut = gain * r * base;
            double[] raw = shareRaw.get(p);
            if (raw != null) raw[0] += cut;
            PetStore.addExp(p, cut * shareCalib);
        }
    }

    /** Attribute (shard) level from the Hunting Box data, 0 when never seen. */
    private static int attributeLevel(String ability) {
        var shard = com.teslamaps.features.hunting.ShardData.byAbilityName(ability);
        if (shard == null) return 0;
        return com.teslamaps.features.hunting.ShardData.tiers(shard, com.teslamaps.features.hunting.ShardData.syphoned(shard))[0];
    }

    private static double baseMultiplier(String petType, String skill) {
        String skillUpper = skill.toUpperCase(Locale.ROOT);
        if (skillUpper.equals("TAMING") || skillUpper.equals("CARPENTRY")
                || skillUpper.equals("RUNECRAFTING") || skillUpper.equals("SOCIAL")) return 0;
        if (petType.equals("ALL")) return 1.0;
        boolean gathering = skillUpper.equals("MINING") || skillUpper.equals("FISHING");
        if (petType.equals(skillUpper)) return gathering ? 1.5 : 1.0;
        if (gathering) return 0.5;
        if (skillUpper.equals("ENCHANTING") || skillUpper.equals("ALCHEMY")) return 1.0 / 12.0;
        return 1.0 / 3.0;
    }

    /** A Golden Dragon below level 100 is still an egg and holds no pet item. */
    static boolean canHoldItem(String type, int lvl) {
        return !"GOLDEN_DRAGON".equals(type) || lvl >= 100;
    }

    private static double itemMultiplier(String skill) {
        if (heldItemColored == null || !canHoldItem(internalName, level)) return 1.0;
        String id = PetConstants.itemId(heldItemColored);
        if (id == null) return 1.0;
        if (id.equals("ALL_SKILLS_SUPER_BOOST")) return 1.2;
        if (id.equals("PET_ITEM_ALL_SKILLS_BOOST_COMMON")) return 1.1;
        String prefix = "PET_ITEM_" + skill.toUpperCase(Locale.ROOT) + "_SKILL_BOOST_";
        if (id.startsWith(prefix)) {
            return switch (id.substring(prefix.length())) {
                case "COMMON" -> 1.2;
                case "UNCOMMON" -> 1.3;
                case "RARE" -> 1.4;
                case "EPIC" -> 1.5;
                default -> 1.0;
            };
        }
        return 1.0;
    }

    private static int tamingLevel() {
        long xp = SkillTracker.totalXp("Taming");
        if (xp <= 0) return 60; // unknown: assume maxed
        return SkillXp.calculate(xp, 60).level();
    }

    // ---- helpers ----

    private static void set(String pet, String newRarity, boolean newSkin, int newLevel) {
        boolean samePet = displayName != null && displayName.equalsIgnoreCase(pet)
                && (newRarity == null || newRarity.equals(rarity));
        if (!samePet && displayName != null) {
            lastPetLine = "§7[Lvl " + level + "] " + rarityColor() + displayName + (skin ? " §d✦" : "");
        }
        displayName = pet;
        internalName = PetConstants.internalName(pet);
        if (newRarity != null) rarity = newRarity;
        skin = newSkin;
        if (!samePet) {
            anchorLevel = -1;
            rawSinceLevel = 0;
            totalXp = -1;
            maxed = false;
            heldItemColored = null;
            skinId = null;
            samples.clear();
            // rarity, skin and xp the tab widget cannot tell, from the Pets menu cache
            Boolean egg = "GOLDEN_DRAGON".equals(internalName) ? pet.trim().endsWith(" Egg") : null;
            PetStore.Pet stored = PetStore.resolve(internalName, newRarity, newLevel, egg);
            if (stored != null) {
                if (newRarity == null) rarity = stored.rarity;
                skinId = stored.skin;
                if (newLevel <= 0 || stored.level == newLevel) totalXp = stored.exp;
                if (newLevel <= 0) level = stored.level;
                if (stored.heldItem != null) {
                    heldItemColored = com.teslamaps.profileviewer.data.PetIcons.heldItemName(stored.heldItem);
                }
            } else if (newRarity == null) {
                rarity = null;
            }
            // a Golden Dragon only exists as Legendary; without a rarity the xp table would be the common one
            if (rarity == null && "GOLDEN_DRAGON".equals(internalName)) rarity = "LEGENDARY";
        }
        if (newLevel > 0) level = newLevel;
    }

    private static void clear() {
        if (displayName != null) {
            lastPetLine = "§7[Lvl " + level + "] " + rarityColor() + displayName + (skin ? " §d✦" : "");
        }
        skinId = null;
        samples.clear();
        displayName = null;
        internalName = null;
        rarity = null;
        skin = false;
        level = 0;
        totalXp = -1;
        maxed = false;
        heldItemColored = null;
    }

    private static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return (c.petOverlay || c.griffinPetWarning || c.petLevelTracker) && c.section("Skills");
    }

    /** Rarity from the color code right before the pet name in a raw tab line, null when none. */
    private static String rarityBefore(String raw, String pet) {
        int at = raw.indexOf(pet);
        for (int i = Math.min(at, raw.length()) - 2; i >= 0; i--) {
            if (raw.charAt(i) != '§') continue;
            String r = rarityFromCode(String.valueOf(Character.toLowerCase(raw.charAt(i + 1))));
            if (r != null) return r;
        }
        return null;
    }

    private static String rarityFromCode(String code) {
        return switch (code) {
            case "f" -> "COMMON";
            case "a" -> "UNCOMMON";
            case "9" -> "RARE";
            case "5" -> "EPIC";
            case "6" -> "LEGENDARY";
            case "d" -> "MYTHIC";
            default -> null;
        };
    }

    public static String rarityColor() {
        if (rarity == null) return "§7";
        return switch (rarity) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§7";
        };
    }

    private static double parseCompact(String s) {
        String clean = s.replace(",", "");
        double mult = 1;
        if (clean.toLowerCase(Locale.ROOT).endsWith("k")) {
            mult = 1000;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("M")) {
            mult = 1_000_000;
            clean = clean.substring(0, clean.length() - 1);
        }
        try {
            return Double.parseDouble(clean) * mult;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
