// Ported from ADM's p3sim/clear/puzzles/PuzzlesModule.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.AdsModules;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.ModuleEvent;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PuzzleFailed;
import com.teslamaps.adm.sim.run.PuzzleRecord;
import com.teslamaps.adm.sim.run.PuzzleSolved;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.RoomEntered;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.SecretFound;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.run.api.PuzzleHost;
import com.teslamaps.adm.sim.run.api.PuzzleType;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The shared puzzle frame (tab rows, the reward chest signal, Hypixel's solved and failed lines, the reset item, the routing of clicks
 * to the puzzle of the room) and the easy and medium puzzles. It collects every module's puzzleTypes() at run start (the hard puzzles
 * come from the hardpuzzles module through the same list).
 */
public final class PuzzlesModule implements AdsModule, PuzzleHost {
    public static final PuzzlesModule INSTANCE = new PuzzlesModule();

    private PuzzlesModule() {}

    @Override public String id() { return "puzzles"; }

    @Override public String owner() { return "puzzles"; }

    /** The reset item's name (what Odin's draft prompt fetches). */
    public static final String DRAFT_NAME = "Architect's First Draft";

    /** Players within this many blocks of a puzzle room's middle make it build its world state. */
    public static final double ACTIVATE_RANGE = 48.0;

    private static final class Later {
        final int at;
        final Runnable what;

        Later(int at, Runnable what) {
            this.at = at;
            this.what = what;
        }
    }

    private static final class RunData {
        final Run run;
        final Map<String, PuzzleType> types = new HashMap<>();
        final List<Puzzle> puzzles = new ArrayList<>();
        final Map<Integer, Puzzle> byRoom = new HashMap<>();
        final List<Later> later = new ArrayList<>();
        /** Puzzles that are being solved (the solve is delayed a few ticks). */
        final Set<Puzzle> solving = new HashSet<>();

        RunData(Run run) { this.run = run; }
    }

    private static RunData data = null;

    private static RunData dataOf(Run run) {
        RunData d = data;
        return d != null && d.run == run ? d : null;
    }

    // ---- the types

    @Override public List<PuzzleType> puzzleTypes() {
        return List.of(
                new PuzzleType(PuzzleKinds.WEIRDOS, "Three Weirdos", Set.of("Three_Weirdos"), WeirdosPuzzle::new),
                new PuzzleType(PuzzleKinds.QUIZ, "Quiz", Set.of("Quiz"), QuizPuzzle::new),
                new PuzzleType(PuzzleKinds.BEAMS, "Creeper Beams", Set.of("Creeper_Beams"), BeamsPuzzle::new),
                new PuzzleType(PuzzleKinds.BOULDER, "Boulder", Set.of("Boulder"), BoulderPuzzle::new),
                new PuzzleType(PuzzleKinds.ICE_FILL, "Ice Fill", Set.of("Ice_Fill"), IceFillPuzzle::new),
                new PuzzleType(PuzzleKinds.TTT, "Tic Tac Toe", Set.of("Tic_Tac_Toe"), TicTacToePuzzle::new),
                new PuzzleType(PuzzleKinds.BLAZE, "Higher Or Lower", Set.of("Blaze_Lower", "Blaze_Upper"), BlazePuzzle::new));
    }

    // ---- run lifecycle

