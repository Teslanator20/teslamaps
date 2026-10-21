package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Devices.kt

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Devices {
    private static final KRandom R = KRandom.DEFAULT;

    public final GoldorPhase phase;
    public final SimonSays ss;
    public final Lights lights;
    public final Arrows arrows;
    public final Target target;

    public Devices(GoldorPhase phase) {
        this.phase = phase;
        this.ss = new SimonSays();
        this.lights = new Lights();
        this.arrows = new Arrows();
        this.target = new Target();
    }

    public void start() { ss.place(); lights.place(); arrows.place(); target.place(); }
    public void stop() { arrows.remove(); }
    public void tick() { ss.tick(); target.tick(); }

    private Station station(String label) {
        for (Station st : phase.stations) if (st.kind == Station.Kind.DEVICE && st.label.equals(label)) return st;
        throw new java.util.NoSuchElementException("Collection contains no element matching the predicate.");
    }

    public void shownDone(String label) {
        switch (label) {
            case "SS" -> ss.clear();
            case "Lights" -> lights.solve();
            case "Arrows" -> arrows.solve();
            case "Target" -> target.clear();
            default -> {}
        }
    }

    public boolean use(BlockPos pos) { return ss.use(pos) || lights.use(pos); }

    // ------------------------------------------------------------------ Simon Says

    public class SimonSays {
        private final BlockPos START = new BlockPos(110, 121, 91);
        private BlockPos buttonAt(int cell) { return new BlockPos(110, 123 - cell / 4, 92 + cell % 4); }
        private BlockPos lampAt(int cell) { return new BlockPos(111, 123 - cell / 4, 92 + cell % 4); }
        private BlockState BUTTON() {
            return net.minecraft.world.level.block.Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.WEST);
        }

        private final HashMap<String, ServerPlayer> pressers = new HashMap<>();

        private final SimonSaysDevice device = new SimonSaysDevice(new SimonSaysDevice.Io() {
            @Override
            public int[] newSequence() {
                List<Integer> s = SimonSays.this.newSequence();
                int[] out = new int[s.size()];
                for (int i = 0; i < out.length; i++) out[i] = s.get(i);
                return out;
            }

            @Override
            public void lamp(int cell, boolean on) { light(cell, on); }

            @Override
            public void button(int cell, boolean up, boolean pressed) {
                Blocks.set(buttonAt(cell), up ? BUTTON().setValue(ButtonBlock.POWERED, pressed) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }

            @Override
            public void completed(String by) {
                Sim.asPlayer(pressers.get(by), false, () -> { station("SS").complete(by); return null; });
            }
        });

        private boolean done() { return station("SS").done; }

        public void place() {
            Blocks.set(START, BUTTON());
            clear();
        }

        public void clear() {
            device.clear();
            pressers.clear();
            for (int c = 0; c < 16; c++) { light(c, false); Blocks.set(buttonAt(c), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); }
        }

        public void tick() { device.tick(); }

        private void light(int cell, boolean on) {
            Blocks.set(lampAt(cell), on ? net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState() : net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
        }

        public boolean use(BlockPos pos) {
            if (pos.equals(START)) {
                double ping = Fight.pingMs();
                double at = System.nanoTime() / 1e6 + ping;
                Fight.afterMs(ping, "ss start", () -> pressStart(at));
                return true;
            }
            Integer found = null;
            for (int c = 0; c < 16; c++) if (buttonAt(c).equals(pos)) { found = c; break; }
            if (found == null) return false;
            final int cell = found;
            Fight.afterPing("ss press", () -> press(cell));
            return true;
        }

        private void pressStart(double ms) {
            Blocks.set(START, BUTTON().setValue(ButtonBlock.POWERED, true));
            Fight.later(2, "ss start up", () -> { if (phase == Fight.phase) Blocks.set(START, BUTTON()); });
            if (done() || phase.section != 1) return;
            String who = Sim.me();
            pressers.put(who, Sim.acting());
            if (device.start(who, ms) == SimonSaysDevice.Start.RESET) Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 8f, 0f, null, SoundSource.MASTER);
        }

        private List<Integer> newSequence() {
            double luck = P3Sim.ssLuck();
            double x = Math.pow(3000.0, Math.abs(luck));
            int n = Math.max((int) x + (R.nextDouble() < x - (int) x ? 1 : 0), 1);
            List<List<Integer>> draws = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                List<Integer> cells = new ArrayList<>();
                for (int c = 0; c < 16; c++) cells.add(c);
                KRandom.shuffle(cells, R);
                draws.add(new ArrayList<>(cells.subList(0, 4)));
            }
            List<Integer> best = draws.get(0);
            double bestP = path(best);
            for (int i = 1; i < draws.size(); i++) {
                double p = path(draws.get(i));
                if (luck > 0 ? p < bestP : p > bestP) { best = draws.get(i); bestP = p; }
            }
            return best;
        }

        private double path(List<Integer> s) {
            double sum = 0.0;
            for (int i = 0; i + 1 < s.size(); i++) {
                int a = s.get(i), b = s.get(i + 1);
                sum += Math.hypot((double) (a % 4 - b % 4), (double) (a / 4 - b / 4));
            }
            return sum;
        }

        private void press(int cell) {
            String who = Sim.me();
            pressers.put(who, Sim.acting());
            SimonSaysDevice.Press r = device.press(cell, who);
            if (r == SimonSaysDevice.Press.NONE) return;
            Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8f, 4.047619f, null, SoundSource.MASTER);
            if (r != SimonSaysDevice.Press.WRONG) return;
            boolean plain = R.nextDouble() < 0.5 + 0.5 * P3Sim.ssLuck();
            Runnable stale = () -> device.after(1 + R.nextInt(4), () -> device.buttonsBack());
            if (plain) device.after(3 + 26, () -> { device.showRound(2); stale.run(); });
            else {
                List<Integer> gaps = List.of(4, 5, 5, 6, 6, 6, 6, 7, 7, 8);
                int gap = gaps.get(R.nextInt(gaps.size()));
                device.after(3 + 34 - gap, () -> { device.showRound(1); stale.run(); });
                device.after(3 + 34, () -> { device.showRound(2); stale.run(); });
            }
        }
    }

    // ------------------------------------------------------------------ Lights

    public class Lights {
        private final Set<Pair<Integer, Integer>> right = Set.of(Pair.of(58, 133), Pair.of(58, 136), Pair.of(60, 134), Pair.of(60, 135), Pair.of(62, 133), Pair.of(62, 136));
        private final List<Pair<Integer, Integer>> levers = new ArrayList<>();
        private final HashSet<Pair<Integer, Integer>> on = new HashSet<>();

        public Lights() {
            for (int x = 58; x <= 62; x++) for (int y = 133; y <= 136; y++) levers.add(Pair.of(x, y));
        }

        public void place() { on.clear(); on.addAll(right); draw(); }

        public void solve() { on.clear(); on.addAll(right); draw(); }

        private boolean lamp(int x, int y) {
            return on.contains(Pair.of(x, y)) || on.contains(Pair.of(x - 1, y)) || on.contains(Pair.of(x + 1, y)) || on.contains(Pair.of(x, y - 1)) || on.contains(Pair.of(x, y + 1));
        }

        private boolean allLit() {
            for (Pair<Integer, Integer> p : levers) if (!lamp(p.first, p.second)) return false;
            return true;
        }

        private void draw() { draw(0); }

        private void draw(int lampOffDelay) {
            for (Pair<Integer, Integer> p : levers) {
                final int x = p.first, y = p.second;
                BlockPos lever = new BlockPos(x, y, 142);
                BlockState st = Blocks.get(lever);
                if (st != null && st.hasProperty(LeverBlock.POWERED)) Blocks.set(lever, st.setValue(LeverBlock.POWERED, on.contains(Pair.of(x, y))));
                BlockPos lampPos = new BlockPos(x, y, 143);
                boolean lit = lamp(x, y);
                if (lit || lampOffDelay <= 0) Blocks.set(lampPos, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, lit));
                else Fight.later(lampOffDelay, "lamp off", () -> {
                    if (phase == Fight.phase && !lamp(x, y)) Blocks.set(lampPos, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));
                });
            }
        }

        public boolean isLever(BlockPos pos) { return pos.getZ() == 142 && levers.contains(Pair.of(pos.getX(), pos.getY())); }

        public boolean use(BlockPos pos) { return use(pos, false); }

        public boolean use(BlockPos pos, boolean left) {
            if (!isLever(pos)) return false;
            Fight.afterPing("lights lever", () -> {
                Station st = station("Lights");
                if (st.done) return;
                boolean[] credit = {false};
                Fight.later(1, "lights lever", () -> {
                    if (phase != Fight.phase || st.done) return;
                    boolean wasLit = allLit();
                    Pair<Integer, Integer> k = Pair.of(pos.getX(), pos.getY());
                    if (!left) {
                        if (!on.remove(k)) on.add(k);
                        draw(R.nextInt(6) == 0 ? 4 : 3);
                        Sim.sound(SoundEvents.LEVER_CLICK, 0.3f, on.contains(k) ? 0.5873016f : 0.4920635f, Vec3.atCenterOf(pos), SoundSource.BLOCKS);
                    }
                    credit[0] = phase.section >= 1 && phase.section <= 2 && (wasLit || (!left && allLit()));
                });
                Fight.later(2, "lights credit", () -> { if (credit[0] && phase == Fight.phase && !st.done) st.complete(Sim.me(), left); });
            });
            return true;
        }
    }

    public boolean leftClick(BlockPos pos) {
        Station st = phase.leverAt(pos);
        if (st != null) { Fight.afterPing("lever", () -> phase.pullLever(st, Sim.me(), true)); return true; }
        return lights.use(pos, true);
    }

    // ------------------------------------------------------------------ Arrow Align

    public class Arrows {
        private final HashMap<Integer, ItemFrame> frames = new HashMap<>();
        private List<Integer> solution = SOLUTIONS.get(0);

        public void place() {
            remove();
            Board b = idleBoard;
            if (b != null) for (ItemFrame f : b.frames.values()) if (f.isRemoved()) { b = null; break; }
            if (b == null) b = newBoard();
            idleBoard = null;
            solution = SOLUTIONS.get(b.layout);
            frames.putAll(b.frames);
        }

        public void remove() { frames.values().forEach(Entity::discard); frames.clear(); }

        private void lightWall() {
            for (int i : frames.keySet()) Blocks.set(-3, 120 + i % 5, 75 + i / 5, net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState());
        }

        public void solve() {
            lightWall();
            int delay = 0;
            List<Map.Entry<Integer, ItemFrame>> sorted = new ArrayList<>(frames.entrySet());
            sorted.sort((a, b) -> Integer.compare(a.getKey(), b.getKey()));
            for (Map.Entry<Integer, ItemFrame> e : sorted) {
                final int i = e.getKey();
                final ItemFrame f = e.getValue();
                if (solution.get(i) < 0 || f.getRotation() == solution.get(i)) continue;
                Fight.later(delay++, "arrows solve", () -> {
                    if (phase != Fight.phase || !frames.containsValue(f)) return;
                    f.setRotation(solution.get(i));
                    Sim.sound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 1f, f.position(), SoundSource.PLAYERS);
                });
            }
        }

        public boolean use(ItemFrame frame) {
            Integer found = null;
            for (Map.Entry<Integer, ItemFrame> e : frames.entrySet()) if (e.getValue() == frame) { found = e.getKey(); break; }
            if (found == null) return false;
            final int i = found;
            Fight.afterPing("arrow", () -> {
                Station st = station("Arrows");
                if (st.done) return;
                if (solution.get(i) < 0) {
                    frame.setRotation((frame.getRotation() + 1) % 8);
                    Sim.sound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 1f, frame.position(), SoundSource.PLAYERS);
                    return;
                }
                frame.setRotation((frame.getRotation() + 1) % 8);
                Sim.sound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 1f, frame.position(), SoundSource.PLAYERS);
                boolean all = true;
                for (Map.Entry<Integer, ItemFrame> e : frames.entrySet()) {
                    int j = e.getKey();
                    if (!(solution.get(j) < 0 || e.getValue().getRotation() == solution.get(j))) { all = false; break; }
                }
                if (phase.section >= 1 && phase.section <= 3 && all) { lightWall(); st.complete(Sim.me()); }
            });
            return true;
        }

        public boolean owns(Entity e) {
            for (ItemFrame f : frames.values()) if (f == e) return true;
            return false;
        }
    }

    // ------------------------------------------------------------------ the target

    public class Target {
        public final BlockPos PLATE = new BlockPos(63, 127, 35);
        private final List<BlockPos> blocks = cells();
        private int lit = -1;
        private int hits = 0;
        private List<Integer> order = shuffledCells();
        private int grid = R.nextInt(10);
        private boolean shownOnce = false;

        private static List<BlockPos> cells() {
            List<BlockPos> l = new ArrayList<>();
            for (int it = 0; it < 9; it++) l.add(new BlockPos(64 + (it % 3) * 2, 126 + (it / 3) * 2, 50));
            return l;
        }

        private static List<Integer> shuffledCells() {
            List<Integer> l = new ArrayList<>();
            for (int i = 0; i < 9; i++) l.add(i);
            KRandom.shuffle(l, R);
            return l;
        }

        public void place() { reset(); blocks.forEach(b -> Blocks.set(b, net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA.defaultBlockState())); }
        public void clear() { lit = -1; blocks.forEach(b -> Blocks.set(b, net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA.defaultBlockState())); }

        private void reset() { lit = -1; hits = 0; order = shuffledCells(); grid = R.nextInt(10); shownOnce = false; }

        private int current() { return hits < 9 ? order.get(hits) : -1; }

        private boolean held = false;
        private int offAt = -1;

        public boolean onPlate() {
            if (rawOnPlate()) { held = true; offAt = -1; }
            else if (held) {
                if (offAt < 0) offAt = phase.t + 1 + R.nextInt(10);
                if (phase.t >= offAt) { held = false; offAt = -1; }
            }
            return held;
        }

        private String plateBy = null;

        public String botOn = null;

        public BlockPos shown() { return lit >= 0 ? blocks.get(lit) : null; }

        private boolean rawOnPlate() {
            if (botOn != null) { plateBy = botOn; return true; }
            ServerPlayer p = null;
            for (ServerPlayer pl : Sim.players()) {
                AABB b = pl.getBoundingBox();
                if (b.maxX > PLATE.getX() && b.minX < PLATE.getX() + 1 && b.maxZ > PLATE.getZ() && b.minZ < PLATE.getZ() + 1 && b.minY >= PLATE.getY() - 0.01 && b.minY < PLATE.getY() + 0.25) { p = pl; break; }
            }
            if (p == null) return false;
            plateBy = p.getGameProfile().name();
            return true;
        }

        public void tick() {
            Station st = station("Target");
            onPlate();
            if (st.done || phase.section > 4 || Math.floorMod(phase.t + 1 - grid, 10) != 0) return;
            if (onPlate()) {
                if (current() >= 0 && lit != current()) light();
            } else if (lit >= 0 || hits > 0) {
                if (lit >= 0) Blocks.set(blocks.get(lit), net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA.defaultBlockState());
                reset();
            }
        }

        private void light() {
            lit = current();
            shownOnce = true;
            Blocks.set(blocks.get(lit), net.minecraft.world.level.block.Blocks.EMERALD_BLOCK.defaultBlockState());
        }

        public void hit(BlockPos pos) {
            Station st = station("Target");
            if (st.done || current() < 0 || !pos.equals(blocks.get(current()))) return;
            if (!shownOnce) return;
            if (lit < 0 && hits == 0 && !onPlate()) return;
            if (lit == current()) { Blocks.set(pos, net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA.defaultBlockState()); lit = -1; }
            hits++;
            if (hits >= 9) { clear(); st.complete(plateBy != null ? plateBy : Sim.me()); }
        }

        public List<BlockPos> targets() { return blocks; }
    }

    // ------------------------------------------------------------------ companion

    public static class Board {
        public final int layout;
        public final Map<Integer, ItemFrame> frames;

        public Board(int layout, Map<Integer, ItemFrame> frames) { this.layout = layout; this.frames = frames; }
    }

    private static Board idleBoard = null;

    public static void showIdle() {
        if (idleBoard != null) idleBoard.frames.values().forEach(Entity::discard);
        idleBoard = null;
        Set<Pair<Integer, Integer>> right = Set.of(Pair.of(58, 133), Pair.of(58, 136), Pair.of(60, 134), Pair.of(60, 135), Pair.of(62, 133), Pair.of(62, 136));
        for (int x = 58; x <= 62; x++) for (int y = 133; y <= 136; y++) {
            BlockPos lever = new BlockPos(x, y, 142);
            BlockState st = Blocks.get(lever);
            if (st != null && st.hasProperty(LeverBlock.POWERED)) Blocks.set(lever, st.setValue(LeverBlock.POWERED, right.contains(Pair.of(x, y))));
            boolean lit = false;
            for (Pair<Integer, Integer> c : List.of(Pair.of(x, y), Pair.of(x - 1, y), Pair.of(x + 1, y), Pair.of(x, y - 1), Pair.of(x, y + 1))) if (right.contains(c)) { lit = true; break; }
            Blocks.set(new BlockPos(x, y, 143), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, lit));
        }
        idleBoard = newBoard();
    }

    private static Board newBoard() {
        int layout = R.nextInt(1, SOLUTIONS.size());
        List<Integer> solution = SOLUTIONS.get(layout);
        HashMap<Integer, ItemFrame> frames = new HashMap<>();
        for (int i = 0; i < 25; i++) {
            Boolean wool = EXTRAS.get(layout).get(i);
            if (solution.get(i) < 0 && wool == null) continue;
            BlockPos pos = new BlockPos(-2, 120 + i % 5, 75 + i / 5);
            ItemFrame f = new ItemFrame(EntityType.ITEM_FRAME, Sim.level(), pos, Direction.EAST);
            f.setInvulnerable(true);
            if (solution.get(i) >= 0) {
                f.setItem(new ItemStack(Items.ARROW), false);
                f.setRotation(solution.get(i));
            } else f.setItem(
                Boolean.TRUE.equals(wool) ? Terminals.named(Items.LIME_WOOL, "Start", 1, false, ChatFormatting.GREEN)
                    : Terminals.named(Items.RED_WOOL, "End", 1, false, ChatFormatting.RED), false);
            frames.put(i, Sim.spawn(f));
        }
        for (int i = 0; i < 25; i++) {
            boolean lit = frames.containsKey(i) && (solution.get(i) < 0 || R.nextInt(8) == 0);
            Blocks.set(-3, 120 + i % 5, 75 + i / 5, (lit ? net.minecraft.world.level.block.Blocks.SEA_LANTERN : net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA).defaultBlockState());
        }
        Map.Entry<Integer, ItemFrame> best = null;
        int bestD = 0;
        for (Map.Entry<Integer, ItemFrame> e : frames.entrySet()) {
            int j = e.getKey();
            if (solution.get(j) < 0) continue;
            int dy = j % 5; int dz = 4 - j / 5;
            int d = dy * dy + dz * dz;
            if (best == null || d < bestD) { best = e; bestD = d; }
        }
        if (best != null) best.getValue().setRotation((solution.get(best.getKey()) + 7) % 8);
        return new Board(layout, frames);
    }

    public static final List<List<Integer>> SOLUTIONS = List.of(
        List.of(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
        List.of(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
        List.of(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
        List.of(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
        List.of(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        List.of(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        List.of(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        List.of(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1),
        List.of(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1)
    );

    public static final List<Map<Integer, Boolean>> EXTRAS = List.of(
        Map.of(2, false, 22, false),
        Map.of(5, true, 15, true, 14, false),
        Map.of(12, true, 2, false, 22, false),
        Map.of(4, true, 24, true, 12, false),
        Map.of(20, true, 12, false),
        Map.of(20, true, 4, false),
        Map.of(20, true, 22, true, 24, true, 0, false, 2, false, 4, false),
        Map.of(20, true, 0, false),
        Map.of(20, true, 22, true, 24, true, 1, false, 3, false)
    );
}
