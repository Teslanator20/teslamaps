package com.teslamaps.safari.capsule;

// Ported from Sparkling Mutuals' contacts/CapsuleContactScene (segment, surfacePatch) + ContactSceneBoundary
import com.teslamaps.safari.capsule.CapsuleContactSolver.Boundary;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Candidate;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Kind;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Segment;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Target;
import com.teslamaps.safari.capsule.CapsulePathModel.Node;
import com.teslamaps.safari.capsule.CapsulePathModel.Stop;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class CapsuleContactScene {

    private static final ShotVector[] FACES = {
            new ShotVector(-1, 0, 0), new ShotVector(1, 0, 0),
            new ShotVector(0, -1, 0), new ShotVector(0, 1, 0),
            new ShotVector(0, 0, -1), new ShotVector(0, 0, 1)};

    private static final Cell UNLOADED = new Cell(null, null, false, false);

    private record Cell(ShotBounds[] solid, ShotBounds[] fluid, boolean water, boolean tooMany) {}

    // cleared every tick, a block change shows up on the next one
    private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
    private final ClientLevel level;
    private final Entity viewer;
    double firstWater = Double.POSITIVE_INFINITY;

    CapsuleContactScene(ClientLevel level, Entity viewer) {
        this.level = level;
        this.viewer = viewer;
    }

    void clear() {
        cells.clear();
        firstWater = Double.POSITIVE_INFINITY;
    }

    private Cell cell(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Cell c = cells.get(key);
        if (c != null) return c;
        if (level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) {
            c = UNLOADED;
        } else {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            List<AABB> solid = state.getCollisionShape(level, pos, CollisionContext.of(viewer)).toAabbs();
            FluidState fluid = state.getFluidState();
            List<AABB> wet = fluid.isEmpty() ? List.of() : fluid.getShape(level, pos).toAabbs();
            if (solid.size() > 32 || wet.size() > 32) {
                c = new Cell(new ShotBounds[0], new ShotBounds[0], false, true);
            } else {
                c = new Cell(moved(solid, pos), moved(wet, pos), fluid.is(FluidTags.WATER), false);
            }
        }
        cells.put(key, c);
        return c;
    }

    private static ShotBounds[] moved(List<AABB> boxes, BlockPos pos) {
        ShotBounds[] out = new ShotBounds[boxes.size()];
        for (int i = 0; i < out.length; i++) out[i] = ShotBounds.of(boxes.get(i).move(pos));
        return out;
    }

    /** cells the segment touches, grown by one in every direction */
    static List<int[]> collisionCells(ShotVector from, ShotVector to) {
        if (!from.finite() || !to.finite() || from.distanceSquared(to) > 1.0000001) return List.of();
        int x0 = (int) Math.floor(Math.min(from.x(), to.x())) - 1, x1 = (int) Math.floor(Math.max(from.x(), to.x())) + 1;
        int y0 = (int) Math.floor(Math.min(from.y(), to.y())) - 1, y1 = (int) Math.floor(Math.max(from.y(), to.y())) + 1;
        int z0 = (int) Math.floor(Math.min(from.z(), to.z())) - 1, z1 = (int) Math.floor(Math.max(from.z(), to.z())) + 1;
        List<int[]> out = new ArrayList<>((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1));
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) out.add(new int[]{x, y, z});
        return out;
    }

    Segment segment(Node a, Node b) {
        List<int[]> around = collisionCells(a.position(), b.position());
        if (around.isEmpty()) return new Segment(null, new Boundary(0, Stop.UNLOADED));
        for (int[] p : around) if (cell(p[0], p[1], p[2]) == UNLOADED) return new Segment(null, new Boundary(0, Stop.UNLOADED));

        Candidate best = null;
        double bestF = Double.POSITIVE_INFINITY, fluidF = Double.POSITIVE_INFINITY, waterF = Double.POSITIVE_INFINITY;
        for (int[] p : around) {
            Cell c = cell(p[0], p[1], p[2]);
            if (c.tooMany()) return new Segment(null, new Boundary(0, Stop.WORK_LIMIT));
            for (ShotBounds body : c.solid()) {
                Double e = body.entry(a.position(), b.position());
                if (e == null || e >= bestF) continue;
                bestF = e;
                ShotVector it = a.position().lerp(b.position(), e);
                double[] gaps = {
                        Math.abs(it.x() - body.min().x()), Math.abs(it.x() - body.max().x()),
                        Math.abs(it.y() - body.min().y()), Math.abs(it.y() - body.max().y()),
                        Math.abs(it.z() - body.min().z()), Math.abs(it.z() - body.max().z())};
                int face = 0;
                for (int i = 1; i < 6; i++) if (gaps[face] > gaps[i]) face = i;
                best = new Candidate(Kind.TERRAIN, a.parameter() + (b.parameter() - a.parameter()) * e, it, FACES[face], null, null);
            }
            for (ShotBounds body : c.fluid()) {
                Double e = body.entry(a.position(), b.position());
                if (e == null) continue;
                if (c.water()) waterF = Math.min(waterF, e);
                else fluidF = Math.min(fluidF, e);
            }
        }
        if (Double.isFinite(waterF) && waterF <= Math.min(bestF, fluidF)) {
            firstWater = Math.min(firstWater, a.parameter() + (b.parameter() - a.parameter()) * waterF);
        }
        return new Segment(best, Double.isFinite(fluidF) ? new Boundary(fluidF, Stop.FLUID) : null);
    }

    /** block and mound boxes around a terrain hit, for the ring; null past the shape limit */
    List<ContactSurfaceBox> surfacePatch(ShotVector center, List<Target> targets) {
        List<int[]> around = collisionCells(center, center);
        for (int[] p : around) if (cell(p[0], p[1], p[2]) == UNLOADED) return null;
        List<ContactSurfaceBox> patch = new ArrayList<>();
        for (int[] p : around) {
            Cell c = cell(p[0], p[1], p[2]);
            if (c.tooMany()) return null;
            for (ShotBounds b : c.solid()) {
                ContactSurfaceBox s = ContactSurfaceBox.axis(b);
                if (s.facesNear(center, 0.65).isEmpty()) continue;
                if (patch.size() == 32) return null;
                patch.add(s);
            }
        }
        for (Target t : targets) {
            if (t.surface() == null || t.surface().facesNear(center, 0.65).isEmpty()) continue;
            if (patch.size() == 32) return null;
            patch.add(t.surface());
        }
        return patch;
    }
}
