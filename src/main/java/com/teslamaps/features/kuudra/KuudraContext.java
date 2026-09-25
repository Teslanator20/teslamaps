package com.teslamaps.features.kuudra;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.MagmaCube;

/** Kuudra run state shared by the Kuudra helpers: in Kuudra, phase, run start, the boss entity. */
public final class KuudraContext {

    public static final int NONE = 0, SUPPLIES = 1, BUILD = 2, STUN = 3, LAIR = 4;

    private static int phase;
    private static long runStart;
    private static boolean inKuudra;
    private static MagmaCube boss;
    private static Object level;
    private static int ticks;

    private KuudraContext() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Kuudra", "Skyblock", true);
        Feature.of("KuudraContext").section("Kuudra")
                .when(KuudraContext::needed)
                .tickAlways(KuudraContext::tick)
                .serverChat(KuudraContext::onChat);
    }

    private static boolean needed() {
        TeslaMapsConfig c = cfg();
        return c.kuudraProfitTracker || c.kuudraRunTracker || c.kuudraArrowPoisonWarn || c.kuudraBackboneTimer
                || c.kuudraFeroProc || c.kuudraRendDamage || c.kuudraSupplySplits || c.kuudraDpsHelper
                || c.kuudraCannonBox || c.kuudraMenuBlock || c.kuudraSupplySound || c.kuudraSupplyHelper
                || c.kuudraSupplyProgress || c.kuudraAlreadyPicking;
    }

    public static boolean inKuudra() { return inKuudra; }
    public static int phase() { return inKuudra ? phase : NONE; }
    public static long runStart() { return runStart; }
    public static MagmaCube boss() { return boss; }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            phase = NONE;
            runStart = 0;
            boss = null;
            KuudraSupplyHelper.reset();
        }
        if (mc.level == null) { inKuudra = false; return; }
        if (++ticks % 10 != 0) return;
        inKuudra = SkyblockUtils.getArea().contains("Kuudra");
        if (!inKuudra) { boss = null; return; }
        if (boss != null && (boss.isRemoved() || !boss.isAlive())) boss = null;
        if (boss == null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof MagmaCube m && m.getSize() == 30
                        && m.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0) { boss = m; break; }
            }
        }
    }

    private static void onChat(String raw) {
        if (!inKuudra) return;
        switch (StringUtil.stripTrim(raw)) {
            case "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> {
                phase = SUPPLIES;
                runStart = System.currentTimeMillis();
                KuudraSupplyHelper.reset();
                KuudraSupplySplits.reset();
            }
            case "[NPC] Elle: OMG! Great work collecting my supplies!" -> phase = BUILD;
            case "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> phase = STUN;
            case "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!" -> phase = LAIR;
            default -> {}
        }
    }
}
