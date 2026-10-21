// Ported from ADM's p3sim/clear/mobs/RoomDecor.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RoomDecor {
    private RoomDecor() {}

    public static class Spot {
        public final double x, y, z;

        public Spot(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static final Map<String, List<Spot>> MINECARTS = Map.of(
            "Mines", List.of(
                    new Spot(12.47, 41.06, 35.50), new Spot(16.50, 63.06, 30.47), new Spot(16.50, 61.06, 39.63), new Spot(31.88, 40.06, 35.50), new Spot(37.75, 41.06, 35.50),
                    new Spot(49.50, 56.06, 16.50), new Spot(49.50, 57.06, 26.63), new Spot(49.50, 58.06, 31.84), new Spot(51.50, 48.06, 8.50)
            ),
            "Rails", List.of(new Spot(32.50, 70.06, 55.03), new Spot(33.59, 47.00, 45.88), new Spot(33.69, 68.00, 45.94), new Spot(9.47, 73.06, 58.50)),
            "Stairs", List.of(new Spot(10.69, 95.06, 12.50))
    );

    public static double[] toWorld(Placement p, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        WorldPos c = p.toWorld(ix, 0, iz);
        double ox, oz;
        switch (p.rot) {
            case SOUTH -> { ox = fx; oz = fz; }
            case NORTH -> { ox = 1 - fx; oz = 1 - fz; }
            case WEST -> { ox = 1 - fz; oz = fx; }
            case EAST -> { ox = fz; oz = 1 - fx; }
            default -> throw new IllegalStateException();
        }
        return new double[]{c.x + ox, c.z + oz};
    }

    public static class StillMinecart extends Minecart {
        public StillMinecart(net.minecraft.world.level.Level level) { super(EntityType.MINECART, level); }

        @Override public void tick() {}

        @Override public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) { return InteractionResult.PASS; }

        @Override public boolean shouldBeSaved() { return false; }
    }

    private static final class Placed {
        final double x, y, z;
        Minecart e = null;

        Placed(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final ArrayList<Placed> placed = new ArrayList<>();
    private static boolean planned = false;

    public static void tick(Run run) {
        if (!planned) {
            planned = true;
            for (PlacedRoom room : run.plan.rooms) {
                List<Spot> spots = MINECARTS.get(room.name);
                if (spots == null) continue;
                Placement pl = room.placement;
                if (pl == null) continue;
                for (Spot s : spots) { double[] w = toWorld(pl, s.x, s.z); placed.add(new Placed(w[0], s.y, w[1])); }
            }
        }
        for (Placed p : placed) {
            if (p.e != null && !p.e.isRemoved()) continue;
            if (!RunBlocks.isLoaded((int) Math.floor(p.x), (int) Math.floor(p.z))) continue;
            Ec.safely("ads room decor", () -> {
                StillMinecart m = new StillMinecart(Sim.level());
                m.snapTo(p.x, p.y, p.z, 0f, 0f);
                m.setInvulnerable(true); m.setSilent(true); m.setNoGravity(true);
                p.e = Sim.spawn(m);
            });
        }
    }

    public static void clear() {
        for (Placed p : placed) if (p.e != null) p.e.discard();
        placed.clear(); planned = false;
    }
}