    @Override public void onRunStart(Run run) {
        RunData d = new RunData(run);
        data = d;
        run.state.puzzles.clear();
        for (AdsModule m : AdsModules.all()) {
            List<PuzzleType> types;
            try { types = m.puzzleTypes(); } catch (Throwable t) { Ec.LOG.error("[ads] " + m.id() + ".puzzleTypes failed", t); types = List.of(); }
            for (PuzzleType t : types) d.types.putIfAbsent(t.kind, t);
        }
        for (PlacedRoom room : run.plan.rooms) {
            if (room.type != RoomType.PUZZLE) continue;
            String packKind = room.design != null && room.design.puzzle != null ? room.design.puzzle.kind : null;
            String kind = PuzzleKinds.fromPackKind(packKind);
            if (kind == null) kind = PuzzleKinds.fromFamily(room.family);
            if (kind == null) {
                for (PuzzleType t : d.types.values()) if (t.families.contains(room.family)) { kind = t.kind; break; }
            }
            if (kind == null) { Ec.LOG.warn("[ads] the puzzle room " + room.name + " (" + room.family + ") has no known puzzle kind"); continue; }
            PuzzleType type = d.types.get(kind);
            String tabName = type != null ? type.tabName : PuzzleKinds.tabNames.get(kind);
            if (tabName == null) tabName = room.name;
            PuzzleRecord record = new PuzzleRecord(room.index, kind, tabName);
            run.state.puzzles.add(record);
            if (type == null) { Ec.LOG.warn("[ads] no module provides the " + kind + " puzzle; the room " + room.name + " stays unsolved (/ads dev puzzlesolve)"); continue; }
            Puzzle puzzle;
            try { puzzle = type.create.apply(new PuzzleContext(run, room, record, this)); } catch (Throwable t) { Ec.LOG.error("[ads] creating the " + kind + " puzzle failed", t); continue; }
            d.puzzles.add(puzzle);
            d.byRoom.put(room.index, puzzle);
            try { puzzle.onRunStart(); } catch (Throwable t) { Ec.LOG.error("[ads] " + kind + " onRunStart failed", t); }
        }
    }

    @Override public void onRunEnd(Run run, EndReason reason) {
        RunData d = dataOf(run);
        if (d == null) return;
        for (Puzzle p : d.puzzles) if (p instanceof WorldPuzzle wp) wp.deactivate();
        data = null;
    }

    @Override public void onTick(Run run) {
        RunData d = dataOf(run);
        if (d == null) return;
        List<Later> due = new ArrayList<>();
        for (Later l : d.later) if (l.at <= run.serverTick) due.add(l);
        if (!due.isEmpty()) {
            d.later.removeAll(new HashSet<>(due));
            for (Later l : due) guard("delayed step", l.what);
        }
        if (!run.stage.inDungeon()) return;
        List<ServerPlayer> humans = Sim.players();
        if (humans.isEmpty()) return;
        Set<Integer> occupied = new HashSet<>();
        for (ServerPlayer p : humans) {
            RoomRecord r = run.state.roomAt(p.getX(), p.getZ());
            if (r != null) occupied.add(r.index());
        }
        for (Puzzle puzzle : d.puzzles) {
            PlacedRoom room = puzzle.context().room;
            if (puzzle instanceof WorldPuzzle wp) {
                if (!wp.activated) {
                    boolean anyNear = false;
                    for (ServerPlayer p : humans) if (near(p, room)) { anyNear = true; break; }
                    if (anyNear) guard(wp.kind() + " activate", wp::activate);
                }
                if (wp.activated) {
                    if (occupied.contains(room.index)) wp.noteEntered(run.serverTick);
                    // Wakes the room a few ticks after the first step into it, then runs its timed steps (even with nobody inside).
                    guard(wp.kind() + " pump", () -> wp.pump(run.serverTick));
                }
            }
            if (occupied.contains(room.index) && (!(puzzle instanceof WorldPuzzle w) || (w.activated && w.awake))) guard(puzzle.kind() + " onTick", puzzle::onTick);
        }
    }

    private static double centreX(PlacedRoom room) {
        double sum = 0;
        for (Tile t : room.tiles) sum += DungeonFrame.tileCentre(t.x);
        return room.tiles.isEmpty() ? Double.NaN : sum / room.tiles.size();
    }

    private static double centreZ(PlacedRoom room) {
        double sum = 0;
        for (Tile t : room.tiles) sum += DungeonFrame.tileCentre(t.z);
        return room.tiles.isEmpty() ? Double.NaN : sum / room.tiles.size();
    }

    private static boolean near(ServerPlayer p, PlacedRoom room) {
        double cx = centreX(room);
        double cz = centreZ(room);
        double dx = p.getX() - cx, dz = p.getZ() - cz;
        return dx * dx + dz * dz <= ACTIVATE_RANGE * ACTIVATE_RANGE;
    }

