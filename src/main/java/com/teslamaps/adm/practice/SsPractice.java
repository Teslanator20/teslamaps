package com.teslamaps.adm.practice;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.practice.mixin.AdmPracticeMinecraftAccessor;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// Ported from ADM's SimonSaysPractice.kt: F7's Simon Says device, drawn in front of you on a keybind.
// Client side only: no block is set, clicks on it are handled here and never sent.
public final class SsPractice {
    private SsPractice() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final int AX = 108, AY = 120, AZ = 94;
    private static final BlockPos START = new BlockPos(110, 121, 91);
    private static final BlockPos EXTRA = new BlockPos(110, 120, 91);
    private static BlockPos fromButton(int n) { return new BlockPos(110, 119 + n, 91); }
    private static BlockPos buttonAt(int cell) { return new BlockPos(110, 123 - cell / 4, 92 + cell % 4); }
    private static BlockPos lampAt(int cell) { return new BlockPos(111, 123 - cell / 4, 92 + cell % 4); }
    private static BlockPos rngButton(int level) { return new BlockPos(110, 121 + level, 96); }

    private static final double[] TOP_ROUNDS = {1.10, 0.80, 1.05};
    private static final int FINAL_ROUND = SimonSaysDevice.MAX_ROUNDS;
    private static final double TURN_AROUND = 100.0;
    private static final String YOU = "you";
    private static final String STORM_DEAD = "[BOSS] Storm: I should have known that I stood no chance.";
    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";

