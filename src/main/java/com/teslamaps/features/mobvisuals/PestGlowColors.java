package com.teslamaps.features.mobvisuals;

// Ported from Skyblocker's GardenGlowAdder.java, VacuumCache.java and GardenConstants.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.JacobContests;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Pest glow in green for the running contest's crop, dark aqua for the playing Stereo Harmony vinyl. */
public final class PestGlowColors {

    private static final int CONTEST = 0x55FF55;
    private static final int VINYL = 0x00AAAA;

    private static final Map<String, String> CROP_BY_VINYL = Map.ofEntries(
            Map.entry("VINYL_PRETTY_FLY", "Wheat"),
            Map.entry("VINYL_BUZZIN_BEATS", "Sugar Cane"),
            Map.entry("VINYL_CRICKET_CHOIR", "Carrot"),
            Map.entry("VINYL_CICADA_SYMPHONY", "Potato"),
            Map.entry("VINYL_EARTHWORM_ENSEMBLE", "Melon"),
            Map.entry("VINYL_RODENT_REVOLUTION", "Pumpkin"),
            Map.entry("VINYL_WINGS_OF_HARMONY", "Cocoa Beans"),
            Map.entry("VINYL_BEETLE", "Nether Wart"),
            Map.entry("VINYL_DYNAMITES", "Cactus"),
            Map.entry("VINYL_SLOW_AND_GROOVY", "Mushroom"),
            Map.entry("VINYL_FIREFLY", "Moonflower"),
            Map.entry("VINYL_IMAGINE_DRAGONFLIES", "Sunflower"),
            Map.entry("VINYL_PRAY_FOR_ME", "Wild Rose"));

    private static int ticks;
    private static ContainerScreen stereo;
    private static String stereoVinyl;

    private PestGlowColors() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PestGlowColors").section("Mob Visuals").when(() -> cfg().pestGlowContest || cfg().pestGlowVinyl)
                .tick(PestGlowColors::tick);
    }

    private static String crop(Mob mob) {
        return switch (mob) {
            case FLY -> "Wheat";
            case MOSQUITO -> "Sugar Cane";
            case CRICKET -> "Carrot";
            case LOCUST -> "Potato";
            case EARTHWORM -> "Melon";
            case RAT -> "Pumpkin";
            case MOTH -> "Cocoa Beans";
            case BEETLE -> "Nether Wart";
            case MITE -> "Cactus";
            case SLUG -> "Mushroom";
            case FIREFLY -> "Moonflower";
            case DRAGONFLY -> "Sunflower";
            case PRAYING_MANTIS -> "Wild Rose";
            default -> null;
        };
    }

    /** Glow colour for a mob, recoloured when it is a pest matching the contest or the vinyl. */
    static int color(Mob mob, int base) {
        if (mob.area() != MobArea.GARDEN) return base;
        TeslaMapsConfig c = cfg();
        if (!c.pestGlowContest && !c.pestGlowVinyl) return base;
        String crop = crop(mob);
        if (crop == null) return base;
        String contest = c.pestGlowContest ? JacobContests.currentCrop() : null;
        if (contest != null) return crop.equals(contest) ? CONTEST : base;
        if (c.pestGlowVinyl && crop.equals(CROP_BY_VINYL.get(c.pestGlowVinylId))) return VINYL;
        return base;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (cfg().pestGlowVinyl) trackStereo(mc);
        if (++ticks < 20) return;
        ticks = 0;
        if (cfg().pestGlowContest && SkyblockUtils.isInGarden()) JacobContests.readScoreboard();
    }

    private static void trackStereo(Minecraft mc) {
        if (mc.screen instanceof ContainerScreen screen
                && StringUtil.stripTrim(screen.getTitle().getString()).equals("Stereo Harmony")) {
            stereo = screen;
            stereoVinyl = "";
            for (Slot slot : screen.getMenu().slots) {
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                for (String line : ItemUtil.loreLines(stack)) {
                    if (!StringUtil.stripTrim(line).equals("Click to stop playing!")) continue;
                    String id = ItemUtil.skyblockId(stack);
                    if (id != null) stereoVinyl = id;
                }
            }
            return;
        }
        if (stereo == null) return;
        stereo = null;
        if (stereoVinyl != null && !stereoVinyl.equals(cfg().pestGlowVinylId)) {
            cfg().pestGlowVinylId = stereoVinyl;
            TeslaMapsConfig.save();
        }
    }
}