    @Override public void onEvent(Run run, RunEvent event) {
        RunData d = dataOf(run);
        if (d == null) return;
        if (event instanceof RoomEntered e) {
            Puzzle puzzle = d.byRoom.get(e.room);
            PuzzleRecord record = null;
            for (PuzzleRecord r : run.state.puzzles) if (r.roomIndex == e.room) { record = r; break; }
            if (record == null) return;
            PlayerRecord pr = run.state.players.get(e.player);
            boolean human = pr == null || !pr.isBot;
            if (human && !record.revealed) record.revealed = true;
            if (human && puzzle != null) {
                if (puzzle instanceof WorldPuzzle wp) { wp.activate(); wp.noteEntered(run.serverTick); }
                guard(puzzle.kind() + " onRoomEntered", () -> puzzle.onRoomEntered(e.player));
            }
        } else if (event instanceof SecretFound e) {
            if (d.byRoom.get(e.room) instanceof WorldPuzzle wp && wp.activated) {
                guard(wp.kind() + " onSecretFound", () -> wp.onSecretFound(e.x, e.y, e.z, e.player));
            }
        } else if (event instanceof ModuleEvent e) {
            // The damage module tells the bus where an arrow landed: x, y, z, by.
            if ("damage".equals(e.source) && "arrowHitBlock".equals(e.name)) {
                if (!(e.data.get("x") instanceof Number nx)) return;
                if (!(e.data.get("y") instanceof Number ny)) return;
                if (!(e.data.get("z") instanceof Number nz)) return;
                int x = nx.intValue(), y = ny.intValue(), z = nz.intValue();
                RoomRecord rr = run.state.roomAt(x + 0.5, z + 0.5);
                if (rr == null) return;
                if (d.byRoom.get(rr.index()) instanceof WorldPuzzle wp && wp.activated) {
                    wp.onArrowHit(x, y, z, e.data.get("by") instanceof UUID u ? u : null);
                }
            }
        }
    }

    // ---- savepoints

    @Override public JsonObject snapshot(Run run) {
        RunData d = dataOf(run);
        if (d == null) return null;
        JsonObject o = new JsonObject();
        JsonArray records = new JsonArray();
        for (PuzzleRecord r : run.state.puzzles) {
            JsonObject j = new JsonObject();
            j.addProperty("room", r.roomIndex);
            j.addProperty("status", r.status.name());
            j.addProperty("revealed", r.revealed);
            j.addProperty("failures", r.failures);
            if (r.solvedBy != null) j.addProperty("solvedBy", r.solvedBy.toString());
            records.add(j);
        }
        o.add("records", records);
        JsonArray puzzles = new JsonArray();
        for (Puzzle p : d.puzzles) {
            JsonObject s = p.snapshot();
            if (s == null) continue;
            JsonObject j = new JsonObject();
            j.addProperty("room", p.context().room.index);
            j.add("data", s);
            puzzles.add(j);
        }
        o.add("puzzles", puzzles);
        return o;
    }

    @Override public void restore(Run run, JsonObject data) {
        RunData d = dataOf(run);
        if (d == null) return;
        d.later.clear();
        d.solving.clear();
        JsonArray records = data.getAsJsonArray("records");
        if (records != null) for (JsonElement e : records) {
            JsonObject j = e.getAsJsonObject();
            int room = j.get("room").getAsInt();
            PuzzleRecord r = null;
            for (PuzzleRecord it : run.state.puzzles) if (it.roomIndex == room) { r = it; break; }
            if (r == null) continue;
            PuzzleStatus st;
            try { st = PuzzleStatus.valueOf(j.get("status").getAsString()); } catch (Throwable t) { st = PuzzleStatus.UNSOLVED; }
            r.status = st;
            JsonElement rev = j.get("revealed");
            r.revealed = rev != null ? rev.getAsBoolean() : false;
            JsonElement fl = j.get("failures");
            r.failures = fl != null ? fl.getAsInt() : 0;
            JsonElement sb = j.get("solvedBy");
            UUID by = null;
            if (sb != null) {
                try { by = UUID.fromString(sb.getAsString()); } catch (Throwable ignored) {}
            }
            r.solvedBy = by;
        }
        // The blocks went back to the start state with the region; each puzzle builds itself again when a player comes near.
        for (Puzzle p : d.puzzles) if (p instanceof WorldPuzzle wp) wp.deactivate();
        JsonArray puzzles = data.getAsJsonArray("puzzles");
        if (puzzles != null) for (JsonElement e : puzzles) {
            JsonObject j = e.getAsJsonObject();
            Puzzle p = d.byRoom.get(j.get("room").getAsInt());
            if (p != null) guard(p.kind() + " restore", () -> p.restore(j.getAsJsonObject("data")));
        }
    }

