// Ported from ADM's p3sim/clear/puzzles/TicTacToePuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Tic Tac Toe in the world, as the recorded Hypixel rooms play it. The board is the wall of nine cells (rows y = 72, 71, 70, columns
 * z = 15, 16, 17 in the room's frame, a stone button in each cell at x = 8 on the iron wall at x = 7). The ROOM plays first, 24 ticks
 * after a player steps in; a press hangs an O and pings, the room answers 60 ticks later. Marks are item frames holding maps.
 * A tie solves (chest, green +2, Completed +13, marks go +29, wall barrier +30, air +56, chest opens +62); a room line fails.
 */
public class TicTacToePuzzle extends WorldPuzzle {
    private TttLogic logic = null;
    private final Map<Integer, ItemFrame> frames = new HashMap<>();
    private UUID presser = null;

    public TicTacToePuzzle(PuzzleContext context) { super(PuzzleKinds.TTT, context); }

    @Override public int wakeDelayTicks() { return TttLogic.OPENING_TICKS; }

    @Override public boolean rewardSignalAtGreen() { return false; }

    @Override public String solvedPhrase() { return "tied Tic Tac Toe!"; }

    /** The line and the check mark come at the last mark: green 2 ticks later, Completed 13 ticks later. */
    @Override public SolveTiming solveTiming() { return new SolveTiming(0, GREEN_TICKS, COMPLETE_TICKS); }

    @Override public List<WorldPos> rewardSpots() {
        WorldPos w = world(CHEST_X, CHEST_Y, CHEST_Z);
        return w == null ? List.of() : List.of(w);
    }

    @Override protected void build() {
        logic = new TttLogic(run().subRandom("ttt:" + context().room.index));
    }

    /** A stone button on every cell of the board. */
    private void placeButtons() {
        BlockState button = roomState(BUTTON_STATE);
        if (button == null) return;
        for (int c = 0; c <= 8; c++) setAt(BOARD_X, rowY(c), colZ(c), button);
    }

    private int rowY(int cell) { return TOP_Y - cell / 3; }

    private int colZ(int cell) { return LEFT_Z + cell % 3; }

    /** A player stepped in 24 ticks ago: the room makes its first mark. */
    @Override protected void wake() {
        if (status() != PuzzleStatus.UNSOLVED) return;
        placeButtons();
        openGame();
    }

    private void openGame() {
        TttLogic l = logic;
        if (l == null) return;
        int cell = l.roomOpens();
        if (cell >= 0) mark(cell, TttLogic.Mark.X);
    }

    // ---- marks

    /** A mark on cell: the button goes and an item frame with the map of the mark takes its place. */
    private void mark(int cell, TttLogic.Mark m) {
        setAt(BOARD_X, rowY(cell), colZ(cell), Blocks.AIR.defaultBlockState());
        ItemFrame old = frames.remove(cell);
        if (old != null) forget(old);
        WorldPos w = world(BOARD_X, rowY(cell), colZ(cell));
        if (w == null) return;
        Placement pl = placement();
        Direction facing = PuzzleBlocks.rotation(pl != null ? pl.quarterTurns() : 0).rotate(Direction.EAST);
        ItemFrame frame = new ItemFrame(EntityTypes.ITEM_FRAME, Sim.level(), new BlockPos(w.x, w.y, w.z), facing);
        frame.setPermanentlyInvulnerable(true);
        boolean isX = m == TttLogic.Mark.X;
        ensureMaps();
        frame.setItem(mapStack(isX ? TttArt.X_MAP_ID : TttArt.O_MAP_ID), false);
        track(frame);
        Sim.spawn(frame);
        frames.put(cell, frame);
    }

    /** Takes every mark off the board and puts the buttons back. */
    private void clearBoard() {
        for (ItemFrame f : frames.values()) forget(f);
        frames.clear();
        placeButtons();
    }

