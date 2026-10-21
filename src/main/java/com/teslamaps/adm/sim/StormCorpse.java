package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/StormEnd.kt

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public class StormCorpse {
    private final BossWither body;
    private final Vec3 at;
    private int n = 0;
    private double yaw;
    private int nextHurt = 0;
    private final HashMap<Integer, List<Vec3>> bolts = new HashMap<>();

    public StormCorpse(BossWither body) {
        this.body = body;
        this.at = body.pos;
        this.yaw = (double) body.e.getYRot();
        for (int s : SPIRALS) {
            double a0 = KRandom.DEFAULT.nextDouble(360.0);
            for (int j = 0; j < 20; j++) {
                double a = Math.toRadians(a0 + 90.0 * j);
                bolts.computeIfAbsent(s + j * STEP, k -> new ArrayList<>()).add(new Vec3(at.x + 2.0 * j * Math.cos(a), at.y, at.z + 2.0 * j * Math.sin(a)));
            }
        }
    }

    public void start() { step(); }

    private void step() {
        tick();
        n++;
        if (n <= LAST) Fight.later(1, "storm corpse", this::step);
    }

    private void tick() {
        if (n < DEAD) {
            yaw += 40.0;
            double r = Math.toRadians(yaw);
            body.moveTo(at, at.add(-Math.sin(r), 0.0, Math.cos(r)));
            if (n == nextHurt) { Sim.sound(SoundEvents.WITHER_HURT, 15f, 1f, at, net.minecraft.sounds.SoundSource.HOSTILE); nextHurt += KRandom.DEFAULT.nextInt(9, 12); }
        }
        switch (n) {
            case 0, 4 -> Sim.sound(SoundEvents.GENERIC_EXPLODE, 2f, 0.6f, at, net.minecraft.sounds.SoundSource.BLOCKS);
            case 12 -> Sim.sound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 3f, 0.9f, at);
            case DEAD -> body.dieAnim();
            case GONE -> body.remove();
            default -> {}
        }
        List<Vec3> due = bolts.get(n);
        if (due != null) for (Vec3 it : due) StormFx.bolt(it.x, it.z, it.y, true);
    }

    public static final int DEAD = 60;
    public static final int GONE = 80;
    public static final List<Integer> SPIRALS = List.of(0, 20, 40, 60, 75, 90, 105, 120, 135, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290);
    public static final int STEP = 4;
    public static final int LAST = SPIRALS.get(SPIRALS.size() - 1) + 19 * STEP;
}
