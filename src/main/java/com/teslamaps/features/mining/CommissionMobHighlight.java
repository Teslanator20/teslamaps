package com.teslamaps.features.mining;

// Mob table ported from SkyHanni's HighlightMiningCommissionMobs.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Mobs that count for an open commission get the configured highlight. */
public final class CommissionMobHighlight {

    private record Target(String commission, Predicate<LivingEntity> matches) {}

    private static final Target[] TARGETS = {
            new Target("Goblin Slayer", e -> name(e).equals("Goblin") || name(e).equals("Weakling")),
            new Target("Star Sentry Puncher", e -> name(e).equals("Crystal Sentry")),
            new Target("Glacite Walker Slayer", e -> name(e).equals("Ice Walker")),
            new Target("Golden Goblin Slayer", e -> name(e).contains("Golden Goblin")),
            new Target("Treasure Hoarder Puncher", e -> name(e).equals("Treasuer Hunter")),
            new Target("Automaton Slayer", e -> e instanceof IronGolem && (hp(e, 15_000) || hp(e, 20_000))),
            new Target("Team Treasurite Member Slayer", e -> name(e).equals("Team Treasurite")),
            new Target("Yog Slayer", e -> e instanceof MagmaCube && hp(e, 35_000)),
            new Target("Thyst Slayer", e -> e instanceof Endermite && hp(e, 5_000)),
            new Target("Corleone Slayer", e -> hp(e, 1_000_000) && name(e).equals("Team Treasurite")),
            new Target("Sludge Slayer", e -> e instanceof Slime && !(e instanceof MagmaCube)
                    && (hp(e, 5_000) || hp(e, 10_000) || hp(e, 25_000))),
    };

    private static final List<Predicate<LivingEntity>> active = new ArrayList<>();
    private static List<LivingEntity> found = List.of();
    private static int lastVersion = -1, ticks;

    private CommissionMobHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static String name(LivingEntity e) {
        return e.getName().getString().trim();
    }

    private static boolean hp(LivingEntity e, int max) {
        float m = e.getMaxHealth();
        return m == max || m == max * 2;
    }

    public static void register() {
        Feature.of("CommissionMobHighlight").section("Mining").when(() -> cfg().commissionMobHighlight)
                .tick(CommissionMobHighlight::tick)
                .world(CommissionMobHighlight::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Commission Mob Highlight", () -> c.commissionMobHighlight, v -> c.commissionMobHighlight = v)
                            .desc("Highlights the mobs your open commissions ask for: Goblins, Ice Walkers, Automatons, Yogs, Thysts, Sludges, Corleone, ...")
                            .tags("commission", "goblin", "automaton", "yog", "sludge", "thyst", "corleone", "esp");
                    b.espStyle("Mob", () -> c.commissionMobEsp);
                });
    }

    private static void tick() {
        if (Commissions.version() != lastVersion) {
            lastVersion = Commissions.version();
            active.clear();
            for (var com : Commissions.active()) {
                if (com.done()) continue;
                for (Target t : TARGETS) if (com.name().startsWith(t.commission())) active.add(t.matches());
            }
        }
        Minecraft mc = Minecraft.getInstance();
        String area = SkyblockUtils.getArea();
        boolean on = !active.isEmpty() && mc.level != null
                && (area.equalsIgnoreCase("Dwarven Mines") || area.equalsIgnoreCase("Crystal Hollows"));
        if (!on) {
            found = List.of();
            return;
        }
        if (++ticks % 20 == 0) {
            List<LivingEntity> out = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) continue;
                for (Predicate<LivingEntity> p : active) {
                    if (p.test(le)) { out.add(le); break; }
                }
            }
            found = out;
        }
        EspStyle style = EspStyle.of(cfg().commissionMobEsp);
        for (LivingEntity e : found) if (e.isAlive()) style.tickEntity(e);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (found.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().commissionMobEsp);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (int i = 0, n = found.size(); i < n; i++) {
            LivingEntity e = found.get(i);
            if (e.isAlive() && !e.isRemoved()) style.drawEntity(pose, e, cam, pt);
        }
    }
}
