package com.teslamaps.features;

// SkyblockAddons: Stop Rat Pet Sounds + Leather Backpack Opening Sound
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

public final class SoundMutes {
    private static long backpackOpenMs = 0L;
    // Skyblocker's MuteEndermenSounds
    private static final java.util.Set<String> ENDERMAN = java.util.Set.of(
            "minecraft:entity.enderman.ambient", "minecraft:entity.enderman.death", "minecraft:entity.enderman.hurt",
            "minecraft:entity.enderman.scream", "minecraft:entity.enderman.stare");

    private SoundMutes() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!TeslaMapsConfig.get().muteBackpackOpen || !(screen instanceof AbstractContainerScreen<?>)) return;
            if (StringUtil.strip(screen.getTitle().getString()).contains("Backpack")) backpackOpenMs = System.currentTimeMillis();
        });
    }

    public static boolean active(TeslaMapsConfig c) {
        return c.muteRatPet || c.muteBackpackOpen || c.muteEndermanEnd || c.muteJerrychine || c.muteFlowerOfTruth || c.muteVacuum || c.muteHoeLevelUp || c.gardenMuteComposter || c.muteTreasureChest;
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
        if (c.muteEndermanEnd && ENDERMAN.contains(id) && com.teslamaps.features.theend.EndProtector.inArea()) return true;
        if (c.muteRatPet) {
            // rat = bat squeak + chicken steps next to the player, with no real bat/chicken there
            if (id.equals("minecraft:entity.bat.ambient")) return near(mc, s, 6) && none(mc, s, EntityType.BAT);
            if (!c.muteRatPetSqueakOnly && id.equals("minecraft:entity.chicken.step"))
                return near(mc, s, 6) && none(mc, s, EntityType.CHICKEN);
        }
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