    private static final BlockState BUTTON = Blocks.STONE_BUTTON.defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.WALL)
            .setValue(ButtonBlock.FACING, Direction.WEST)
            .setValue(ButtonBlock.POWERED, false);

    // ------------------------------------------------------------------ placement

    private static final class Placement {
        final ClientLevel level;
        final BlockPos origin;
        final Direction forward;
        final Direction right;
        final Vec3 eye;
        final Rotation rotation;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Map<BlockPos, SignBlockEntity> signs = new LinkedHashMap<>();
        final BlockPos lightPos;

        Placement(ClientLevel level, BlockPos origin, Direction forward, Vec3 eye) {
            this.level = level;
            this.origin = origin;
            this.forward = forward;
            this.right = forward.getClockWise();
            this.eye = eye;
            this.rotation = switch (forward) {
                case SOUTH -> Rotation.CLOCKWISE_90;
                case WEST -> Rotation.CLOCKWISE_180;
                case NORTH -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            this.lightPos = origin.above();
        }

        BlockPos at(int dx, int dy, int dz) {
            return origin.offset(forward.getStepX() * dx + right.getStepX() * dz, dy, forward.getStepZ() * dx + right.getStepZ() * dz);
        }

        BlockPos at(BlockPos real) { return at(real.getX() - AX, real.getY() - AY, real.getZ() - AZ); }

        void setWorld(BlockPos pos, BlockState state) {
            if (state.isAir()) blocks.remove(pos); else blocks.put(pos.immutable(), state.rotate(rotation));
        }

        void set(BlockPos real, BlockState state) { setWorld(at(real), state); }

        AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
            double y = origin.getY() - AY;
            return new AABB(wx(x0, z0), y0 + y, wz(x0, z0), wx(x1, z1), y1 + y, wz(x1, z1));
        }

        private double wx(double x, double z) {
            return origin.getX() + 0.5 + forward.getStepX() * (x - AX - 0.5) + right.getStepX() * (z - AZ - 0.5);
        }

        private double wz(double x, double z) {
            return origin.getZ() + 0.5 + forward.getStepZ() * (x - AX - 0.5) + right.getStepZ() * (z - AZ - 0.5);
        }
    }

    private static Placement placed;

    public static boolean practicing() { return placed != null; }

    // ------------------------------------------------------------------ registration

    public static void register() {
        Feature.of("AdmSsPractice").section(Adm.SECTION).when(() -> cfg().admSsPractice)
                .tickAlways(SsPractice::tick)
                .world(SsPractice::renderOverlay)
                .serverChat(SsPractice::onChat)
                .config("ADM Terminals & Practice", b -> {
                    b.label("SS Practice");
                    b.toggle("SS Practice", () -> cfg().admSsPractice, v -> cfg().admSsPractice = v)
                            .desc("Summons F7's first device (Simon Says) in front of you to practice it anywhere. Client side only: the blocks and your clicks never reach the server. Also plays right/wrong sounds and times your rounds on the real device in P3.");
                    b.keybind("  Summon Keybind", () -> cfg().admSsPracticeKey, v -> cfg().admSsPracticeKey = v)
                            .desc("Summons the device in front of you, and takes it away again. With Infinileap in your hand: /termsim inf instead, the numbers that never ends.");
                    b.slider("  Show Speed", 1f, 3f, () -> cfg().admSsShowSpeed, v -> cfg().admSsShowSpeed = Math.round(v * 4f) / 4f, 2)
                            .desc("How fast the lights are shown (1x = the game's 8 ticks each). Only the lights: the buttons still come back 10 ticks after the last light's tick (on a skip, on the first show's), as in the game.");
                    b.toggle("  Instant Show", () -> cfg().admSsInstantShow, v -> cfg().admSsInstantShow = v)
                            .desc("The whole sequence at once, no lights shown (the solver still marks it). Only the show: the buttons still come back 10 ticks after it (on a skip, after the first show), as in the game. Times are as at 1x.");
                    b.toggle("  Full Block Hitboxes", () -> cfg().admSsFullBlockHitboxes, v -> cfg().admSsFullBlockHitboxes = v)
                            .desc("On the practice device, each button clicks as the whole face of its block (still a button's depth), and the solver boxes cover the whole face too.");
                    b.toggle("  Trigger Bot", () -> cfg().admSsTriggerBot, v -> cfg().admSsTriggerBot = v)
                            .desc("Practice device only (never the real one): presses the button under your crosshair the moment it's the one to press next.");
                    b.toggle("  Follow You", () -> cfg().admSsFollow, v -> cfg().admSsFollow = v)
                            .desc("The device stays the same distance in front of you as you move, and turns around when you do (more than 100 degrees from it): to the side it stays where it is. Off: it stays where you summoned it.");
                    b.slider("  Size", 1f, 3f, () -> cfg().admSsSize, v -> cfg().admSsSize = Math.round(v * 2f) / 2f, 1)
                            .desc("How big the device is drawn, and as many times farther away (2x: twice as big, twice as far), so it looks the same from where you summoned it. Clicks reach it as they would at 1x.");
                });
    }

    private static View frameView(Placement p) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        return view(p, pt, mc.options.getCameraType().isFirstPerson() ? cam : null);
    }

    /** Fabric's collect-submits stage: the device's blocks and signs (only drawn when submitted there). */
    public static void render(LevelRenderContext context) {
        if (!Adm.on() || !cfg().admSsPractice) return;
        Placement p = placed;
        Minecraft mc = Minecraft.getInstance();
        if (p == null || p.level != mc.level) return;
        View v = frameView(p);
        PracticeUtil.safely("ss practice draw", () -> draw(p, v, context, mc.gameRenderer.mainCamera().position()));
    }

    /** World pass: the outline of the block you look at, and the solver's boxes. */
    private static void renderOverlay(PoseStack pose, Vec3 cam) {
        Placement p = placed;
        if (p == null || p.level != Minecraft.getInstance().level) return;
        View v = frameView(p);
        BlockPos target = target();
        BlockState state = target == null ? null : p.blocks.get(target);
        if (state != null) {
            VoxelShape shape = shapeOf(target, state);
            if (!shape.isEmpty()) ESPRenderer.drawBoxOutline(pose, v.box(shape.bounds().move(target)), 0x66000000, 2f, cam, false);
        }
        if (!(cfg().solveSimonSays || AdmSimonSays.overrides())) return;
        if (Inf.on) {
            for (int i = 0; i < Inf.queue.size(); i++) {
                AdmSimonSays.drawSolverBox(pose, v.box(solverBox(p, lampAt(Inf.queue.get(i)))), AdmSimonSays.color(i), cam);
            }
            return;
        }
        List<Integer> shown = device.shown;
        int next = device.next();
        for (int i = next; i < shown.size(); i++) {
            AdmSimonSays.drawSolverBox(pose, v.box(solverBox(p, lampAt(shown.get(i)))), AdmSimonSays.color(i - next), cam);
        }
    }

    // ------------------------------------------------------------------ summon

    private static boolean keyDown;

    private static void summonOrRemove() {
        if (placed != null) { remove(); PracticeUtil.msg("§7SS Practice: removed."); return; }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        Placement p = new Placement(level, player.blockPosition(), player.getDirection(), player.getEyePosition(1f));
        PracticeUtil.safely("ss practice summon", () -> {
            for (int y = 119; y <= 124; y++) for (int z = 91; z <= 96; z++) {
                boolean grid = y >= 120 && y <= 123 && z >= 92 && z <= 95;
                p.set(new BlockPos(111, y, z), grid ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.WOOL.black().defaultBlockState());
            }
            p.set(START, BUTTON);
            p.set(EXTRA, BUTTON);
            for (int n = 3; n <= 4; n++) p.set(fromButton(n), BUTTON);
            sign(p, EXTRA, "Inf", "", Direction.NORTH);
            sign(p, START, "Start", "3x for the skip", Direction.NORTH);
            for (int n = 3; n <= 4; n++) sign(p, fromButton(n), "Start on r" + n, "on repeat", Direction.NORTH);
            for (int lvl = -1; lvl <= 2; lvl++) {
                p.set(rngButton(lvl), BUTTON);
                String a = switch (lvl) { case -1 -> "Harder"; case 0 -> "Normal RNG"; case 1 -> "Easier"; default -> "Easiest"; };
                String b = switch (lvl) { case -1 -> "spread out"; case 0 -> "random"; case 1 -> "tighter"; default -> "tightest"; };
                sign(p, rngButton(lvl), a, b, Direction.SOUTH);
            }
        });
        placed = p;
        turned = 0;
        Set<BlockPos> cells = new HashSet<>();
        for (int i = 0; i < 16; i++) cells.add(p.at(buttonAt(i)));
        gridCells = cells;
        reset();
        markMode(0);
        markRng();
        PracticeUtil.msg("§7SS Practice: summoned. Press the start button §8(left of the grid)§7 to begin, 3 times for the skip. The others are signed. The keybind again takes it away.");
    }

    private static void remove() {
        if (placed == null) return;
        placed = null;
        hit = null;
        reset();
    }

    // ------------------------------------------------------------------ the device

    private record Job(long at, int gen, Runnable run) {}

    private static long tick = 0L;
    private static int gen = 0;
    private static final List<Job> jobs = new ArrayList<>();
    private static void after(int ticks, Runnable run) { jobs.add(new Job(tick + ticks, gen, run)); }

    private static final SimonSaysDevice device = new SimonSaysDevice(new SimonSaysDevice.Io() {
        @Override public int[] newSequence() { return drawSequence(); }
        @Override public void lamp(int cell, boolean on) {
            light(cell, on);
            if (on && firstLight == 0L) firstLight = tick;
        }
        @Override public void button(int cell, boolean up, boolean pressed) { setButton(cell, up, pressed); }
        @Override public int lampTick(int j) { return showTick(j); }
        @Override public void opened(int round) {
            roundUp = tick;
            roundClicks.clear();
            lastClickMs = System.currentTimeMillis();
            if (!accepted.isEmpty()) lastStart = new ArrayList<>(accepted);
        }
        @Override public void completed(String by) { onDone(); }
    });

    private static int showTick(int j) {
        return cfg().admSsInstantShow ? 0 : (int) Math.round(8 * j / (double) cfg().admSsShowSpeed);
    }

    private static final Random rng = new Random();
    private static final boolean[] buttonUp = new boolean[16];
    private static final long[] downUntil = new long[16];
    private static long firstLight = 0L;
    private static long roundUp = 0L;
    private static final List<Double> rounds = new ArrayList<>();
    private static final List<List<Double>> splits = new ArrayList<>();
    private static final List<Double> roundClicks = new ArrayList<>();
    private static long lastClickMs = 0L;
    private static long shownFaster = 0L;
    private static int fails = 0;
    private static boolean running = false;
    private static long runStart = 0L;
    private static final List<Long> accepted = new ArrayList<>();

    private static void reset() {
        stopInf();
        fromRound = 0;
        gen++;
        jobs.clear();
        device.clear();
        running = false;
        Placement p = placed;
        if (p != null) for (int c = 0; c < 16; c++) { p.set(lampAt(c), Blocks.OBSIDIAN.defaultBlockState()); setButton(c, false, false); }
    }

    private static void beginRun() {
        running = true;
        runStart = tick;
        accepted.clear();
        firstLight = 0L; shownFaster = 0L; rounds.clear(); splits.clear(); fails = 0;
    }

    private static void setButton(int cell, boolean up, boolean pressed) {
        Placement p = placed;
        if (p == null) return;
        buttonUp[cell] = up;
        p.set(buttonAt(cell), up ? BUTTON.setValue(ButtonBlock.POWERED, pressed) : Blocks.AIR.defaultBlockState());
    }

    private static void light(int cell, boolean on) {
        Placement p = placed;
        if (p != null) p.set(lampAt(cell), on ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
    }

    private static int[] drawSequence() {
        if (rngLevel == 0) {
            List<Integer> cells = new ArrayList<>();
            for (int i = 0; i < 16; i++) cells.add(i);
            Collections.shuffle(cells, rng);
            int[] out = new int[FINAL_ROUND];
            for (int i = 0; i < FINAL_ROUND; i++) out[i] = cells.get(i);
            return out;
        }
        double k = switch (rngLevel) { case -1 -> -1.0; case 1 -> 1.0; default -> 2.0; };
        List<Integer> out = new ArrayList<>();
        out.add(rng.nextInt(16));
        while (out.size() < FINAL_ROUND) {
            int last = out.get(out.size() - 1);
            List<Integer> left = new ArrayList<>();
            for (int c = 0; c < 16; c++) if (!out.contains(c)) left.add(c);
            double[] w = new double[left.size()];
            double sum = 0;
            for (int i = 0; i < left.size(); i++) {
                int c = left.get(i);
                w[i] = Math.exp(-k * Math.hypot(c / 4 - last / 4, c % 4 - last % 4));
                sum += w[i];
            }
            double r = rng.nextDouble() * sum;
            int pick = left.get(left.size() - 1);
            for (int i = 0; i < left.size(); i++) { r -= w[i]; if (r <= 0) { pick = left.get(i); break; } }
            out.add(pick);
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private static int rngLevel = 0;
    private static Set<BlockPos> gridCells = Set.of();

    private static AABB solverBox(Placement p, BlockPos lamp) {
        if (cfg().admSsFullBlockHitboxes)
            return p.box(lamp.getX() - 0.15, lamp.getY(), lamp.getZ(), lamp.getX() + 0.05, lamp.getY() + 1.0, lamp.getZ() + 1.0);
        return p.box(lamp.getX() - 0.15, lamp.getY() + 0.37, lamp.getZ() + 0.3, lamp.getX() + 0.05, lamp.getY() + 0.63, lamp.getZ() + 0.7);
    }

    private static VoxelShape shapeOf(BlockPos pos, BlockState state) {
        VoxelShape full = fullBlockShape(state, pos);
        return full != null ? full : state.getShape(EmptyBlockGetter.INSTANCE, pos);
    }

    private static VoxelShape fullBlockShape(BlockState state, BlockPos pos) {
        if (!cfg().admSsFullBlockHitboxes || !(state.getBlock() instanceof ButtonBlock) || !gridCells.contains(pos)) return null;
        double d = state.getValue(ButtonBlock.POWERED) ? 1.0 : 2.0;
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case WEST -> Block.box(16 - d, 0.0, 0.0, 16.0, 16.0, 16.0);
            case EAST -> Block.box(0.0, 0.0, 0.0, d, 16.0, 16.0);
            case NORTH -> Block.box(0.0, 0.0, 16 - d, 16.0, 16.0, 16.0);
            default -> Block.box(0.0, 0.0, 0.0, 16.0, 16.0, d);
        };
    }

    private static void markRng() {
        Placement p = placed;
        if (p == null) return;
        for (int lvl = -1; lvl <= 2; lvl++)
            p.set(rngButton(lvl).east(), lvl == rngLevel ? Blocks.WOOL.lightGray().defaultBlockState() : Blocks.WOOL.black().defaultBlockState());
    }

    private static void pressRng(int lvl) {
        Placement p = placed;
        if (p == null) return;
        rngLevel = lvl;
        markRng();
        click(p.at(rngButton(lvl)));
        PracticeUtil.correct();
        p.set(rngButton(lvl), BUTTON.setValue(ButtonBlock.POWERED, true));
        after(2, () -> { if (placed != null) placed.set(rngButton(lvl), BUTTON); });
    }

    private static List<Long> lastStart = List.of(0L, 2L, 4L);

    private static void restart(boolean sound) {
        if (placed == null) return;
        if (Inf.on) { startInf(); return; }
        if (fromRound > 0) { startFrom(fromRound, sound); return; }
        List<Long> presses = lastStart;
        reset();
        beginRun();
        if (sound) PracticeUtil.correct();
        for (long t : presses) after((int) t, () -> {
            accepted.add(t);
            device.start(YOU, 0.0, false);
        });
    }

    // ------------------------------------------------------------------ Inf mode

    private static final class Inf {
        static final int SHOWN = 3;
        static final int FRESH = 6;
        static boolean on = false;
        static final List<Integer> queue = new ArrayList<>();
        static final ArrayDeque<Integer> bag = new ArrayDeque<>();
        static int lastPressed = -1;
        static long lastMs = 0L;
        static final List<Long> gaps = new ArrayList<>();
    }

    private static void startInf() {
        if (placed == null) return;
        reset();
        markMode(1);
        Inf.on = true;
        Inf.queue.clear(); Inf.bag.clear(); Inf.gaps.clear(); Inf.lastPressed = -1; Inf.lastMs = 0L;
        while (Inf.queue.size() < Inf.SHOWN) Inf.queue.add(infPick());
        for (int c = 0; c < 16; c++) setButton(c, true, false);
        PracticeUtil.correct();
    }

    private static void stopInf() {
        if (!Inf.on) return;
        Inf.on = false;
        if (Inf.gaps.isEmpty()) return;
        double avg = Inf.gaps.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        PracticeUtil.msg("§7Inf SS: §f" + (Inf.gaps.size() + 1) + "§7 presses §8· §f" + String.format(Locale.ROOT, "%.3f", avg) + "s§7 between");
    }

    private static int infPick() {
        if (Inf.bag.isEmpty()) {
            List<Integer> candidates = new ArrayList<>();
            for (int c = 0; c < 16; c++) if (!Inf.queue.contains(c) && c != Inf.lastPressed) candidates.add(c);
            Collections.shuffle(candidates, rng);
            List<Integer> fresh = candidates.subList(0, Math.min(Inf.FRESH, candidates.size()));
            Inf.bag.addAll(fresh);
            List<Integer> rest = new ArrayList<>();
            for (int c = 0; c < 16; c++) if (!fresh.contains(c)) rest.add(c);
            Collections.shuffle(rest, rng);
            Inf.bag.addAll(rest);
        }
        return Inf.bag.removeFirst();
    }

    private static void infPress(int cell) {
        if (Inf.queue.isEmpty() || cell != Inf.queue.get(0)) { PracticeUtil.wrong(); return; }
        PracticeUtil.correct();
        long now = System.currentTimeMillis();
        if (Inf.lastMs != 0L) Inf.gaps.add(now - Inf.lastMs);
        Inf.lastMs = now;
        Inf.lastPressed = cell;
        Inf.queue.remove(0);
        Inf.queue.add(infPick());
    }

    private static void markMode(int mode) {
        Placement p = placed;
        if (p == null) return;
        BlockState grey = Blocks.WOOL.lightGray().defaultBlockState();
        BlockState black = Blocks.WOOL.black().defaultBlockState();
        p.set(START.east(), mode == 0 ? grey : black);
        p.set(EXTRA.east(), mode == 1 ? grey : black);
        for (int n = 3; n <= 4; n++) p.set(fromButton(n).east(), mode == n ? grey : black);
    }

    private static void sign(Placement p, BlockPos button, String line1, String line2, Direction side) {
        BlockPos real = button.east().relative(side);
        p.set(real, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, side));
        BlockPos pos = p.at(real);
        BlockState state = p.blocks.get(pos);
        if (state == null) return;
        SignBlockEntity be = new SignBlockEntity(pos, state);
        be.setLevel(p.level);
        p.signs.put(pos, be);
        be.setText(new SignText().setMessage(1, Component.literal(line1)).setMessage(2, Component.literal(line2)), true);
    }

    // ------------------------------------------------------------------ Start on r3 / r4

    private static int fromRound = 0;

    private static void startFrom(int n, boolean sound) {
        reset();
        fromRound = n;
        markMode(n);
        if (sound) PracticeUtil.correct();
        after(6, SsPractice::fromRun);
    }

    private static void fromRun() {
        device.clear();
        firstLight = 0L; shownFaster = 0L; rounds.clear(); splits.clear(); fails = 0;
        device.showRound(fromRound);
    }

    private static void fromDone() {
        for (int i = 0; i < rounds.size(); i++) {
            int r = fromRound + i;
            double t = rounds.get(i);
            Double top = topFor(r);
            if (top == null) continue;
            String c = t <= top ? "§a" : t <= top + 0.3 ? "§e" : "§c";
            List<Double> presses = i < splits.size() ? splits.get(i) : List.of();
            PracticeUtil.msg("§8 r" + r + "   " + c + fmt(t) + "§8/" + fmt(top) + " §8| " + joinPresses(presses, false));
        }
        if (rounds.size() > 1) {
            double sum = 0;
            for (double d : rounds) sum += d;
            PracticeUtil.msg("§7 r" + fromRound + "-r" + FINAL_ROUND + " clicking: §f" + fmt(sum) + "s");
        }
    }

    private static void pressFrom(int n) {
        Placement p = placed;
        if (p == null) return;
        startFrom(n, true);
        click(p.at(fromButton(n)));
        p.set(fromButton(n), BUTTON.setValue(ButtonBlock.POWERED, true));
        after(2, () -> { if (placed != null) placed.set(fromButton(n), BUTTON); });
    }

    private static void pressExtra() {
        Placement p = placed;
        if (p == null) return;
        startInf();
        click(p.at(EXTRA));
        p.set(EXTRA, BUTTON.setValue(ButtonBlock.POWERED, true));
        after(2, () -> { if (placed != null) placed.set(EXTRA, BUTTON); });
    }

    private static void pressStart() {
        Placement p = placed;
        if (p == null) return;
        if (Inf.on || fromRound > 0 || device.done()) reset();
        if (!running) beginRun();
        SimonSaysDevice.Start r = device.start(YOU, System.nanoTime() / 1e6);
        if (r == SimonSaysDevice.Start.STARTED || r == SimonSaysDevice.Start.RESET) accepted.add(tick - runStart);
        markMode(0);
        click(p.at(START));
        PracticeUtil.correct();
        p.set(START, BUTTON.setValue(ButtonBlock.POWERED, true));
        after(2, () -> { if (placed != null) placed.set(START, BUTTON); });
    }

    /** Trigger Bot: practice device only, never the real one. */
    private static void trigger() {
        Placement p = placed;
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        BlockPos pos = target();
        if (pos == null) return;
        int cell = -1;
        for (int i = 0; i < 16; i++) if (p.at(buttonAt(i)).equals(pos)) { cell = i; break; }
        if (cell < 0) return;
        if (Inf.on) {
            if (!buttonUp[cell] || downUntil[cell] > tick || Inf.queue.isEmpty() || cell != Inf.queue.get(0)) return;
        } else {
            Integer due = device.due();
            if (!device.pressable(cell) || due == null || cell != due) return;
        }
        press(cell);
        if (mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND, false);
    }

    private static void press(int cell) {
        Placement p = placed;
        if (p == null) return;
        if (Inf.on) {
            if (!buttonUp[cell] || downUntil[cell] > tick) return;
            click(p.at(buttonAt(cell)));
            setButton(cell, true, true);
            downUntil[cell] = tick + 3;
            after(3, () -> { if (buttonUp[cell]) setButton(cell, true, false); });
            infPress(cell);
            return;
        }
        SimonSaysDevice.Press r = device.press(cell, YOU);
        if (r == SimonSaysDevice.Press.NONE) return;
        click(p.at(buttonAt(cell)));
        switch (r) {
            case RIGHT, ROUND -> {
                PracticeUtil.correct();
                long now = System.currentTimeMillis();
                roundClicks.add((now - lastClickMs) / 1000.0);
                lastClickMs = now;
                if (r == SimonSaysDevice.Press.RIGHT) return;
                int n = device.round();
                rounds.add((tick + 6 - roundUp) / 20.0);
                splits.add(new ArrayList<>(roundClicks));
                shownFaster += 8L * n - showTick(n);
            }
            case WRONG -> {
                PracticeUtil.wrong();
                fails++;
                int f = fails;
                restart(false);
                fails = f;
            }
            default -> {}
        }
    }

    private static void onDone() {
        if (fromRound > 0) {
            fromDone();
            fromRun();
            return;
        }
        running = false;
        if (firstLight == 0L) return;
        double total = (tick + shownFaster - firstLight) / 20.0;
        float speedSetting = cfg().admSsShowSpeed;
        String speed = cfg().admSsInstantShow ? " §8(instant, as at 1x)"
                : speedSetting != 1f ? " §8(" + trimSpeed(speedSetting) + "x, as at 1x)" : "";
        report(total, rounds, splits, (fails > 0 ? " §c" + fails + " wrong" : "") + speed);
    }

    private static String trimSpeed(float s) {
        String t = fmt(s);
        while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static Double topFor(int n) {
        int i = n - 2;
        return i >= 0 && i < TOP_ROUNDS.length ? TOP_ROUNDS[i] : null;
    }

    private static void report(double total, List<Double> rounds, List<List<Double>> splits, String suffix) {
        String colour = total <= 11.6 ? "§a" : total <= 11.95 ? "§2" : total <= 12.6 ? "§e" : total <= 13.5 ? "§c" : "§4";
        PracticeUtil.msg("§7SS took: " + colour + fmt(total) + "s" + suffix);
        for (int i = 0; i < rounds.size(); i++) {
            List<Double> presses = i < splits.size() ? splits.get(i) : List.of();
            boolean skip = i == 0 && presses.size() >= 2;
            String name = skip ? "skip" : "r" + presses.size();
            Double top = topFor(presses.size());
            double t = rounds.get(i);
            String c = top == null ? "§f" : t <= top ? "§a" : t <= top + 0.3 ? "§e" : "§c";
            String vs = top == null ? "" : "§8/" + fmt(top);
            PracticeUtil.msg("§8 " + String.format(Locale.ROOT, "%-4s", name) + " " + c + fmt(t) + vs + " §8| " + joinPresses(presses, skip));
        }
    }

    private static String joinPresses(List<Double> presses, boolean skip) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < presses.size(); j++) {
            if (j > 0) sb.append(" §8› ");
            double t = presses.get(j);
            sb.append(pressColour(skip, j, t)).append(fmt(t));
        }
        return sb.toString();
    }

    private static String pressColour(boolean skip, int index, double t) {
        if (skip) return "§7";
        if (index == 0) return t <= 0.10 ? "§a" : t <= 0.20 ? "§e" : "§c";
        return t <= 0.25 ? "§2" : t <= 0.35 ? "§e" : "§c";
    }

    private static String fmt(double v) { return PracticeUtil.fmt(v); }

    private static void click(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Placement p = placed;
        Vec3 c = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 w = p == null ? c : view(p, 1f, null).toWorld(c);
        if (mc.level != null) mc.level.playLocalSound(w.x, w.y, w.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f, false);
    }

    // ------------------------------------------------------------------ where it's drawn

    private static final class View {
        final Placement p;
        final Vec3 eye;
        final int k;
        final double s;

        View(Placement p, Vec3 eye, int k, double s) { this.p = p; this.eye = eye; this.k = k; this.s = s; }

        private static double[] turn(double x, double z, int steps) {
            double a = x, b = z;
            for (int i = 0; i < steps; i++) { double t = a; a = -b; b = t; }
            return new double[]{a, b};
        }

        Vec3 toWorld(Vec3 v) {
            double[] t = turn(v.x - p.eye.x, v.z - p.eye.z, k);
            return new Vec3(eye.x + t[0] * s, eye.y + (v.y - p.eye.y) * s, eye.z + t[1] * s);
        }

        Vec3 toDevice(Vec3 v) {
            double[] t = turn(v.x - eye.x, v.z - eye.z, (4 - k) % 4);
            return new Vec3(p.eye.x + t[0] / s, p.eye.y + (v.y - eye.y) / s, p.eye.z + t[1] / s);
        }

        Vec3 dirToDevice(Vec3 v) {
            double[] t = turn(v.x, v.z, (4 - k) % 4);
            return new Vec3(t[0], v.y, t[1]);
        }

        AABB box(AABB b) {
            return new AABB(toWorld(new Vec3(b.minX, b.minY, b.minZ)), toWorld(new Vec3(b.maxX, b.maxY, b.maxZ)));
        }

        BlockPos lightPos() { return BlockPos.containing(eye); }
    }

    private static int turned = 0;

    private static View view(Placement p, float partialTicks, Vec3 eye) {
        Player player = Minecraft.getInstance().player;
        double size = cfg().admSsSize;
        if (!cfg().admSsFollow || player == null) return new View(p, p.eye, 0, size);
        Direction facing = Direction.from2DDataValue(p.forward.get2DDataValue() + turned);
        if (Math.abs(Mth.wrapDegrees(player.getYRot() - facing.toYRot())) > TURN_AROUND) turned = (turned + 2) % 4;
        return new View(p, eye != null ? eye : player.getEyePosition(partialTicks), turned, size);
    }

    // ------------------------------------------------------------------ drawing

    private static final class DeviceBlock extends MovingBlockRenderState {
        final BlockPos lightAt;

        DeviceBlock(BlockPos lightAt) { this.lightAt = lightAt; }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return lightEngine.getLayerListener(layer).getLightValue(lightAt);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return lightEngine.getRawBrightness(lightAt, darkening);
        }
    }

    private interface Draw { void run(); }

    private static void draw(Placement p, View v, LevelRenderContext context, Vec3 cam) {
        PoseStack pose = context.poseStack();
        SubmitNodeCollector out = context.submitNodeCollector();
        ClientLevel level = p.level;
        BlockPos lightPos = cfg().admSsFollow ? v.lightPos() : p.lightPos;
        int light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, lightPos);
        for (Map.Entry<BlockPos, BlockState> e : p.blocks.entrySet()) {
            BlockPos pos = e.getKey();
            if (p.signs.containsKey(pos)) continue;
            DeviceBlock s = new DeviceBlock(lightPos);
            s.randomSeedPos = pos;
            s.blockPos = pos;
            s.blockState = e.getValue();
            s.biome = level.getBiome(pos);
            s.cardinalLighting = level.cardinalLighting();
            s.lightEngine = level.getLightEngine();
            at(pose, v, pos, cam, () -> out.submitMovingBlock(pose, s));
        }
        BlockEntityRenderDispatcher signs = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        var camera = context.levelState().cameraRenderState;
        for (Map.Entry<BlockPos, SignBlockEntity> e : p.signs.entrySet()) {
            BlockEntityRenderState s = signs.tryExtractRenderState(e.getValue(), 0f, null);
            if (s == null) continue;
            s.lightCoords = light;
            at(pose, v, e.getKey(), cam, () -> signs.submit(s, pose, out, camera));
        }
    }

    private static void at(PoseStack pose, View v, BlockPos pos, Vec3 cam, Draw draw) {
        Vec3 w = v.toWorld(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
        pose.pushPose();
        pose.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
        if (v.k != 0) pose.mulPose(new Matrix4f().rotateY((float) Math.toRadians(-90.0 * v.k)));
        if (v.s != 1.0) pose.scale((float) v.s, (float) v.s, (float) v.s);
        draw.run();
        pose.popPose();
    }

    // ------------------------------------------------------------------ input (from the mixin)

    private static BlockHitResult hit;

    private static BlockPos target() {
        Placement p = placed;
        if (p == null || hit == null) return null;
        BlockPos pos = hit.getBlockPos();
        return p.blocks.containsKey(pos) ? pos : null;
    }

    public static void afterPick(float partialTicks) {
        hit = null;
        disableCheck();
        Placement p = placed;
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (p.level != mc.level) return;
        Player player = mc.player;
        if (player == null || mc.getCameraEntity() != player) return;
        PracticeUtil.safely("ss practice pick", () -> {
            View v = view(p, partialTicks, null);
            Vec3 worldEye = player.getEyePosition(partialTicks);
            Vec3 eye = v.toDevice(worldEye);
            Vec3 end = eye.add(v.dirToDevice(player.getViewVector(partialTicks)).scale(player.blockInteractionRange()));
            BlockHitResult best = null;
            double bestD = Double.MAX_VALUE;
            for (Map.Entry<BlockPos, BlockState> e : p.blocks.entrySet()) {
                BlockHitResult r = shapeOf(e.getKey(), e.getValue()).clip(eye, end, e.getKey());
                if (r == null) continue;
                double d = r.getLocation().distanceToSqr(eye);
                if (d < bestD) { bestD = d; best = r; }
            }
            if (best == null) return;
            Vec3 at = v.toWorld(best.getLocation());
            HitResult game = mc.hitResult;
            if (game instanceof EntityHitResult eh && !(eh.getEntity() instanceof Player)
                    && game.getLocation().distanceToSqr(worldEye) < at.distanceToSqr(worldEye)) return;
            hit = best;
            mc.hitResult = BlockHitResult.miss(at, best.getDirection(), best.getBlockPos());
            mc.crosshairPickEntity = null;
        });
    }

    /** Right click. True: it was on the device and the game must not use it. */
    public static boolean onUse() {
        BlockPos pos = target();
        Placement p = placed;
        if (pos == null || p == null) return false;
        BlockState st = p.blocks.get(pos);
        boolean button = st != null && st.getBlock() instanceof ButtonBlock;
        PracticeUtil.safely("ss practice use", () -> {
            Integer from = null;
            for (int n = 3; n <= 4; n++) if (pos.equals(p.at(fromButton(n)))) from = n;
            Integer rngPick = null;
            for (int lvl = -1; lvl <= 2; lvl++) if (pos.equals(p.at(rngButton(lvl)))) rngPick = lvl;
            if (pos.equals(p.at(START))) pressStart();
            else if (rngPick != null) pressRng(rngPick);
            else if (pos.equals(p.at(EXTRA))) pressExtra();
            else if (from != null) pressFrom(from);
            else for (int i = 0; i < 16; i++) if (p.at(buttonAt(i)).equals(pos)) { press(i); break; }
        });
        Minecraft mc = Minecraft.getInstance();
        if (button && mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND, false);
        ((AdmPracticeMinecraftAccessor) mc).teslamaps$admPracticeRightClickDelay(4);
        return true;
    }

    /** Left click on the device: a click on the grid restarts; nothing is sent. */
    public static boolean onAttack() {
        BlockPos pos = target();
        Placement p = placed;
        if (pos == null || p == null) return false;
        boolean grid = false;
        for (int i = 0; i < 16; i++) if (p.at(lampAt(i)).equals(pos) || p.at(buttonAt(i)).equals(pos)) { grid = true; break; }
        if (grid) PracticeUtil.safely("ss practice restart", () -> restart(true));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND, false);
        return true;
    }

    public static boolean blocksContinueAttack() {
        return target() != null;
    }

    // ------------------------------------------------------------------ the real device

    /** A right click on F7's real Simon Says in P3; [blocked]: Block Wrong Clicks stopped it. */
    public static void realClick(BlockPos pos, boolean blocked) {
        if (!Adm.on() || !cfg().admSsPractice || placed != null || !PracticeUtil.inP3()) return;
        boolean isStart = pos.equals(START);
        if (!isStart && (pos.getX() != 110 || pos.getY() < 120 || pos.getY() > 123 || pos.getZ() < 92 || pos.getZ() > 95)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ButtonBlock) || state.getValue(BlockStateProperties.POWERED)) return;
        if (blocked) { PracticeUtil.wrong(); return; }
        if (isStart) { PracticeUtil.correct(); return; }
        List<BlockPos> order = AdmSimonSays.answer();
        int i = AdmSimonSays.next();
        boolean right = i < order.size() && order.get(i).equals(pos.east());
        if (right) PracticeUtil.correct(); else PracticeUtil.wrong();
        Real.press(right, right && i == order.size() - 1, System.currentTimeMillis());
    }

    private static final class Real {
        static long firstLight = 0L, roundUp = 0L, lastPress = 0L;
        static boolean buttonsUp = false;
        static int fails = 0;
        static final List<Double> rounds = new ArrayList<>();
        static final List<List<Double>> splits = new ArrayList<>();
        static final List<Double> presses = new ArrayList<>();

        static void reset() {
            firstLight = 0L; roundUp = 0L; buttonsUp = false; lastPress = 0L; fails = 0;
            rounds.clear(); splits.clear(); presses.clear();
        }

        static void block(BlockPos pos, BlockState old, BlockState updated) {
            if (pos.getY() < 120 || pos.getY() > 123 || pos.getZ() < 92 || pos.getZ() > 95) return;
            long now = System.currentTimeMillis();
            if (pos.getX() == 111) {
                if (updated.getBlock() == Blocks.SEA_LANTERN && old.getBlock() != Blocks.SEA_LANTERN
                        && (firstLight == 0L || now - firstLight > 30_000)) { reset(); firstLight = now; }
            } else if (pos.getX() == 110) {
                if (updated.getBlock() instanceof ButtonBlock && !(old.getBlock() instanceof ButtonBlock) && !buttonsUp) {
                    buttonsUp = true; roundUp = now; lastPress = now; presses.clear();
                } else if (updated.isAir() && old.getBlock() instanceof ButtonBlock) {
                    buttonsUp = false;
                }
            }
        }

        static void press(boolean right, boolean last, long now) {
            if (firstLight == 0L || roundUp == 0L) return;
            if (!right) { fails++; rounds.clear(); splits.clear(); presses.clear(); return; }
            presses.add((now - lastPress) / 1000.0);
            lastPress = now;
            if (!last) return;
            rounds.add((now - roundUp) / 1000.0 + 0.3);
            splits.add(new ArrayList<>(presses));
            if (presses.size() == FINAL_ROUND) {
                report((now + 300 - firstLight) / 1000.0, rounds, splits, (fails > 0 ? " §c" + fails + " wrong" : "") + " §8(real)");
                reset();
            }
            presses.clear();
        }
    }

    /** Client thread, before a block change is applied. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated) {
        if (placed != null || !Adm.on() || !cfg().admSsPractice) return;
        if (pos.getX() != 110 && pos.getX() != 111) return;
        if (!PracticeUtil.inP3()) return;
        PracticeUtil.safely("ss real times", () -> Real.block(pos, old, updated));
    }

    // ------------------------------------------------------------------ tick / chat / world

    private static Object lastLevel;

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            placed = null; hit = null; gen++; jobs.clear(); device.clear(); running = false;
            Real.reset();
        }
        boolean down = mc.gui.screen() == null && KeyNames.isDown(cfg().admSsPracticeKey);
        if (down && !keyDown && mc.player != null) {
            if (!InfNumbersSim.openFromLeap()) summonOrRemove();
        }
        keyDown = down;
        if (cfg().admSsTriggerBot) PracticeUtil.safely("ss trigger bot", SsPractice::trigger);
        tick++;
        if (!jobs.isEmpty()) {
            List<Job> due = new ArrayList<>();
            for (Job j : jobs) if (j.at <= tick) due.add(j);
            jobs.removeAll(due);
            for (Job j : due) if (j.gen == gen) PracticeUtil.safely("ss practice", j.run);
        }
        PracticeUtil.safely("ss practice device", device::tick);
    }

    private static void onChat(String raw) {
        if (placed == null) return;
        String msg = com.teslamaps.utils.StringUtil.strip(raw);
        if (!msg.equals(STORM_DEAD) && !msg.equals(GOLDOR_START)) return;
        remove();
        PracticeUtil.msg("§7SS Practice: removed (terminals starting).");
    }

    /** The feature turned off or ADM off: nothing stays drawn. */
    static void disableCheck() {
        if (placed != null && (!Adm.on() || !cfg().admSsPractice)) remove();
    }
}