    // ---- the host (what the puzzles call)

    @Override public Puzzle puzzleOf(Run run, int roomIndex) {
        RunData d = dataOf(run);
        return d == null ? null : d.byRoom.get(roomIndex);
    }

    /** A player's name for the lines: the party record, else the server player, else a stand-in. */
    public static String nameOf(Run run, UUID player) {
        if (player == null) return "Someone";
        PlayerRecord rec = run.state.players.get(player);
        if (rec != null && rec.name != null) return rec.name;
        for (ServerPlayer p : Sim.players()) {
            if (p.getUUID().equals(player)) {
                String n = p.getGameProfile().name();
                return n != null ? n : "Someone";
            }
        }
        return "Someone";
    }

    @Override public void reportSolved(Puzzle puzzle, UUID by) {
        Run run = puzzle.context().run;
        RunData d = dataOf(run);
        if (d == null) return;
        PuzzleRecord rec = puzzle.context().record;
        if (rec.status == PuzzleStatus.SOLVED || d.solving.contains(puzzle)) return;
        d.solving.add(puzzle);
        WorldPuzzle wp = puzzle instanceof WorldPuzzle w ? w : null;
        SolveTiming timing = wp != null ? wp.solveTiming() : null;
        if (timing == null) timing = SolveTiming.fromLead(wp != null ? wp.greenLeadTicks() : GREEN_LEAD_TICKS);
        String name = nameOf(run, by);
        int index = run.state.puzzles.indexOf(rec);
        Runnable announce = () -> {
            if (wp == null) return;
            String phrase = wp.solvedPhrase();
            if (phrase != null) Services.channels().chat(PuzzleLines.solved(name, phrase));
        };
        Runnable complete = () -> {
            d.solving.remove(puzzle);
            rec.status = PuzzleStatus.SOLVED;
            rec.solvedBy = by;
        };
        Runnable green = () -> {
            if (wp == null || wp.rewardSignalAtGreen()) rewardChestOpened(puzzle, by);
            // A puzzle room's one secret is its reward chest: it counts now, so the room can turn green.
            if (wp == null || wp.countsRewardAsSecret()) {
                RoomRecord rr = run.state.room(rec.roomIndex);
                if (rr != null && rr.secretsFound < rr.secretsMax()) {
                    WorldPos spot = null;
                    if (wp != null) {
                        List<WorldPos> spots = wp.rewardSpots();
                        if (!spots.isEmpty()) spot = spots.get(0);
                    }
                    Services.secrets().found(run, rec.roomIndex, SecretKind.CHEST, by, spot != null ? spot.x : 0, spot != null ? spot.y : 0, spot != null ? spot.z : 0);
                }
            }
            run.bus.post(new PuzzleSolved(run.t(), index, rec.roomIndex, by));
        };
        // Steps due on the same tick run green, then the line, then Completed.
        List<Pair<Integer, Runnable>> steps = new ArrayList<>(List.of(Pair.of(timing.green, green), Pair.of(timing.chat, announce), Pair.of(timing.complete, complete)));
        steps.sort((a, b) -> Integer.compare(a.first, b.first));
        for (Pair<Integer, Runnable> s : steps) {
            if (s.first <= 0) s.second.run();
            else later(d, run.serverTick + s.first, s.second);
        }
    }

