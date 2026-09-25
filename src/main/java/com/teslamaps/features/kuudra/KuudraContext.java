package com.teslamaps.features.kuudra;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.MagmaCube;

/** Kuudra run state shared by the Kuudra helpers: in Kuudra, phase, run start, the boss entity. */
public final class KuudraContext {

    public static final int NONE = 0, SUPPLIES = 1, BUILD = 2, STUN = 3, LAIR = 4;

    private static int lastPhase;
    private static boolean inKuudra;
    private static MagmaCube boss;
    private static Object level;
    private static int ticks;

    private KuudraContext() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Kuudra", "Skyblock", true);
        Feature.of("KuudraContext").when(KuudraContext::needed).tickAlways(KuudraContext::tick);
    }

    /** Also keeps KuudraBuild's phase state machine running. */
    public static boolean needed() {
        TeslaMapsConfig c = cfg();
        return c.kuudraProfitTracker || c.kuudraRunTracker || c.kuudraArrowPoisonWarn || c.kuudraBackboneTimer
                || c.kuudraFeroProc || c.kuudraRendDamage || c.kuudraSupplySplits || c.kuudraDpsHelper
                || c.kuudraCannonBox || c.kuudraMenuBlock || c.kuudraSupplySound || c.kuudraSupplyHelper
                || c.kuudraSupplyProgress || c.kuudraAlreadyPicking;
    }

    public static boolean inKuudra() { return inKuudra; }
    public static int phase() { return inKuudra ? KuudraBuild.phase : NONE; }
    public static long runStart() { return KuudraSupply.runStart; }
    public static MagmaCube boss() { return boss; }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            boss = null;
            KuudraSupplyHelper.reset();
        }
        if (mc.level == null) { inKuudra = false; return; }
        int p = KuudraBuild.phase;
        if (p != lastPhase) {
            if (p == SUPPLIES) {
                KuudraSupplyHelper.reset();
                KuudraSupplySplits.reset();
            }
            lastPhase = p;
        }
        if (++ticks % 10 != 0) return;
        inKuudra = KuudraBuild.inKuudra();
        if (!inKuudra) { boss = null; return; }
        if (boss != null && (boss.isRemoved() || !boss.isAlive())) boss = null;
        if (boss == null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof MagmaCube m && m.getSize() == 30
                        && m.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0) { boss = m; break; }
            }
        }
    }
}
