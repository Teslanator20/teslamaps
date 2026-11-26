package com.teslamaps.features;

// SkyblockAddons: Stop Rat Pet Sounds + Leather Backpack Opening Sound
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;

public final class SoundMutes {
    private static long backpackOpenMs = 0L;
    // Skyblocker's MuteEndermenSounds
    private static final java.util.Set<String> ENDERMAN = java.util.Set.of(
            "minecraft:entity.enderman.ambient", "minecraft:entity.enderman.death", "minecraft:entity.enderman.hurt",
            "minecraft:entity.enderman.scream", "minecraft:entity.enderman.stare");

    // SkyHanni's MuteBanshee
    private static final float[] BANSHEE_PITCHES = {0.1904762f, 0.20634921f, 0.22222222f, 0.23809524f, 0.25396827f,
            0.26984128f, 0.2857143f, 0.3015873f, 0.31746033f, 0.33333334f, 0.34920636f, 0.36507937f, 0.3809524f,
            0.3968254f, 0.41269842f, 0.42857143f, 0.44444445f, 0.46031746f, 0.47619048f, 0.4920635f};

    private SoundMutes() {}

    public static void register() {
        com.teslamaps.registry.Feature.of("HighHeatMute").section("Mining").config("Mining", b -> {
            TeslaMapsConfig c = b.config();
            b.toggle("Mute High Heat Sound", () -> c.muteHighHeat, v -> c.muteHighHeat = v)
                    .desc("Drops the Crystal Hollows heat warning (low wolf panting) below y 66.")
                    .tags("heat", "crystal hollows", "wolf", "pant", "mute", "sound");
        });
        com.teslamaps.registry.Feature.of("FishingMutes").section("Fishing").config("Fishing", b -> {
            TeslaMapsConfig c = b.config();
            b.label("Fishing Sounds");
            b.toggle("Mute Banshee", () -> c.muteBanshee, v -> c.muteBanshee = v)
                    .desc("Drops the Banshee's ghast wail in Backwater Bayou")
                    .tags("banshee", "ghast", "bayou", "mute", "sound");
            b.toggle("Mute Rod Cast Sound", () -> c.muteCastSound, v -> c.muteCastSound = v)
                    .desc("Silences the bobber throw sound when casting a rod")
                    .tags("cast", "bobber", "throw", "rod", "mute", "sound");
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!TeslaMapsConfig.get().muteBackpackOpen || !(screen instanceof AbstractContainerScreen<?>)) return;
            if (StringUtil.strip(screen.getTitle().getString()).contains("Backpack")) backpackOpenMs = System.currentTimeMillis();
        });
    }

    public static boolean active(TeslaMapsConfig c) {
        return c.muteRatPet || c.muteBackpackOpen || c.muteEndermanEnd || c.muteJerrychine || c.muteFlowerOfTruth || c.muteVacuum || c.muteHoeLevelUp || c.gardenMuteComposter || c.muteTreasureChest || c.muteHighHeat
                || c.muteBanshee || c.muteCastSound;
    }

    public static boolean shouldMute(TeslaMapsConfig c, SoundInstance s, String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.isSameThread()) return false;
        if (com.teslamaps.features.gardenqol.GardenSafety.muteComposter(id)) return true;
        if (c.muteBackpackOpen && id.equals("minecraft:block.chest.open")
                && System.currentTimeMillis() - backpackOpenMs < 500 && near(mc, s, 1.5)) return true;
        // Skytils' disableJerrygunSounds / disableTruthFlowerSounds signatures
        if (c.muteJerrychine) {
            if (id.equals("minecraft:entity.villager.yes") && approx(s.getVolume(), 0.35f)) return true;
            if (id.equals("minecraft:entity.villager.trade") && approx(s.getVolume(), 0.5f)) return true;
        }
        if (c.muteFlowerOfTruth && id.equals("minecraft:entity.generic.eat")
                && approx(s.getPitch(), 0.6984127f) && approx(s.getVolume(), 1.0f)) return true;
        if (c.muteHoeLevelUp && id.equals("minecraft:block.portal.travel") && approx(s.getPitch(), 1.4920635f)
                && c.section("Garden") && com.teslamaps.utils.SkyblockUtils.isInGarden()) return true;
        // SkyHanni's muteVacuum
        if (c.muteVacuum && id.equals("minecraft:entity.wither.shoot") && (com.teslamaps.utils.SkyblockUtils.isInGarden() || com.teslamaps.features.foraging.Foraging.inTorrhus())) return true;
        // SkyHanni's PowderChestTimer muteChestDiscover / muteChestOpen
        if (c.muteTreasureChest && (id.equals("minecraft:entity.player.levelup") || id.equals("minecraft:block.chest.open"))
                && approx(s.getPitch(), 1f) && approx(s.getVolume(), 1f) && c.section("Mining")
                && com.teslamaps.utils.SkyblockUtils.isInArea("Crystal Hollows")) {
            if (id.equals("minecraft:entity.player.levelup") ? c.muteTreasureChestDiscover : c.muteTreasureChestOpen) return true;
        }
        // SkyHanni's HighHeatSound
        if (c.muteHighHeat && id.equals("minecraft:entity.wolf.pant") && s.getPitch() < 0.55f && mc.player.getY() <= 65
                && c.section("Mining") && com.teslamaps.utils.SkyblockUtils.isInArea("Crystal Hollows")) return true;
        if (c.muteCastSound && id.equals("minecraft:entity.fishing_bobber.throw") && c.section("Fishing")) return true;
        if (c.muteBanshee && id.equals("minecraft:entity.ghast.ambient") && banshee(s.getPitch()) && c.section("Fishing")
                && com.teslamaps.utils.SkyblockUtils.isInArea("Backwater Bayou")) return true;
        if (c.muteEndermanEnd && ENDERMAN.contains(id) && com.teslamaps.features.theend.EndProtector.inArea()) return true;
        if (c.muteRatPet) {
            // rat = bat squeak + chicken steps next to the player, with no real bat/chicken there
            if (id.equals("minecraft:entity.bat.ambient")) return near(mc, s, 6) && none(mc, s, EntityTypes.BAT);
            if (!c.muteRatPetSqueakOnly && id.equals("minecraft:entity.chicken.step"))
                return near(mc, s, 6) && none(mc, s, EntityTypes.CHICKEN);
        }
        return false;
    }

    private static boolean banshee(float pitch) {
        for (float p : BANSHEE_PITCHES) if (Math.abs(pitch - p) < 0.001f) return true;
        return false;
    }

    private static boolean approx(float a, float b) { return Math.abs(a - b) < 0.01f; }

    private static boolean near(Minecraft mc, SoundInstance s, double r) {
        if (s.isRelative()) return true;
        return mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()) <= r * r;
    }

    private static boolean none(Minecraft mc, SoundInstance s, EntityType<?> type) {
        AABB box = new AABB(s.getX() - 2, s.getY() - 2, s.getZ() - 2, s.getX() + 2, s.getY() + 2, s.getZ() + 2);
        return mc.level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> e.getType() == type).isEmpty();
    }
}