    @Override public void reportFailed(Puzzle puzzle, UUID by, String reason) {
        Run run = puzzle.context().run;
        PuzzleRecord rec = puzzle.context().record;
        if (rec.status == PuzzleStatus.SOLVED) return;
        rec.status = PuzzleStatus.FAILED;
        rec.failures++;
        WorldPuzzle wp = puzzle instanceof WorldPuzzle w ? w : null;
        if (wp == null || wp.printsFailLine()) Services.channels().chat(PuzzleLines.failed(nameOf(run, by), reason));
        int index = run.state.puzzles.indexOf(rec);
        run.bus.post(new PuzzleFailed(run.t(), index, rec.roomIndex, by, reason));
        // Players fetch the draft from their sack a tick after the fail line: the sack line and the item arrive in the same tick.
        if (PuzzleKinds.resettable.contains(rec.kind) && by != null) {
            RunData d = dataOf(run);
            if (d == null) giveDraft(by);
            else later(d, run.serverTick + 1, () -> {
                Services.channels().chat(PuzzleLines.SACK_MOVED, new Audience.Only(by));
                giveDraft(by);
            });
        }
    }

    @Override public void rewardChestOpened(Puzzle puzzle, UUID by) {
        List<WorldPos> spots = puzzle instanceof WorldPuzzle wp ? wp.rewardSpots() : List.of();
        if (spots == null) return;
        for (WorldPos s : spots) sendRewardSignal(new BlockPos(s.x, s.y, s.z));
    }

    /** Odin's chest-open signal: a cherry-log block event at the chest's position, to every player. */
    public static void sendRewardSignal(BlockPos pos) {
        ClientboundBlockEventPacket packet = new ClientboundBlockEventPacket(pos, Blocks.CHERRY_LOG, 1, 1);
        for (ServerPlayer p : Sim.players()) p.connection.send(packet);
    }

    private static void later(RunData d, int at, Runnable what) { d.later.add(new Later(at, what)); }

    /** Sets a puzzle back to its start (the reset item, the Quiz starting over): the tab row goes back to Incomplete. */
    public static void resetPuzzle(Puzzle puzzle) {
        PuzzleRecord rec = puzzle.context().record;
        if (rec.status == PuzzleStatus.SOLVED) return;
        rec.status = PuzzleStatus.UNSOLVED;
        guard(puzzle.kind() + " onReset", puzzle::onReset);
    }

    // ---- the reset item

    /** The lines under the draft's name, as Hypixel shows them. */
    private static final List<String> DRAFT_LORE = List.of(
            "§7Can be used to reset a puzzle room", "§7when in dungeons.", "", "§7A 'reset' button seems to have been",
            "§7hidden somewhere in the floor, just", "§7in case something goes wrong...", "", "§cDungeons only!", "", "§5§lEPIC DUNGEON ITEM");

