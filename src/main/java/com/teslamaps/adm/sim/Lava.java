package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Lava.kt

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

public final class Lava {
    private Lava() {}

    private static final float HIGH_PITCH = -40.7f;
    private static final double VY = 2.25;
    private static final double VY_HIGH = 3.0375;
    private static final int COOLDOWN = 4;

    private static final class Seen {
        final long ns;
        final boolean inLava;
        final float pitch;
        Seen(long ns, boolean inLava, float pitch) { this.ns = ns; this.inLava = inLava; this.pitch = pitch; }
    }

    private static final PerPlayer<ArrayDeque<Seen>> seen = new PerPlayer<>(ArrayDeque::new);
    private static final PerPlayer<Integer> bouncedAt = new PerPlayer<>(() -> -1000);
    private static final PerPlayer<Integer> hurtAt = new PerPlayer<>(() -> -1000);

    public static void reset() { seen.get().clear(); bouncedAt.set(-1000); hurtAt.set(-1000); }

    public static void tick(ServerPlayer p) {
        if (p.isSpectator() || p.isCreative()) { reset(); return; }
        int now = Fight.serverTick;
        if (p.getRemainingFireTicks() > 0) p.clearFire();
        long nowNs = System.nanoTime();
        ArrayDeque<Seen> seenQ = seen.get();
        seenQ.addLast(new Seen(nowNs, inLava(p), p.getXRot()));
        while (seenQ.size() > 40) seenQ.removeFirst();
        if (now % 2 != 0) return;
        double half = P3Sim.ping() / 2.0;
        long target = nowNs - (long) (half * 1e6);
        Seen s = null;
        for (Iterator<Seen> it = seenQ.descendingIterator(); it.hasNext(); ) {
            Seen e = it.next();
            if (e.ns <= target) { s = e; break; }
        }
        if (s == null) s = seenQ.getFirst();
        if (!s.inLava) return;
        if (now - bouncedAt.get() < COOLDOWN + 2 * (int) Math.round(half / 50.0)) return;
        final int sinceLast = now - bouncedAt.get();
        bouncedAt.set(now);
        final double vy = s.pitch <= HIGH_PITCH ? VY_HIGH : VY;
        Fight.afterMs(half > 0 ? Fight.pingMs() / 2 : 0.0, "lava bounce", () -> { if (!p.isRemoved()) bounce(p, vy, sinceLast); });
    }

    private static boolean inLava(ServerPlayer p) {
        ServerLevel l = Sim.level();
        for (int x = (int) Math.floor(p.getX() - 0.2); x <= (int) Math.floor(p.getX() + 0.2); x++)
            for (int y = (int) Math.floor(p.getY() + 0.4); y <= (int) Math.floor(p.getY() + 1.4); y++)
                for (int z = (int) Math.floor(p.getZ() - 0.2); z <= (int) Math.floor(p.getZ() + 0.2); z++)
                    if (l.getFluidState(new BlockPos(x, y, z)).is(FluidTags.LAVA)) return true;
        return false;
    }

    private static double silentChance(int sinceLast, int sinceHurt) {
        if (sinceHurt < 10) return 0.75;
        if (sinceLast < 50) return 0.07;
        if (sinceLast < 150) return 0.16;
        if (sinceLast < 200) return 0.20;
        if (sinceLast < 250) return 0.32;
        return 0.47;
    }

    private static void bounce(ServerPlayer p, double vy, int sinceLast) {
        p.setDeltaMovement(new Vec3(0.0, vy, 0.0));
        p.syncVelocity = true;
        p.fallDistance = 0.0;
        p.clearFire();
        int now = Fight.serverTick;
        if (KRandom.DEFAULT.nextDouble() < silentChance(sinceLast, now - hurtAt.get())) return;
        hurtAt.set(now);
        Runnable hurt = () -> {
            if (!p.isRemoved()) {
                p.connection.send(new ClientboundHurtAnimationPacket(p));
                Sim.sound(SoundEvents.PLAYER_HURT, 1f, 1f, p.position(), SoundSource.PLAYERS);
            }
        };
        if (KRandom.DEFAULT.nextDouble() < 0.4) Fight.later(1, "lava hurt", hurt::run); else hurt.run();
    }
}
