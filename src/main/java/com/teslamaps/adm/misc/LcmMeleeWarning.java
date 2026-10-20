package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

// Ported from ADM's misc/LcmMeleeWarning.kt: Mage in melee reach of a wither boss = the click melees
public final class LcmMeleeWarning {
    private static final String TITLE = "§c§lMELEE RANGE!";
    private static int sinceAlert = Integer.MAX_VALUE;

    private LcmMeleeWarning() {}

    static void register() {
        AdmCore.onWorldLoad(() -> sinceAlert = Integer.MAX_VALUE);
        Feature.of("AdmLcmMeleeWarning").section(Adm.SECTION).when(() -> TeslaMapsConfig.get().admLcmMeleeWarning)
                .tick(LcmMeleeWarning::tick)
                .config(Adm.SECTION, b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("LCM Melee Warning");
                    b.toggle("LCM Melee Warning", () -> c.admLcmMeleeWarning, v -> c.admLcmMeleeWarning = v)
                            .desc("Boss only, as Mage: a title while you are in melee range of a wither boss, where your left click melees instead of firing the beam.");
                    b.toggle("  Play Sound", () -> c.admLcmPlaySound, v -> c.admLcmPlaySound = v)
                            .desc("Play the alert sound with the title.");
                    b.slider("  Repeat Every (ticks)", 1f, 60f, () -> (float) c.admLcmRepeatTicks, v -> c.admLcmRepeatTicks = Math.round(v), 0)
                            .desc("While you stay in melee range, the warning shows again this often.");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var me = mc.player;
        if (me == null || mc.level == null || !DungeonManager.isInBossArea() || !"Mage".equalsIgnoreCase(PlayerTracker.getLocalClass())) {
            sinceAlert = Integer.MAX_VALUE;
            return;
        }
        Vec3 eye = me.getEyePosition();
        double reach = me.entityInteractionRange();
        boolean near = !mc.level.getEntitiesOfClass(WitherBoss.class, me.getBoundingBox().inflate(reach + 4.0), w ->
                w.isAlive() && Witherborn.isBoss(w) && !w.getName().getString().toLowerCase().contains("miner")
                        && w.getBoundingBox().distanceToSqr(eye) <= reach * reach).isEmpty();
        if (sinceAlert < Integer.MAX_VALUE) sinceAlert++;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (near && sinceAlert >= c.admLcmRepeatTicks) {
            AdmMisc.alert(TITLE, c.admLcmPlaySound);
            sinceAlert = 0;
        }
    }
}
