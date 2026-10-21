package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Blocks.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.KRandom;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public final class Blocks {
    private Blocks() {}

    private static final LinkedHashSet<BlockPos> touched = new LinkedHashSet<>();

    public static void set(BlockPos pos, BlockState state) {
        ServerLevel level = SimServer.level();
        if (level == null) return;
        touched.add(pos.immutable());
        Arena.set(level, pos, state);
    }

    public static void set(int x, int y, int z, BlockState state) { set(new BlockPos(x, y, z), state); }

    public static BlockState get(BlockPos pos) {
        ServerLevel level = SimServer.level();
        return level == null ? null : level.getBlockState(pos);
    }

    public static void restoreAll() {
        ServerLevel level = SimServer.level();
        if (level == null) return;
        anims.clear();
        done.clear();
        conveyor = null;
        Set<BlockPos> hits = clientHits != null ? clientHits : Collections.emptySet();
        HashSet<BlockPos> sync = new HashSet<>(touched);
        sync.addAll(hits);
        clientHits = null;
        for (BlockPos p : touched) Arena.restore(level, p);
        touched.clear();
        int fixed = Arena.resync(level);
        if (fixed > 0) Ec.LOG.info("[p3sim] restore: {} more arena blocks put back", fixed);
        resend(hits);
        Fight.later(10, "ghost block sync", () -> resend(sync));
        Fight.later(40, "ghost block sync", () -> resend(hits));
    }

    private static HashSet<BlockPos> clientHits = null;

    public static void clientHit(BlockPos pos) {
        if (clientHits == null) clientHits = new HashSet<>();
        clientHits.add(pos.immutable());
    }

    public static void resend(Collection<BlockPos> positions) {
        ServerLevel level = SimServer.level();
        if (level == null) return;
        if (positions.isEmpty()) return;
        Map<SectionPos, List<BlockPos>> bySection = new LinkedHashMap<>();
        for (BlockPos p : positions) bySection.computeIfAbsent(SectionPos.of(p), k -> new ArrayList<>()).add(p);
        List<ClientboundSectionBlocksUpdatePacket> packets = new ArrayList<>();
        for (Map.Entry<SectionPos, List<BlockPos>> e : bySection.entrySet()) {
            SectionPos sp = e.getKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(sp.x(), sp.z());
            if (chunk == null) continue;
            int si = chunk.getSectionIndexFromSectionY(sp.y());
            if (si < 0 || si >= chunk.getSections().length) continue;
            ShortOpenHashSet shorts = new ShortOpenHashSet();
            for (BlockPos p : e.getValue()) shorts.add(SectionPos.sectionRelativePos(p));
            packets.add(new ClientboundSectionBlocksUpdatePacket(sp, shorts, chunk.getSection(si)));
        }
        for (ServerPlayer p : Sim.players()) for (ClientboundSectionBlocksUpdatePacket pk : packets) p.connection.send(pk);
    }

    // recorded animations

    public static final class Frame {
        public final int dt;
        public final BlockPos pos;
        public final BlockState state;
        public final float chance;

        public Frame(int dt, BlockPos pos, BlockState state, float chance) {
            this.dt = dt; this.pos = pos; this.state = state; this.chance = chance;
        }

        public Frame(int dt, BlockPos pos, BlockState state) { this(dt, pos, state, 1f); }
    }

    public static final class Anim {
        public final String name;
        public final String event;
        public final List<Frame> frames;
        private Set<BlockPos> positions;

        public Anim(String name, String event, List<Frame> frames) {
            this.name = name; this.event = event; this.frames = frames;
        }

        public synchronized Set<BlockPos> positions() {
            if (positions == null) {
                HashSet<BlockPos> s = new HashSet<>();
                for (Frame f : frames) s.add(f.pos);
                positions = s;
            }
            return positions;
        }

        public int length() { return frames.isEmpty() ? 0 : frames.get(frames.size() - 1).dt; }
    }

    private static volatile Map<String, Anim> library;

    private static Map<String, Anim> library() {
        Map<String, Anim> l = library;
        if (l != null) return l;
        synchronized (Blocks.class) {
            if (library != null) return library;
            HashMap<String, Anim> out = new HashMap<>();
            for (String file : List.of("anims-p3.json", "anims-p124.json")) Ec.safely("p3sim " + file, () -> {
                String text;
                try (InputStream in = Blocks.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/" + file)) {
                    if (in == null) return;
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                JsonObject root = JsonParser.parseString(text).getAsJsonObject();
                for (Map.Entry<String, JsonElement> en : root.entrySet()) {
                    String name = en.getKey();
                    JsonElement v = en.getValue();
                    if (name.startsWith("_") || !v.isJsonObject()) continue;
                    JsonObject o = v.getAsJsonObject();
                    JsonArray fr = o.getAsJsonArray("frames");
                    if (fr == null) continue;
                    HashMap<String, BlockState> states = new HashMap<>();
                    List<Frame> frames = new ArrayList<>();
                    for (JsonElement e : fr) {
                        JsonArray a = e.getAsJsonArray();
                        String s = a.get(4).getAsString();
                        BlockState st = states.computeIfAbsent(s, k -> Arena.parse(k.contains(":") ? k : "minecraft:" + k));
                        frames.add(new Frame(a.get(0).getAsInt(), new BlockPos(a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()), st,
                                a.size() > 5 ? a.get(5).getAsFloat() : 1f));
                    }
                    frames.sort(java.util.Comparator.comparingInt(f -> f.dt));
                    JsonElement ev = o.get("event");
                    out.put(name, new Anim(name, ev != null ? ev.getAsString() : "", frames));
                }
            });
            library = out;
            return out;
        }
    }

    public static Anim anim(String name) { return library().get(name); }

    public static JsonObject extra(String file, String key) {
        try (InputStream in = Blocks.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/" + file)) {
            if (in == null) return null;
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class Playing {
        final Anim anim;
        final int start;
        int next;
        final boolean[] skip;
        final boolean debris;
        int debrisNext = 0;

        Playing(Anim anim, int start) {
            this.anim = anim;
            this.start = start;
            this.next = 0;
            this.skip = new boolean[anim.frames.size()];
            for (int i = 0; i < skip.length; i++) {
                Frame f = anim.frames.get(i);
                skip[i] = f.chance < 1f && KRandom.DEFAULT.nextFloat() >= f.chance;
            }
            this.debris = anim.name.startsWith("gate") || anim.name.startsWith("door") || anim.name.equals("core");
        }
    }

    private static final ArrayList<Playing> anims = new ArrayList<>();
    private static final HashSet<String> done = new HashSet<>();

    public static void play(String name) { play(name, 0, Set.of(), 0); }

    public static void play(String name, int skip) { play(name, skip, Set.of(), 0); }

    public static void play(String name, int skip, Set<BlockPos> exclude) { play(name, skip, exclude, 0); }

    public static void play(String name, int skip, Set<BlockPos> exclude, int delay) {
        Anim a = library().get(name);
        if (a == null) {
            Ec.LOG.warn("[p3sim] no animation {}", name);
            return;
        }
        done.add(name);
        if (name.equals("core")) Fight.later(Math.max(delay, 1), "core bats", Blocks::coreBats);
        Playing p = new Playing(a, Fight.serverTick - skip + delay);
        if (!exclude.isEmpty()) for (int i = 0; i < a.frames.size(); i++) if (exclude.contains(a.frames.get(i).pos)) p.skip[i] = true;
        anims.add(p);
        advance(p);
        if (name.equals(STRIP)) conveyor = new Conveyor(p.start + a.length() + STRIP_PERIOD);
    }

    private static void coreBats() {
        BlockState gold = net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState();
        for (int n = 0; n < 35; n++) {
            Bat b = new Bat(EntityType.BAT, Sim.level());
            b.setSilent(true);
            b.setInvulnerable(true);
            b.setInvisible(true);
            b.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
            b.setNoAi(true);
            b.setNoGravity(true);
            double x = 52.0 + KRandom.DEFAULT.nextDouble() * 4.0;
            double y = 114.0 + KRandom.DEFAULT.nextDouble() * 6.0;
            b.snapTo(x, y, 54.5, KRandom.DEFAULT.nextFloat() * 360f, 0f);
            Sim.spawn(b);
            CarriedBlock block = new CarriedBlock(Sim.level(), gold);
            block.snapTo(x, y, 54.5, 0f, 0f);
            Sim.spawn(block);
            Fight.later(1, "core bat carry", () -> { if (!b.isRemoved() && !block.isRemoved()) block.startRiding(b, true, false); });
            for (int k = 7; k <= 30; k++) {
                final int kk = k;
                Fight.later(k, "core bat fall", () -> { if (!b.isRemoved()) b.snapTo(x, y - 0.5 * (kk - 6), 54.5, b.getYRot(), 0f); });
            }
            Fight.later(31, "core bat gone", () -> { block.discard(); b.discard(); });
        }
    }

    public static void finish(String name) { finish(name, Set.of()); }

    public static void finish(String name, Set<BlockPos> exclude) {
        Anim a = library().get(name);
        if (a == null) return;
        if (!done.add(name)) return;
        Playing p = new Playing(a, 0);
        for (int i = 0; i < a.frames.size(); i++) {
            Frame f = a.frames.get(i);
            if (!p.skip[i] && !exclude.contains(f.pos)) set(f.pos, f.state);
        }
    }

    public static void stop(String name) {
        anims.removeIf(p -> p.anim.name.equals(name));
        if (name.equals(STRIP)) conveyor = null;
    }

    public static boolean isPlaying(String name) {
        for (Playing p : anims) if (p.anim.name.equals(name)) return true;
        return false;
    }

    public static void tick() {
        if (!anims.isEmpty()) {
            for (Playing p : new ArrayList<>(anims)) advance(p);
            anims.removeIf(p -> p.next >= p.anim.frames.size());
        }
        Ec.safely("p3sim strip", Blocks::conveyorTick);
    }

    private static void advance(Playing p) {
        int now = Fight.serverTick - p.start;
        List<Frame> f = p.anim.frames;
        if (p.debris) while (p.debrisNext < f.size() && f.get(p.debrisNext).dt <= now + 1) {
            Frame fr = f.get(p.debrisNext);
            if (!p.skip[p.debrisNext] && fr.state.isAir()) debris(fr.pos);
            p.debrisNext++;
        }
        while (p.next < f.size() && f.get(p.next).dt <= now) {
            if (!p.skip[p.next]) set(f.get(p.next).pos, f.get(p.next).state);
            p.next++;
        }
    }

    private static int debrisTick = -1;
    private static int debrisCount = 0;
    private static final int DEBRIS_PER_TICK = 48;

    private static void debris(BlockPos pos) {
        ServerLevel level = SimServer.level();
        if (level == null) return;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) return;
        if (debrisTick != Fight.serverTick) {
            debrisTick = Fight.serverTick;
            debrisCount = 0;
        }
        if (debrisCount >= DEBRIS_PER_TICK) return;
        debrisCount++;
        Debris e = new Debris(level, s);
        e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        e.setDeltaMovement(Vec3.ZERO);
        Sim.spawn(e);
    }

    // the world at a phase start

    public static void prepare(Fight.Start start) {
        if (start == Fight.Start.P1) return;
        finish(STRIP);
        set(73, 221, 73, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
        for (int y = 222; y <= 224; y++) set(73, y, 73, net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS.defaultBlockState());
        finish("p1end");
        if (start == Fight.Start.P2) return;
        stormPillars();
        if (start != Fight.Start.P4) return;
        for (String a : List.of("p3start", "gate12", "door1", "ss_s1done", "gate23", "door2", "gate34", "door3", "core")) finish(a);
    }

    private static void stormPillars() {
        JsonObject p = extra("anims-p124.json", "pillars");
        if (p == null) return;
        JsonObject origins = p.getAsJsonObject("origins");
        List<int[]> footprint = new ArrayList<>();
        for (JsonElement e : p.getAsJsonArray("footprint")) {
            JsonArray a = e.getAsJsonArray();
            footprint.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
        }
        for (String name : List.of("Purple", "Yellow")) {
            JsonObject o = origins.getAsJsonObject(name);
            int x0 = o.get("x").getAsInt(), z0 = o.get("z").getAsInt();
            for (int[] dd : footprint) {
                for (int y = 175; y < PILLAR_BOTTOM; y++) set(x0 + dd[0], y, z0 + dd[1], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                for (int y = PILLAR_BOTTOM; y <= 189; y++) set(x0 + dd[0], y, z0 + dd[1], net.minecraft.world.level.block.Blocks.POLISHED_DIORITE.defaultBlockState());
            }
        }
    }

    // Maxor's conveyor strip

    private static final class Conveyor {
        int next;
        Conveyor(int next) { this.next = next; }
    }

    private static Conveyor conveyor = null;
    private static List<List<BlockPos>> stripRows;

    private static List<List<BlockPos>> stripRows() {
        if (stripRows != null) return stripRows;
        Anim a = library().get(STRIP);
        Set<BlockPos> pos = a != null ? a.positions() : Set.of();
        List<int[]> rows = new ArrayList<>();
        for (int z = 70; z <= 76; z++) rows.add(new int[]{224, z});
        for (int y = 225; y <= 226; y++) for (int z = 72; z <= 74; z++) rows.add(new int[]{y, z});
        List<List<BlockPos>> out = new ArrayList<>();
        for (int[] r : rows) {
            List<BlockPos> row = new ArrayList<>();
            for (int x = 34; x <= 112; x++) {
                BlockPos bp = new BlockPos(x, r[0], r[1]);
                if (pos.contains(bp) && !(bp.getX() == 73 && bp.getZ() == 73)) row.add(bp);
            }
            out.add(row);
        }
        stripRows = out;
        return out;
    }

    private static void conveyorTick() {
        Conveyor c = conveyor;
        if (c == null) return;
        if (!(Fight.phase instanceof P1Maxor maxor) || maxor.status().equals("Maxor dead")) {
            stop(STRIP);
            return;
        }
        if (Fight.serverTick < c.next || isPlaying(STRIP)) return;
        c.next = Fight.serverTick + STRIP_PERIOD;
        ServerLevel level = SimServer.level();
        if (level == null) return;
        for (List<BlockPos> row : stripRows()) {
            HashSet<BlockPos> inRow = new HashSet<>(row);
            HashMap<BlockPos, BlockState> old = new HashMap<>();
            for (BlockPos p : row) old.put(p, level.getBlockState(p));
            for (BlockPos p : row) {
                BlockPos west = p.west();
                BlockState s;
                if (inRow.contains(west)) s = old.get(west);
                else if (p.getY() != 224) s = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                else if (p.getX() == row.get(0).getX()) {
                    BlockState o = old.get(p.offset(6, 0, 0));
                    s = o != null ? o : net.minecraft.world.level.block.Blocks.GRAY_WOOL.defaultBlockState();
                } else s = net.minecraft.world.level.block.Blocks.GRAY_WOOL.defaultBlockState();
                set(p, s);
            }
        }
    }

    private static final String STRIP = "p1strip";
    private static final int STRIP_PERIOD = 10;
    private static final int PILLAR_BOTTOM = 183;
}
