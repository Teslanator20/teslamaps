package com.teslamaps.features;

// Ported from Shizo's DiamanteDetector
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.item.Items;

public final class DiamanteAlert {

    private DiamanteAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static Object world;
    private static boolean alerted;
    private static int soundTicks = -1;
    private static int scan;

    public static void register() {
        Feature.of("DiamanteAlert").section("Blood Camp")
                .when(() -> cfg().diamanteAlert)
                .tick(DiamanteAlert::tick)
                .config("Blood Camp", b -> b.toggle("Diamante Giant Alert", () -> cfg().diamanteAlert,
                                v -> cfg().diamanteAlert = v)
                        .desc("F7/M7 blood room: three anvil sounds once per run when the Diamante Giant (full diamond armor) shows up."));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            world = mc.level;
            alerted = false;
            soundTicks = -1;
        }
        if (soundTicks >= 0) {
            if (soundTicks % 4 == 0 && mc.player != null)
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            if (++soundTicks > 8) soundTicks = -1;
        }
        if (alerted || ++scan % 5 != 0 || !DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f == null || f.getLevel() != 7) return;
        for (Giant g : EntityCache.byType(Giant.class)) {
            if (g.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                    && g.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
                    && g.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS)
                    && g.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS)) {
                alerted = true;
                soundTicks = 0;
                return;
            }
        }
    }
}
