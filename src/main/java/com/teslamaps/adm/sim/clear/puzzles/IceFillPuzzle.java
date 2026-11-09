// Ported from ADM's p3sim/clear/puzzles/IceFillPuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Fill in the world, as the recorded Hypixel rooms run it. The two reward chests are behind the gate from the moment a player steps
 * in. A wrong step collapses the floor being filled for COLLAPSE_TICKS ticks and only that player is told. The third floor done starts
 * the opening: gate to barrier then air, the chests open by themselves, the room turns green 15 ticks after the last step.
 * UNMEASURED: which chest opens first is taken from one recorded room (the east one, in the room's frame).
 */
public class IceFillPuzzle extends WorldPuzzle {
    private IceFillLogic logic = null;
    private List<WorldPos> chests = List.of();

    /** All the columns with ice per floor (patch cells and the cells under an obstacle), in the room's frame. */
    private List<Set<IceFillLogic.Cell>> iceCells = List.of();

    /** The obstacles standing on the ice at feet level, as the room has them (read at build). */
    private List<Map<IceFillLogic.Cell, BlockState>> obstacles = List.of();

    /** The column each player stood in when last seen on the active floor. */
    private final Map<UUID, Pair<Integer, Integer>> standing = new HashMap<>();

    /** True from a collapse until the floor comes back. */
    private boolean collapsed = false;

    /** True from the last step of the third floor: the opening is running, nothing more can be stepped. */
    private boolean opening = false;

    public IceFillPuzzle(PuzzleContext context) { super(PuzzleKinds.ICE_FILL, context); }

    @Override public int wakeDelayTicks() { return 0; }

    @Override public boolean rewardSignalAtGreen() { return false; }

    @Override public List<WorldPos> rewardSpots() { return chests; }

    @Override protected void build() {
        List<WorldPos> cs = new ArrayList<>();
        for (String n : List.of("chest1", "chest2")) {
            List<WorldPos> a = context().anchor(n);
            if (!a.isEmpty()) cs.add(a.get(0));
        }
        chests = cs;
        Block ice = Blocks.ICE;
        IceFillLogic.CellPredicate isIce = (x, y, z) -> {
            BlockState s = stateAt(x, y, z);
            return s != null && s.getBlock() == ice;
        };
        IceFillLogic.CellPredicate isAir = this::isAirAt;
        List<Set<IceFillLogic.Cell>> fl = new ArrayList<>();
        for (int f = 0; f <= 2; f++) fl.add(IceFillLogic.scan(f, isIce, isAir));
        logic = new IceFillLogic(fl);
        List<Set<IceFillLogic.Cell>> ic = new ArrayList<>();
        for (int f = 0; f <= 2; f++) ic.add(IceFillLogic.scanIce(f, isIce));
        iceCells = ic;
        List<Map<IceFillLogic.Cell, BlockState>> obs = new ArrayList<>();
        for (int f = 0; f <= 2; f++) {
            Set<IceFillLogic.Cell> patch = logic.floors.get(f);
            Map<IceFillLogic.Cell, BlockState> m = new LinkedHashMap<>();
            for (IceFillLogic.Cell c : iceCells.get(f)) {
                if (patch.contains(c)) continue;
                BlockState s = stateAt(c.x, c.y, c.z);
                if (s != null && !s.isAir()) m.put(c, s);
            }
            obs.add(m);
        }
        obstacles = obs;
        List<Integer> chosen = IceFillLogic.patterns(isAir);
        boolean anyBad = false;
        List<Integer> bad = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) if (chosen.get(i) < 0) { anyBad = true; bad.add(i); }
        if (anyBad) {
            String variant = context().room.variantId != null ? "/" + context().room.variantId : "";
            Ec.LOG.warn("[ads] Ice Fill in " + context().room.designId + variant + ": floors " + bad + " match none of Odin's patterns, so Odin cannot solve them");
        }
    }

    /** A player steps in: the chests are there behind the gate. */
    @Override protected void wake() { placeChests(); }

    private void placeChests() {
        BlockState state = roomState(CHEST_STATE);
        if (state == null) return;
        for (WorldPos c : chests) setWorld(c, state);
    }

    @Override public void onTick() {
        IceFillLogic l = logic;
        if (l == null) return;
        if (status() != PuzzleStatus.UNSOLVED || collapsed || opening) return;
        for (ServerPlayer p : playersInRoom()) {
            int f = l.activeFloor();
            if (f < 0) return;
            int feetY = (int) Math.floor(p.getY() + 0.01);
            if (feetY != IceFillLogic.FLOOR_Y.get(f)) continue;
            Pair<Integer, Integer> col = local((int) Math.floor(p.getX()), (int) Math.floor(p.getZ()));
            if (col == null) continue;
            // Only a move onto a different column counts: standing still, or jumping on the spot, is not a step.
            if (Objects.equals(standing.get(p.getUUID()), col)) continue;
            standing.put(p.getUUID(), col);
            IceFillLogic.Step r = l.step(col.first, feetY, col.second);
            switch (r.outcome) {
                case NOTHING -> {}
                case COVERED -> {
                    IceFillLogic.Cell c = r.cell;
                    setAt(c.x, c.y - 1, c.z, Blocks.PACKED_ICE.defaultBlockState());
                    WorldPos w = world(c.x, c.y - 1, c.z);
                    if (w != null) Sim.level().levelEvent(2001, new BlockPos(w.x, w.y, w.z), Block.getId(Blocks.WOOL.white().defaultBlockState()));
                    if (r.floorDone) floorDone(f, c, p.getUUID());
                    if (l.solved()) return;
                }
                case WRONG_BLOCK -> { sayTo(p.getUUID(), PuzzleLines.ICE_WRONG_BLOCK); collapse(f); return; }
                case DIAGONAL -> { sayTo(p.getUUID(), PuzzleLines.ICE_DIAGONAL); collapse(f); return; }
            }
        }
    }

    // ---- a floor done

    /** A floor is done: three rising harp notes at the cell (and on the third floor, the opening). */
    private void floorDone(int f, IceFillLogic.Cell at, UUID by) {
        WorldPos w = world(at.x, at.y - 1, at.z);
        if (w != null) {
            Vec3 pos = new Vec3(w.x + 0.5, w.y + 0.5, w.z + 0.5);
            float[] harp = FLOOR_HARP.get(f);
            for (int i = 0; i < harp.length; i++) {
                float pitch = harp[i];
                after(5 * i, () -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1.0f, pitch, pos, SoundSource.BLOCKS));
            }
        }
        if (logic != null && logic.solved()) startOpening(by);
    }

    /** The third floor is done at tick T: the gate, the chests, the jingle, and the green mark at T + 15. */
    private void startOpening(UUID by) {
        opening = true;
        List<WorldPos> gate = gateCells();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (WorldPos g : gate) setWorld(g, barrier);
        after(GATE_AIR_TICKS, () -> { for (WorldPos g : gate) setWorld(g, Blocks.AIR.defaultBlockState()); });
        if (!gate.isEmpty()) {
            WorldPos g = gate.get(0);
            Vec3 at = new Vec3(g.x + 0.5, g.y + 0.5, g.z + 0.5);
            for (int i = 0; i < GATE_AIR_TICKS / 5 + 1; i++) after(5 * i, () -> Sim.sound(SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, 2.0f, 0.4920635f, at));
        }
        // The east chest of the room opens first, the other 3 ticks after.
        List<WorldPos> order = new ArrayList<>(chests);
        order.sort(Comparator.comparingInt(this::canonicalX).reversed());
        for (int i = 0; i < order.size(); i++) {
            WorldPos c = order.get(i);
            after(FIRST_CHEST_TICKS + CHEST_GAP_TICKS * i, () -> openChest(c));
        }
        after(GREEN_TICKS, () -> solve(by));
    }

    /** The canonical x of a world position (to tell the east chest from the west one whatever way the room is turned). */
    private int canonicalX(WorldPos p) {
        Pair<Integer, Integer> l = local(p.x, p.z);
        return l != null ? l.first : 0;
    }

    /** The bars of the gate in the way of the chests (x 13 to 17, y 74 to 79, z 27 in the room's frame). */
    private List<WorldPos> gateCells() {
        List<WorldPos> out = new ArrayList<>();
        for (int y = GATE_Y_FROM; y <= GATE_Y_TO; y++) for (int x = GATE_X_FROM; x <= GATE_X_TO; x++) {
            WorldPos w = world(x, y, GATE_Z);
            if (w != null) out.add(w);
        }
        return out;
    }

    /** A chest opens by itself: Odin's signal, the lid sound and the five harp notes. */
    private void openChest(WorldPos c) {
        lidSignal(c);
        Vec3 at = new Vec3(c.x + 0.5, c.y + 0.5, c.z + 0.5);
        Sim.sound(SoundEvents.CHEST_OPEN, 0.5f, 0.92f, at, SoundSource.BLOCKS);
        for (int i = 0; i < ChestJingle.PITCHES.length; i++) {
            float pitch = ChestJingle.PITCHES[i];
            after(ChestJingle.FIRST_NOTE + ChestJingle.NOTE_GAP * i, () -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1.0f, pitch, at));
        }
    }

    // ---- a wrong step

    /** Floor f falls: ice and obstacles become air in one go; COLLAPSE_TICKS ticks later they all come back. */
    private void collapse(int f) {
        IceFillLogic l = logic;
        if (l == null) return;
        collapsed = true;
        l.collapse();
        standing.clear();
        BlockState iceState = Blocks.ICE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        ServerLevel level = Sim.level();
        for (IceFillLogic.Cell c : iceCells.get(f)) {
            setAt(c.x, c.y - 1, c.z, air);
            WorldPos w = world(c.x, c.y - 1, c.z);
            if (w != null) level.levelEvent(2001, new BlockPos(w.x, w.y, w.z), Block.getId(iceState));
        }
        for (Map.Entry<IceFillLogic.Cell, BlockState> e : obstacles.get(f).entrySet()) {
            IceFillLogic.Cell c = e.getKey();
            WorldPos w = world(c.x, c.y, c.z);
            if (w == null) continue;
            BlockPos pos = new BlockPos(w.x, w.y, w.z);
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, e.getValue());
            falling.disableDrop();
            track(falling);
            falling.addTag(Sim.TAG);
            after(FALL_TICKS, () -> forget(falling));
        }
        after(COLLAPSE_TICKS, () -> {
            for (IceFillLogic.Cell c : iceCells.get(f)) setAt(c.x, c.y - 1, c.z, iceState);
            for (Map.Entry<IceFillLogic.Cell, BlockState> e : obstacles.get(f).entrySet()) setAt(e.getKey().x, e.getKey().y, e.getKey().z, e.getValue());
            collapsed = false;
        });
    }

    // ---- solved and reset

    /** The room as it stands once solved: the gate open, the chests in place. */
    @Override public void applySolved() {
        for (WorldPos c : gateCells()) setWorld(c, Blocks.AIR.defaultBlockState());
        placeChests();
    }

    @Override public void onReset() { if (logic != null) logic.reset(); }

    /** A collapsed floor comes back this long after it fell (measured: 59 to 61 ticks in 36 of 36). */
    public static final int COLLAPSE_TICKS = 60;

    /** The falling blocks of the obstacles are removed this long after the collapse (measured: 17). */
    public static final int FALL_TICKS = 17;

    /** The gate's bars are barrier at the last step and air this many ticks later. */
    public static final int GATE_AIR_TICKS = 26;

    /** The first chest opens this long after the last step (median 9) and the second CHEST_GAP_TICKS after the first (median 3). */
    public static final int FIRST_CHEST_TICKS = 9;
    public static final int CHEST_GAP_TICKS = 3;

    /** The room turns green this long after the last step (median 15). */
    public static final int GREEN_TICKS = 15;

    /** The three harp notes that play at the cell when floor 1, 2 and 3 is done. */
    public static final List<float[]> FLOOR_HARP = List.of(
            new float[]{1.1904762f, 1.2857143f, 1.3968254f},
            new float[]{1.4920635f, 1.5873016f, 1.6984127f},
            new float[]{1.7936508f, 1.8888889f, 2.0f});

    /** The gate in the room's frame: x 13 to 17, y 74 to 79 (the rows above stay bars), z 27. */
    public static final int GATE_X_FROM = 13;
    public static final int GATE_X_TO = 17;
    public static final int GATE_Y_FROM = 74;
    public static final int GATE_Y_TO = 79;
    public static final int GATE_Z = 27;

    /** The chests face the gate, which is toward canonical north. */
    private static final String CHEST_STATE = "minecraft:chest[facing=north,type=single,waterlogged=false]";
}