    private ItemStack mapStack(int id) {
        ItemStack s = new ItemStack(Items.FILLED_MAP);
        s.set(DataComponents.MAP_ID, new MapId(id));
        return s;
    }

    /** The two maps exist on the server so every player who sees a frame gets the picture, and are sent once more to the players here now. */
    private void ensureMaps() {
        ServerLevel level = Sim.level();
        int[] ids = {TttArt.X_MAP_ID, TttArt.O_MAP_ID};
        boolean[] xs = {true, false};
        for (int k = 0; k < 2; k++) {
            int id = ids[k];
            boolean isX = xs[k];
            MapId mid = new MapId(id);
            byte[] pixels = TttArt.pixels(isX);
            if (level.getMapData(mid) == null) {
                MapItemSavedData data = MapItemSavedData.createFresh(0.0, 0.0, (byte) 0, false, false, Level.OVERWORLD);
                System.arraycopy(pixels, 0, data.colors, 0, pixels.length);
                level.setMapData(mid, data);
            }
            if (sentMaps.contains(id)) continue;
            sentMaps.add(id);
            ClientboundMapItemDataPacket packet = new ClientboundMapItemDataPacket(mid, (byte) 0, false, Optional.empty(),
                    Optional.of(new MapItemSavedData.MapPatch(0, 0, TttArt.SIZE, TttArt.SIZE, pixels)));
            for (ServerPlayer p : Sim.players()) p.connection.send(packet);
        }
    }

    private final Set<Integer> sentMaps = new HashSet<>();

    // ---- the game

    @Override public boolean onInteract(UUID player, int x, int y, int z) {
        TttLogic l = logic;
        if (l == null) return false;
        Pair<Integer, Integer> loc = local(x, z);
        if (loc == null) return false;
        if (loc.first < BOARD_X - 1 || loc.first > BOARD_X || y < TOP_Y - 2 || y > TOP_Y || loc.second < LEFT_Z || loc.second > LEFT_Z + 2) return false;
        if (!awake || status() != PuzzleStatus.UNSOLVED) return false;
        int cell = (TOP_Y - y) * 3 + (loc.second - LEFT_Z);
        // Out of turn, or on a marked cell: the press only clicks the button.
        if (!l.press(cell)) return false;
        presser = player;
        mark(cell, TttLogic.Mark.O);
        ServerPlayer p = playerOf(player);
        if (p != null) pling(p);
        if (l.result != TttLogic.Result.PLAYING) { finish(l, player); return true; }
        after(TttLogic.THINK_TICKS, TAG_GAME, this::reply);
        return true;
    }

