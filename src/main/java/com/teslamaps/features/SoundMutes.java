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
        return c.muteRatPet || c.muteBackpackOpen || c.muteEndermanEnd;
    }

    public static boolean shouldMute(TeslaMapsConfig c, SoundInstance s, String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (c.muteBackpackOpen && id.equals("minecraft:block.chest.open")
                && System.currentTimeMillis() - backpackOpenMs < 500 && near(mc, s, 1.5)) return true;
        if (c.muteEndermanEnd && ENDERMAN.contains(id) && com.teslamaps.features.theend.EndProtector.inArea()) return true;
        if (c.muteRatPet) {
            // rat = bat squeak + chicken steps next to the player, with no real bat/chicken there
            if (id.equals("minecraft:entity.bat.ambient")) return near(mc, s, 6) && none(mc, s, EntityType.BAT);
            if (!c.muteRatPetSqueakOnly && id.equals("minecraft:entity.chicken.step"))
                return near(mc, s, 6) && none(mc, s, EntityType.CHICKEN);
        }
        return false;
    }

    private static boolean near(Minecraft mc, SoundInstance s, double r) {
        if (s.isRelative()) return true;
        return mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()) <= r * r;
    }

    private static boolean none(Minecraft mc, SoundInstance s, EntityType<?> type) {
        AABB box = new AABB(s.getX() - 2, s.getY() - 2, s.getZ() - 2, s.getX() + 2, s.getY() + 2, s.getZ() + 2);
        return mc.level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> e.getType() == type).isEmpty();
    }
}
