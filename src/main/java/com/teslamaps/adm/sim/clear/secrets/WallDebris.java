// Ported from ADM's p3sim/clear/secrets/WallDebris.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.CarriedBlock;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.run.RunBlocks;
import java.util.ArrayList;
import java.util.Iterator;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.block.state.BlockState;

public final class WallDebris {
    private WallDebris() {}

    public static final double DY = 0.34375;
    public static final double SINK_PER_TICK = 0.155;
    public static final int RIDE_DELAY = 1;
    public static final int LIFE = 42;

    private static final class Piece {
        final Bat bat;
        final CarriedBlock block;
        final double x, y0, z;
        final int cellY;
        int age = 0;

        Piece(Bat bat, CarriedBlock block, double x, double y0, double z, int cellY) {
            this.bat = bat;
            this.block = block;
            this.x = x;
            this.y0 = y0;
            this.z = z;
            this.cellY = cellY;
        }
    }

    private static final ArrayList<Piece> pieces = new ArrayList<>();

    public static void spawn(int x, int y, int z, BlockState state) {
        if (state.isAir() || !RunBlocks.isLoaded(x, z)) return;
        Ec.safely("ads wall debris", () -> {
            double px = x + 0.5, pz = z + 0.5, py = y + DY;
            Bat b = new Bat(EntityTypes.BAT, Sim.level());
            b.setSilent(true); b.setPermanentlyInvulnerable(true); b.setInvisible(true);
            b.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
            b.setNoAi(true); b.setNoGravity(true);
            b.snapTo(px, py, pz, 0f, 0f);
            CarriedBlock f = new CarriedBlock(Sim.level(), state);
            f.snapTo(px, py, pz, 0f, 0f);
            Sim.spawn(b); Sim.spawn(f);
            pieces.add(new Piece(b, f, px, py, pz, y));
        });
    }

    public static void tick() {
        if (pieces.isEmpty()) return;
        Iterator<Piece> it = pieces.iterator();
        while (it.hasNext()) {
            Piece p = it.next();
            p.age++;
            if (p.bat.isRemoved()) { p.block.discard(); it.remove(); continue; }
            if (p.age == RIDE_DELAY && !p.block.isRemoved()) p.block.startRiding(p.bat, true, false);
            double y = p.y0 - SINK_PER_TICK * p.age;
            int cell = (int) Math.floor(y);
            BlockState below = cell < p.cellY ? RunBlocks.get((int) Math.floor(p.x), cell, (int) Math.floor(p.z)) : null;
            boolean floor = cell < p.cellY && below != null && !below.isAir();
            if (p.age >= LIFE || floor) { p.block.discard(); p.bat.discard(); it.remove(); continue; }
            p.bat.snapTo(p.x, y, p.z, 0f, 0f);
        }
    }

    public static void clear() {
        for (Piece p : pieces) { p.block.discard(); p.bat.discard(); }
        pieces.clear();
    }
}