    /** The pling: loud, high, at the player's feet. */
    private void pling(ServerPlayer p) {
        Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8.0f, 4.047619f, new Vec3(p.getX(), p.getY(), p.getZ()), SoundSource.BLOCKS);
    }

    /** The room's answer, 60 ticks after the party's press. */
    private void reply() {
        TttLogic l = logic;
        if (l == null) return;
        if (status() != PuzzleStatus.UNSOLVED) return;
        int cell = l.roomReplies();
        if (cell >= 0) mark(cell, TttLogic.Mark.X);
        if (l.result != TttLogic.Result.PLAYING) finish(l, presser);
    }

    /** The game is over on the tick of the last mark: a tie solves the puzzle, a line for the room fails it. */
    private void finish(TttLogic l, UUID by) {
        switch (l.result) {
            case TIE, WON -> solveGame(by);
            case LOST -> fail(by, "lost Tic Tac Toe!");
            case PLAYING -> {}
        }
    }

    // ---- the solve

    private List<WorldPos> wallCells() {
        List<WorldPos> out = new ArrayList<>();
        for (int z = LEFT_Z; z <= LEFT_Z + 2; z++) {
            for (int y = TOP_Y - 2; y <= TOP_Y; y++) {
                WorldPos w = world(WALL_X, y, z);
                if (w != null) out.add(w);
            }
            WorldPos w = world(WALL_X, TOP_Y - 3, z);
            if (w != null) out.add(w);
        }
        return out;
    }

    private void placeChest() {
        List<WorldPos> spots = rewardSpots();
        if (spots.isEmpty()) return;
        BlockState s = roomState(CHEST_STATE);
        if (s != null) setWorld(spots.get(0), s);
    }

    /** T: the chest appears and the marks, the wall and the chest follow their schedule. */
    private void solveGame(UUID by) {
        placeChest();
        solve(by);
        after(FRAMES_TICKS, () -> { for (ItemFrame f : frames.values()) forget(f); frames.clear(); });
        List<WorldPos> wall = wallCells();
        after(WALL_BARRIER_TICKS, () -> { for (WorldPos w : wall) setWorld(w, Blocks.BARRIER.defaultBlockState()); });
        WorldPos g = world(WALL_X, TOP_Y - 1, LEFT_Z + 1);
        if (g != null) {
            Vec3 at = new Vec3(g.x + 0.5, g.y + 0.5, g.z + 0.5);
            int t = WALL_BARRIER_TICKS;
            while (t < WALL_OPEN_TICKS) {
                after(t, () -> Sim.sound(SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, 2.0f, 0.4920635f, at));
                t += 5;
            }
        }
        after(WALL_OPEN_TICKS, () -> { for (WorldPos w : wall) setWorld(w, Blocks.AIR.defaultBlockState()); });
        after(CHEST_OPEN_TICKS, this::openChest);
    }

    /** The chest opens by itself: Odin's signal, the lid sound, the harp jingle. */
    private void openChest() {
        List<WorldPos> spots = rewardSpots();
        if (spots.isEmpty()) return;
        WorldPos c = spots.get(0);
        lidSignal(c);
        Vec3 at = new Vec3(c.x + 0.5, c.y + 0.5, c.z + 0.5);
        Sim.sound(SoundEvents.CHEST_OPEN, 0.5f, 0.92f, at, SoundSource.BLOCKS);
        for (int i = 0; i < ChestJingle.PITCHES.length; i++) {
            float pitch = ChestJingle.PITCHES[i];
            after(ChestJingle.FIRST_NOTE + ChestJingle.NOTE_GAP * i, () -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1.0f, pitch, at));
        }
    }

    /** The room as it stands once solved: the chest there, the wall open, no marks. */
    @Override public void applySolved() {
        placeChest();
        for (WorldPos w : wallCells()) setWorld(w, Blocks.AIR.defaultBlockState());
    }

    /** The draft is used: the marks go one tick later and the room opens a new game. */
    @Override public void onReset() {
        cancelTimers(TAG_GAME);
        if (logic != null) logic.reset();
        after(RESET_TICKS, () -> {
            clearBoard();
            after(TttLogic.OPENING_TICKS, TAG_GAME, this::openGame);
        });
    }

    public static final int BOARD_X = 8;
    public static final int WALL_X = 7;
    public static final int TOP_Y = 72;
    public static final int LEFT_Z = 15;

    /** The reward chest appears here at the solve, in the room's frame, facing east. */
    public static final int CHEST_X = 2;
    public static final int CHEST_Y = 70;
    public static final int CHEST_Z = 16;
    private static final String CHEST_STATE = "minecraft:chest[facing=east,type=single,waterlogged=false]";
    private static final String BUTTON_STATE = "minecraft:stone_button[face=wall,facing=east,powered=false]";

    /** After the last mark: green, Completed, the marks go, the wall turns to barrier, then to air, the chest opens. */
    public static final int GREEN_TICKS = 2;
    public static final int COMPLETE_TICKS = 13;
    public static final int FRAMES_TICKS = 29;
    public static final int WALL_BARRIER_TICKS = 30;
    public static final int WALL_OPEN_TICKS = 56;
    public static final int CHEST_OPEN_TICKS = 62;

    /** The marks go this long after the reset item is used. */
    public static final int RESET_TICKS = 1;

    private static final String TAG_GAME = "game";
}
