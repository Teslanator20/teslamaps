package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/StormEnd.kt

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public final class StormFx {
    private StormFx() {}

    public static void line(BossWither w, String text) {
        Sim.chat("§4[BOSS] Storm§r§c: " + text);
        Sim.sound(SoundEvents.WITHER_AMBIENT, 5f, 1.19f, w != null ? w.pos : null);
        if (w == null) return;
        ArmorStand s = new ArmorStand(EntityType.ARMOR_STAND, Sim.level());
        s.setInvisible(true); s.setNoGravity(true); s.setInvulnerable(true); s.setSilent(true);
        Station.setMarker(s);
        s.setCustomName(Sim.legacy("§4§l" + text));
        s.setCustomNameVisible(true);
        s.snapTo(w.pos.x, w.pos.y + SPEECH_Y, w.pos.z, 0f, 0f);
        Sim.spawn(s);
        speech.add(Pair.of(s, w));
        Fight.later(40, "storm speech", () -> { s.discard(); speech.removeIf(it -> it.first == s); });
    }

    private static final ArrayList<Pair<ArmorStand, BossWither>> speech = new ArrayList<>();
    private static final double SPEECH_Y = 4.1;

    public static void follow() {
        speech.removeIf(it -> it.first.isRemoved());
        for (Pair<ArmorStand, BossWither> it : speech) it.first.snapTo(it.second.pos.x, it.second.pos.y + SPEECH_Y, it.second.pos.z, 0f, 0f);
    }

    public static void clearSpeech() {
        for (Pair<ArmorStand, BossWither> it : speech) it.first.discard();
        speech.clear();
    }

    public static Vec3 bolt(double x, double z, double fromY) { return bolt(x, z, fromY, false); }

    public static Vec3 bolt(double x, double z, double fromY, boolean thunder) {
        Vec3 p = new Vec3(x, ground(x, z, fromY), z);
        LightningBolt b = new LightningBolt(EntityType.LIGHTNING_BOLT, Sim.level());
        b.setVisualOnly(true);
        b.snapTo(p.x, p.y, p.z, 0f, 0f);
        Sim.spawn(b);
        if (thunder) Sim.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 10000f, 0.8f + KRandom.DEFAULT.nextFloat() * 0.2f, p, net.minecraft.sounds.SoundSource.WEATHER);
        return p;
    }

    public static double ground(double x, double z, double fromY) {
        int bx = (int) Math.floor(x); int bz = (int) Math.floor(z);
        int y = (int) Math.floor(fromY);
        for (int i = 0; i < 15; i++) {
            if (Sim.level().getBlockState(new BlockPos(bx, y, bz)).blocksMotion()) return y + 1.0;
            y--;
        }
        return fromY;
    }

    public static String dmg(double v) {
        String s = String.format(Locale.US, "%,.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static void hit(String what, double v) { hit(what, v, false); }

    public static void hit(String what, double v, boolean trueDamage) {
        Sim.chat("§cStorm's§r§7 " + what + " hit you for §r§c" + dmg(v) + "§r§7 " + (trueDamage ? "true " : "") + "damage.");
    }

    public static int burst(Vec3 at) { return Sim.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0); }
}