    /** The Architect's First Draft: a paper with that name (dark purple, not italic) and Hypixel's lore. */
    public static ItemStack draftItem() {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(DRAFT_NAME).withStyle(st -> st.withColor(ChatFormatting.DARK_PURPLE).withItalic(false)));
        List<Component> lore = new ArrayList<>();
        for (String l : DRAFT_LORE) lore.add(Sim.legacy(l).copy().withStyle(st -> st.withItalic(false)));
        s.set(DataComponents.LORE, new ItemLore(lore));
        return s;
    }

    private static boolean isDraft(ItemStack s) { return !s.isEmpty() && s.getHoverName().getString().equals(DRAFT_NAME); }

    /** Puts a draft in a player's inventory (what the sack command does on Hypixel); the sack line is printed by the caller. */
    public static void giveDraft(UUID player) {
        ServerPlayer p = null;
        for (ServerPlayer it : Sim.players()) if (it.getUUID().equals(player)) { p = it; break; }
        if (p == null) return;
        if (p.getInventory().contains(PuzzlesModule::isDraft)) return;
        p.getInventory().add(draftItem());
    }

    // ---- player interaction

    @Override public InteractionResult onUseBlock(Run run, ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        RunData d = dataOf(run);
        if (d == null) return null;
        BlockPos pos = hit.getBlockPos();
        RoomRecord rr = run.state.roomAt(pos.getX() + 0.5, pos.getZ() + 0.5);
        if (rr == null) return null;
        Puzzle puzzle = d.byRoom.get(rr.index());
        if (puzzle == null) return null;
        if (puzzle instanceof WorldPuzzle wp && !wp.activated) return null;
        return puzzle.onInteract(player.getUUID(), pos.getX(), pos.getY(), pos.getZ()) ? InteractionResult.SUCCESS : null;
    }

    @Override public InteractionResult onUseEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        RunData d = dataOf(run);
        if (d == null) return null;
        Puzzle puzzle = puzzleOfEntity(d, entity);
        if (puzzle == null) return null;
        Component cn = entity.getCustomName();
        if (cn == null) return null;
        String name = cn.getString();
        return puzzle.onNpcClick(player.getUUID(), name) ? InteractionResult.SUCCESS : null;
    }

    @Override public InteractionResult onAttackEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        RunData d = dataOf(run);
        if (d == null) return null;
        if (!(puzzleOfEntity(d, entity) instanceof WorldPuzzle puzzle)) return null;
        return puzzle.onAttack(player.getUUID(), entity) ? InteractionResult.SUCCESS : null;
    }

    private static Puzzle puzzleOfEntity(RunData d, Entity e) {
        Set<String> tags = e.entityTags();
        if (!tags.contains(WorldPuzzle.TAG)) return null;
        String roomTag = null;
        for (String t : tags) if (t.startsWith(WorldPuzzle.TAG_ROOM)) { roomTag = t; break; }
        if (roomTag == null) return null;
        Integer room = toIntOrNull(roomTag.substring(WorldPuzzle.TAG_ROOM.length()));
        if (room == null) return null;
        return d.byRoom.get(room);
    }

    @Override public InteractionResult onUseItem(Run run, ServerPlayer player, InteractionHand hand) {
        RunData d = dataOf(run);
        if (d == null) return null;
        ItemStack held = player.getItemInHand(hand);
        if (!isDraft(held)) return null;
        RoomRecord rr = run.state.roomAt(player.getX(), player.getZ());
        Puzzle puzzle = rr != null ? d.byRoom.get(rr.index()) : null;
        PuzzleRecord rec = puzzle != null ? puzzle.context().record : null;
        if (puzzle == null || rec == null || !PuzzleKinds.resettable.contains(rec.kind) || rec.status == PuzzleStatus.SOLVED) {
            Sim.note("The " + DRAFT_NAME + " only works in an unsolved Higher Or Lower, Three Weirdos or Tic Tac Toe room.");
            return InteractionResult.FAIL;
        }
        held.shrink(1);
        resetPuzzle(puzzle);
        Services.channels().chat(PuzzleLines.reset(rec.tabName), new Audience.Only(player.getUUID()));
        return InteractionResult.SUCCESS;
    }

    // ---- dev commands

    private static Integer toIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static PuzzleRecord recordArg(Run run, String arg) {
        if (run == null) return null;
        Integer i = toIntOrNull(arg);
        int n = i != null ? i : -1;
        return n >= 0 && n < run.state.puzzles.size() ? run.state.puzzles.get(n) : null;
    }

    @Override public List<AdsCommand> devCommands() {
        return List.of(
                new AdsCommand("puzzles", "/ads dev puzzles", "Lists the puzzle rooms of the run: kind, status, where, and whether the room is built.", true, null, ctx -> {
                    Run run = ctx.run();
                    RunData d = run != null ? dataOf(run) : null;
                    if (run == null || d == null) { ctx.reply("No run is on."); return; }
                    ctx.reply(PuzzleLines.tabHeadline(run.state.puzzles.size()));
                    for (int i = 0; i < run.state.puzzles.size(); i++) {
                        PuzzleRecord r = run.state.puzzles.get(i);
                        PlacedRoom room = run.plan.rooms.get(r.roomIndex);
                        Puzzle p = d.byRoom.get(r.roomIndex);
                        int cx = (int) centreX(room);
                        int cz = (int) centreZ(room);
                        String built = p == null ? "no module" : p instanceof WorldPuzzle wp ? (wp.activated ? "built" : "not built yet") : "hard puzzle";
                        ctx.reply("#" + i + " " + r.tabName + " (" + r.kind + ") " + r.status + " " + PuzzleLines.glyph(r.status) + " room " + room.name + " at " + cx + ", " + cz + ", " + built);
                    }
                }),
                new AdsCommand("puzzlesolve", "/ads dev puzzlesolve <number>", "Solves a puzzle by its number from /ads dev puzzles.", true, null, ctx -> {
                    Run run = ctx.run();
                    PuzzleRecord rec = recordArg(run, ctx.arg(0));
                    Puzzle p = rec != null ? puzzleOf_(run, rec.roomIndex) : null;
                    if (run == null || rec == null) { ctx.reply("Usage: /ads dev puzzlesolve <number> (see /ads dev puzzles)."); return; }
                    if (p == null) {
                        rec.status = PuzzleStatus.SOLVED;
                        rec.solvedBy = ctx.player.getUUID();
                        run.bus.post(new PuzzleSolved(run.t(), run.state.puzzles.indexOf(rec), rec.roomIndex, ctx.player.getUUID()));
                    } else INSTANCE.reportSolved(p, ctx.player.getUUID());
                    ctx.reply("Solved " + rec.tabName + ".");
                }),
                new AdsCommand("puzzlewake", "/ads dev puzzlewake <number>", "Builds a puzzle room and wakes it at once (the blazes and the chest, the Ice Fill chests, Tic Tac Toe's first mark) without walking there.", true, null, ctx -> {
                    Run run = ctx.run();
                    PuzzleRecord rec = recordArg(run, ctx.arg(0));
                    Puzzle pz = rec != null ? puzzleOf_(run, rec.roomIndex) : null;
                    WorldPuzzle p = pz instanceof WorldPuzzle w ? w : null;
                    if (rec == null || p == null) { ctx.reply("Usage: /ads dev puzzlewake <number> (see /ads dev puzzles; Higher Or Lower, Ice Fill and Tic Tac Toe wake)."); return; }
                    p.wakeNow();
                    ctx.reply("Woke " + rec.tabName + ".");
                }),
                new AdsCommand("puzzlereset", "/ads dev puzzlereset <number>", "Puts a puzzle back to its start.", true, null, ctx -> {
                    Run run = ctx.run();
                    PuzzleRecord rec = recordArg(run, ctx.arg(0));
                    Puzzle p = rec != null ? puzzleOf_(run, rec.roomIndex) : null;
                    if (rec == null || p == null) { ctx.reply("Usage: /ads dev puzzlereset <number> (see /ads dev puzzles)."); return; }
                    rec.status = PuzzleStatus.UNSOLVED;
                    guard("reset", p::onReset);
                    ctx.reply("Reset " + rec.tabName + ".");
                }),
                new AdsCommand("draft", "/ads dev draft", "Gives you the Architect's First Draft (the puzzle reset item).", true, null, ctx -> {
                    ctx.player.getInventory().add(draftItem());
                    ctx.reply("Here is the " + DRAFT_NAME + ".");
                }),
                new AdsCommand("puzzledraw", "/ads dev puzzledraw [count]", "Shows a draw of puzzle rooms by the measured odds (2/3/4/5 rooms; Blaze rooms never both).", false, ctx -> {
                    KRandom rnd = new KRandom(System.nanoTime());
                    Integer n = toIntOrNull(ctx.arg(0));
                    List<PuzzleDraw.Design> picks = n != null ? PuzzleDraw.drawDesigns(rnd, Math.clamp(n, 1, 10)) : PuzzleDraw.draw(rnd);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < picks.size(); i++) { if (i > 0) sb.append(", "); sb.append(picks.get(i).room); }
                    ctx.reply.accept(PuzzleLines.tabHeadline(picks.size()) + ": " + sb);
                }));
    }

    private static Puzzle puzzleOf_(Run run, int roomIndex) { return INSTANCE.puzzleOf(run, roomIndex); }

    private static void guard(String what, Runnable block) {
        try { block.run(); } catch (Throwable t) { Ec.LOG.error("[ads] puzzles: " + what + " failed", t); }
    }

    /** UNMEASURED fallback for a puzzle that is not a WorldPuzzle: the measured lead of the green mark over Completed. */
    public static final int GREEN_LEAD_TICKS = 0;
}
