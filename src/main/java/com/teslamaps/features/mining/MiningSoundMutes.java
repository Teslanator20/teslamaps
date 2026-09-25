package com.teslamaps.features.mining;

// Ported from SkyHanni's MetalDetectorMute.kt and MuteOrganDonor.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Mutes the metal detector ping in the Mines of Divan and the Organ Donor ping once every corpse was found. */
public final class MiningSoundMutes {

    private static final String HARP = "minecraft:block.note_block.harp";
    private static final String DETECTOR = "DWARVEN_METAL_DETECTOR";
    private static final Set<String> CORPSE_HELMETS = Set.of(
            "LAPIS_ARMOR_HELMET", "ARMOR_OF_YOG_HELMET", "MINERAL_HELMET", "VANGUARD_HELMET");
    private static final Pattern CORPSE_LINE = Pattern.compile("\\s*\\w+: (?:NOT )?LOOTED\\s*");

    private static Feature feature;
    private static boolean divan, mineshaft, detectorHeld, allCorpsesFound;
    private static String held = "", previous = "";
    private static final Set<UUID> seenCorpses = new HashSet<>();
    private static int totalCorpses;
    private static Object lastLevel;
    private static int ticks;

    private MiningSoundMutes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MiningSoundMutes").section("Mining")
                .when(() -> cfg().muteMetalDetector || cfg().muteOrganDonor)
                .tick(MiningSoundMutes::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mute Metal Detector", () -> c.muteMetalDetector, v -> c.muteMetalDetector = v)
                            .desc("Mutes the harp ping in the Mines of Divan while the Metal Detector is (or just was) in your hand.")
                            .tags("metal detector", "divan", "sound", "harp");
                    b.toggle("Mute Organ Donor", () -> c.muteOrganDonor, v -> c.muteOrganDonor = v)
                            .desc("Mutes the Organ Donor harp ping in a Glacite Mineshaft once every corpse was seen.")
                            .tags("organ donor", "corpse", "mineshaft", "sound", "harp");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seenCorpses.clear();
            totalCorpses = 0;
            allCorpsesFound = false;
            held = previous = "";
        }
        String now = ItemUtil.skyblockId(mc.player.getMainHandItem());
        if (!now.equals(held)) {
            previous = held;
            held = now;
        }
        detectorHeld = DETECTOR.equals(held) || DETECTOR.equals(previous);

        if (ticks++ % 20 == 0) {
            divan = false;
            if (cfg().muteMetalDetector && SkyblockUtils.isInArea("Crystal Hollows")) {
                for (String line : ScoreboardUtils.getScoreboardLines()) {
                    if (ScoreboardUtils.cleanLine(line).contains("Mines of Divan")) { divan = true; break; }
                }
            }
            mineshaft = cfg().muteOrganDonor && SkyblockUtils.getArea().contains("Mineshaft");
            if (mineshaft) {
                int n = 0;
                for (String line : TabListUtils.getTabListLines()) {
                    if (CORPSE_LINE.matcher(StringUtil.strip(line)).matches()) n++;
                }
                totalCorpses = n;
            }
        }
        if (!mineshaft) return;
        for (ArmorStand as : com.teslamaps.utils.EntityCache.armorStands()) {
            if (as.isRemoved() || seenCorpses.contains(as.getUUID())) continue;
            if (!CORPSE_HELMETS.contains(ItemUtil.skyblockId(as.getItemBySlot(EquipmentSlot.HEAD)))) continue;
            if (mc.player.hasLineOfSight(as)) seenCorpses.add(as.getUUID());
        }
        allCorpsesFound = totalCorpses > 0 && seenCorpses.size() >= totalCorpses;
    }

    public static boolean active(TeslaMapsConfig c) {
        return c.muteMetalDetector || c.muteOrganDonor;
    }

    /** From SoundPlayMixin. */
    public static boolean shouldMute(TeslaMapsConfig c, String id) {
        if (!HARP.equals(id) || feature == null || !feature.active()) return false;
        if (c.muteMetalDetector && divan && detectorHeld) return true;
        return c.muteOrganDonor && mineshaft && allCorpsesFound;
    }
}
