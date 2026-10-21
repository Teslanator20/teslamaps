package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/ArenaFixes.kt

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.run.ArenaRegion;
import com.teslamaps.adm.sim.util.KRandom;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class ArenaFixes {
    private ArenaFixes() {}

    static final class Run {
        final int[] init;
        final int[] events;
        Run(int[] init, int[] events) { this.init = init; this.events = events; }
    }

    private static final class Table {
        final BlockState[] states;
        final List<Run> runs;
        final JsonObject root;
        Table(BlockState[] states, List<Run> runs, JsonObject root) { this.states = states; this.runs = runs; this.root = root; }
    }

    private static volatile boolean tableDone = false;
    private static Table table;

    private static Table table() {
        if (tableDone) return table;
        synchronized (ArenaFixes.class) {
            if (!tableDone) {
                table = loadTable();
                tableDone = true;
            }
            return table;
        }
    }

    private static int[] flat(JsonArray a) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement e : a) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsInt());
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static Table loadTable() {
        try {
            String text;
            try (InputStream in = ArenaFixes.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/arena-fixes.json")) {
                if (in == null) return null;
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray sa = root.getAsJsonArray("states");
            BlockState[] states = new BlockState[sa.size()];
            for (int i = 0; i < states.length; i++) states[i] = Arena.parse(sa.get(i).getAsString());
            List<Run> runs = new ArrayList<>();
            for (JsonElement r : root.getAsJsonArray("runs")) {
                JsonObject o = r.getAsJsonObject();
                runs.add(new Run(flat(o.getAsJsonArray("init")), flat(o.getAsJsonArray("events"))));
            }
            return new Table(states, runs, root);
        } catch (Throwable t) {
            Ec.LOG.warn("[p3sim] arena-fixes.json unreadable", t);
            return null;
        }
    }

    private static volatile int pick = 0;

    public static Arena.Data patch(Arena.Data base) {
        Table t = table();
        Arena.Data ext;
        try {
            ext = Arena.readBin("arena-ext.bin");
        } catch (Throwable e) {
            Ec.LOG.warn("[p3sim] arena-ext.bin unreadable", e);
            ext = null;
        }
        boolean clip = ArenaRegion.active();
        int x0 = Math.min(base.x0, ext != null ? ArenaRegion.clippedMin(ext.x0, clip) : base.x0);
        int y0 = Math.min(base.y0, ext != null ? ext.y0 : base.y0);
        int z0 = Math.min(base.z0, ext != null ? ArenaRegion.clippedMin(ext.z0, clip) : base.z0);
        int x1 = Math.max(base.x0 + base.w, ext != null ? ext.x0 + ext.w : 0);
        int y1 = Math.max(base.y0 + base.h, ext != null ? ext.y0 + ext.h : 0);
        int z1 = Math.max(base.z0 + base.d, ext != null ? ext.z0 + ext.d : 0);
        int w = x1 - x0, h = y1 - y0, d = z1 - z0;
        List<BlockState> states = new ArrayList<>(List.of(base.states));
        Map<BlockState, Integer> index = new HashMap<>();
        for (int i = 0; i < states.size(); i++) index.putIfAbsent(states.get(i), i);
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        short airIdx = (short) idx(states, index, air);
        short[] cells = new short[w * h * d];
        java.util.Arrays.fill(cells, airIdx);
        for (int y = 0; y < base.h; y++) for (int z = 0; z < base.d; z++) {
            System.arraycopy(base.cells, (y * base.d + z) * base.w, cells, (((y + base.y0 - y0) * d) + (z + base.z0 - z0)) * w + (base.x0 - x0), base.w);
        }
        if (ext != null) {
            for (int y = 0; y < ext.h; y++) for (int z = 0; z < ext.d; z++) for (int x = 0; x < ext.w; x++) {
                BlockState s = ext.states[ext.cells[(y * ext.d + z) * ext.w + x]];
                if (s.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) continue;
                if (!ArenaRegion.keeps(ext.x0 + x, ext.z0 + z, clip)) continue;
                put(cells, states, index, x0, y0, z0, x1, y1, z1, w, d, ext.x0 + x, ext.y0 + y, ext.z0 + z, s);
            }
        }
        if (t != null) {
            BlockState[] st = t.states;
            for (JsonElement e : t.root.getAsJsonArray("y63")) {
                JsonArray a = e.getAsJsonArray();
                JsonArray best = null;
                for (JsonElement o : a.get(2).getAsJsonArray()) {
                    JsonArray oa = o.getAsJsonArray();
                    if (st[oa.get(0).getAsInt()].isAir()) continue;
                    if (best == null || oa.get(1).getAsInt() > best.get(1).getAsInt()) best = oa;
                }
                if (best == null) continue;
                put(cells, states, index, x0, y0, z0, x1, y1, z1, w, d, a.get(0).getAsInt(), 63, a.get(1).getAsInt(), st[best.get(0).getAsInt()]);
            }
            pick = KRandom.DEFAULT.nextInt(t.runs.size());
        }
        return new Arena.Data(x0, y0, z0, w, h, d, states.toArray(new BlockState[0]), cells);
    }

    private static int idx(List<BlockState> states, Map<BlockState, Integer> index, BlockState s) {
        Integer i = index.get(s);
        if (i != null) return i;
        states.add(s);
        int n = states.size() - 1;
        index.put(s, n);
        return n;
    }

    private static void put(short[] cells, List<BlockState> states, Map<BlockState, Integer> index,
                            int x0, int y0, int z0, int x1, int y1, int z1, int w, int d, int x, int y, int z, BlockState s) {
        if (x >= x0 && x < x1 && y >= y0 && y < y1 && z >= z0 && z < z1)
            cells[(((y - y0) * d) + (z - z0)) * w + (x - x0)] = (short) idx(states, index, s);
    }

    // player heads

    private static final class Skull {
        final int x, y, z;
        final ResolvableProfile profile;
        Skull(int x, int y, int z, ResolvableProfile profile) { this.x = x; this.y = y; this.z = z; this.profile = profile; }
    }

    private static volatile Map<Long, List<Skull>> skulls;

    private static Map<Long, List<Skull>> skullMap() {
        Map<Long, List<Skull>> m = skulls;
        if (m != null) return m;
        synchronized (ArenaFixes.class) {
            if (skulls != null) return skulls;
            Map<Long, List<Skull>> out = new HashMap<>();
            Ec.safely("p3sim skulls", () -> {
                Table t = table();
                if (t == null) return;
                JsonObject root = t.root;
                List<String> tex = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("skullTex")) tex.add(e.getAsString());
                for (JsonElement e : root.getAsJsonArray("skulls")) {
                    JsonArray a = e.getAsJsonArray();
                    JsonArray ida = a.get(5).getAsJsonArray();
                    int[] id = new int[4];
                    for (int i = 0; i < 4; i++) id[i] = ida.get(i).getAsInt();
                    UUID uuid = new UUID(((long) id[0] << 32) | (id[1] & 0xffffffffL), ((long) id[2] << 32) | (id[3] & 0xffffffffL));
                    String tx = tex.get(a.get(3).getAsInt());
                    GameProfile gp = new GameProfile(uuid, a.get(4).getAsString(), new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tx))));
                    int x = a.get(0).getAsInt(), z = a.get(2).getAsInt();
                    out.computeIfAbsent(key(x >> 4, z >> 4), k -> new ArrayList<>()).add(new Skull(x, a.get(1).getAsInt(), z, ResolvableProfile.createResolved(gp)));
                }
            });
            skulls = out;
            return out;
        }
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xffffffffL); }

    public static void reskull(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SkullBlock)) return;
        List<Skull> list = skullMap().get(key(pos.getX() >> 4, pos.getZ() >> 4));
        if (list == null) return;
        Skull s = null;
        for (Skull k : list) if (k.x == pos.getX() && k.y == pos.getY() && k.z == pos.getZ()) { s = k; break; }
        if (s == null) return;
        BlockEntity b = level.getBlockEntity(pos);
        if (!(b instanceof SkullBlockEntity be)) return;
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, s.profile);
        be.applyComponentsFromItemStack(stack);
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    public static void skulls(ChunkAccess chunk) {
        List<Skull> list = skullMap().get(key(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4));
        if (list == null) return;
        for (Skull s : list) {
            BlockPos pos = new BlockPos(s.x, s.y, s.z);
            BlockState state = chunk.getBlockState(pos);
            if (!(state.getBlock() instanceof SkullBlock)) continue;
            SkullBlockEntity be = new SkullBlockEntity(pos, state);
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, s.profile);
            be.applyComponentsFromItemStack(stack);
            chunk.setBlockEntity(be);
        }
    }

    // the recorded run's changes

    private static final class Crusher {
        final int back, front, zMin, zMax;
        final int[] cycle;
        final int xMin, xMax;

        Crusher(int back, int front, int zMin, int zMax, int[] cycle) {
            this.back = back; this.front = front; this.zMin = zMin; this.zMax = zMax; this.cycle = cycle;
            this.xMin = Math.min(back, front);
            this.xMax = Math.max(back, front);
        }

        boolean has(int x, int y, int z) { return x >= xMin && x <= xMax && z >= zMin && z <= zMax && y >= 116 && y <= 124; }

        static final int[] YS = {117, 118, 119, 121, 122, 123};
    }

    private static List<Crusher> CRUSHERS() {
        return List.of(new Crusher(14, 12, 101, 102, null), new Crusher(2, 3, 82, 84, new int[]{1, 0, 1, 2}));
    }

    private static final int CRUSHER_STEP = 35;
    private static final int CRUSHER_HOLD = 40;

    private static Crusher crusherAt(int x, int y, int z) {
        for (Crusher c : CRUSHERS()) if (c.has(x, y, z)) return c;
        return null;
    }

    public static final class Replay {
        private final Run run;
        private final BlockState[] states;
        private int ptr = 0;
        private int crushBase = 0;
        private HashMap<Integer, Integer> crushOut = null;
        private boolean catchUp = false;
        private HashSet<BlockPos> placed = null;

        Replay(Run run, BlockState[] states) { this.run = run; this.states = states; }

        private void driveCrushers(int n) {
            if (crushOut == null) crushOut = new HashMap<>();
            HashMap<Integer, Integer> out = crushOut;
            List<Crusher> cs = CRUSHERS();
            for (int ci = 0; ci < cs.size(); ci++) {
                Crusher c = cs.get(ci);
                int[] cyc = c.cycle;
                if (cyc == null) continue;
                int k = n <= crushBase ? 0 : (n - crushBase) / CRUSHER_STEP;
                int want = cyc[k % cyc.length];
                Integer cur = out.get(ci);
                if (cur != null && cur == want) continue;
                out.put(ci, want);
                int step = c.front < c.back ? -1 : 1;
                for (int i = 0; i <= Math.abs(c.front - c.back); i++) {
                    int x = c.back + i * step;
                    for (int y : Crusher.YS) for (int z = c.zMin; z <= c.zMax; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        Blocks.set(pos, i < want ? net.minecraft.world.level.block.Blocks.POLISHED_GRANITE.defaultBlockState() : Arena.data().get(x, y, z));
                    }
                }
            }
        }

        public void begin(int n0) {
            crushBase = n0;
            crushOut = null;
            driveCrushers(n0);
            int[] a = run.init;
            for (int i = 0; i < a.length; i += 4)
                if (a[i] != -3 && crusherAt(a[i], a[i + 1], a[i + 2]) == null) place(a[i], a[i + 1], a[i + 2], states[a[i + 3]]);
            catchUp = true;
            try {
                tick(n0 - 1);
            } finally {
                catchUp = false;
            }
        }

        public void tick(int n) {
            if (!catchUp) driveCrushers(n);
            int[] e = run.events;
            while (ptr * 5 < e.length && e[ptr * 5] <= n) {
                int i = ptr * 5;
                ptr++;
                if (e[i + 1] == -3) continue;
                int s = e[i + 4];
                BlockPos pos = new BlockPos(e[i + 1], e[i + 2], e[i + 3]);
                Crusher crusher = crusherAt(pos.getX(), pos.getY(), pos.getZ());
                if (crusher != null && (crusher.cycle != null || e[i] < CRUSHER_HOLD)) continue;
                if (crusher != null && s >= 0 && states[s].is(net.minecraft.world.level.block.Blocks.POLISHED_GRANITE)) {
                    int step = crusher.front < crusher.back ? -1 : 1;
                    int x = crusher.back;
                    while (x != pos.getX()) {
                        BlockState cur = Blocks.get(new BlockPos(x, pos.getY(), pos.getZ()));
                        if (cur == null || !cur.is(net.minecraft.world.level.block.Blocks.POLISHED_GRANITE)) place(x, pos.getY(), pos.getZ(), states[s]);
                        x += step;
                    }
                }
                if (s < 0) {
                    BlockState cur = Blocks.get(pos);
                    if (cur != null && cur.is(net.minecraft.world.level.block.Blocks.POLISHED_GRANITE))
                        Blocks.set(pos, Arena.data().get(pos.getX(), pos.getY(), pos.getZ()));
                } else place(pos.getX(), pos.getY(), pos.getZ(), states[s]);
            }
        }

        private HashSet<BlockPos> placed() {
            if (placed == null) placed = new HashSet<>();
            return placed;
        }

        private void place(int x, int y, int z, BlockState s) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState cur = Blocks.get(pos);
            if (cur == null) return;
            if (s.isAir()) {
                if (!placed().remove(pos)) return;
                BlockState built = Arena.data().get(x, y, z);
                if (built.isAir() && cur.is(net.minecraft.world.level.block.Blocks.TNT) && !catchUp) {
                    primeCube(pos);
                    return;
                }
                Blocks.set(pos, built);
                return;
            }
            if (cur.hasBlockEntity()) return;
            placed().add(pos);
            Blocks.set(pos, s);
        }

        private void primeCube(BlockPos pos) {
            PrimedTnt tnt = new PrimedTnt(Sim.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, null) {
                @Override public boolean shouldBeSaved() { return false; }
            };
            tnt.setFuse(80);
            Sim.spawn(tnt);
            Sim.sound(SoundEvents.TNT_PRIMED, 1f, 1f, tnt.position(), SoundSource.BLOCKS);
            Fight.later(1, "tnt cube block", () -> {
                BlockState cur = Blocks.get(pos);
                if (cur != null && cur.is(net.minecraft.world.level.block.Blocks.TNT)) Blocks.set(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            });
            Fight.later(21, "tnt cube gone", tnt::discard);
        }
    }

    public static Replay replay() {
        Table t = table();
        if (t == null) return null;
        return new Replay(t.runs.get(Math.clamp(pick, 0, t.runs.size() - 1)), t.states);
    }
}
