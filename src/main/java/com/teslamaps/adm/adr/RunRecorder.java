package com.teslamaps.adm.adr;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.mixin.AbstractArrowInvoker;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

// Ported from ADM's RunRecorder.kt
/**
 * One recording session: everything in one world, from the moment it loads. Gzipped JSON Lines,
 * each line has "k" (kind) and, for events, "t" (client ticks since the world loaded). Kept only
 * once the world turns out to be a dungeon; lines are buffered until then.
 * Disk writes run on one background thread, handed over once a tick.
 */
public class RunRecorder {
    static final int ABANDON_AFTER_TICKS = 20 * 60;
    private static final long[] POW10 = {1, 10, 100, 1000, 10000};
    private static final double ARC_SLACK = 0.1;
    private static final int NO_FLIGHT = 0;
    private static final int ARROW = 1;
    private static final int POWERED = 2;
    private static final Pattern SKIN_HASH = Pattern.compile("[0-9a-f]{20,80}");
    private static final Pattern FORMAT_CODES = Pattern.compile("§.");
    static final long FRAME_NS = 16_000_000L;
    private static final Set<BlockPos> SS_BUTTONS = new HashSet<>();
    static {
        SS_BUTTONS.add(new BlockPos(110, 121, 91));
        for (int y = 120; y <= 123; y++) for (int z = 92; z <= 95; z++) SS_BUTTONS.add(new BlockPos(110, y, z));
    }
    static final int FAR_HALF = 32;
    static final String NO_EQUIPMENT = "[\"\",\"\",\"\",\"\",\"\"]";
    private static final Pattern DYE = Pattern.compile("#[0-9a-f]{6}");
    static final int SKULL_CHUNKS = 12;
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path dir;
    private final boolean captureGeometry;
    private final boolean sim;

    private int tick = 0;
    private boolean confirmed = false;
    private boolean abandoned = false;
    private final List<String> backlog = new ArrayList<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "teslamaps-adr-writer");
        t.setDaemon(true);
        return t;
    });
    private BufferedWriter writer;
    private Path tempFile;
    private long linesWritten = 0L;

    private final LocalDateTime startedAt = LocalDateTime.now();
    private String floorName;
    private String partyKey = "";
    private final long pingsAtStart = ServerTickTimer.pings();

    public RunRecorder(Path dir, boolean captureGeometry, boolean sim) {
        this.dir = dir;
        this.captureGeometry = captureGeometry;
        this.sim = sim;
    }

    public boolean abandoned() { return abandoned; }
    public boolean confirmed() { return confirmed; }

    private static final class Tracked {
        double x, y, z;
        float yaw, headYaw;
        String name;
        int flight;
        double px, py, pz, vx, vy, vz, accel, inertia;
        double lx = Double.NaN, ly = Double.NaN, lz = Double.NaN;

        Tracked(double x, double y, double z, float yaw, String name, float headYaw, int flight) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.name = name; this.headYaw = headYaw; this.flight = flight;
        }

        boolean ballistic() { return flight != NO_FLIGHT; }

        void launch(double x, double y, double z, Vec3 v) {
            lx = x; ly = y; lz = z;
            px = r(x, 1000.0); py = r(y, 1000.0); pz = r(z, 1000.0);
            vx = r(v.x, 10000.0); vy = r(v.y, 10000.0); vz = r(v.z, 10000.0);
        }

        void fly() {
            if (flight == ARROW) {
                px += vx; py += vy; pz += vz; vx *= 0.99; vy = vy * 0.99 - 0.05; vz *= 0.99;
            } else {
                double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len > 1e-9) { vx += vx / len * accel; vy += vy / len * accel; vz += vz / len * accel; }
                vx *= inertia; vy *= inertia; vz *= inertia;
                px += vx; py += vy; pz += vz;
            }
        }

        String flightLine() {
            return ",\"x\":" + px + ",\"y\":" + py + ",\"z\":" + pz + ",\"v\":[" + vx + "," + vy + "," + vz + "]"
                    + (flight == POWERED ? ",\"a\":" + accel + ",\"i\":" + inertia : "");
        }

        private static double r(double v, double s) { return Math.round(v * s) / s; }
    }

    private final HashMap<Integer, Tracked> tracked = new HashMap<>();
    private final GeometryCapture geometry = new GeometryCapture(this::emit);
    private final HashMap<BlockState, Integer> palette = new HashMap<>();

    // ------------------------------------------------------------------ inputs

    public void onTick(ClientLevel level) {
        flushFrames();
        flushMouse();
        flushSsPending(true);
        tick++;
        if (!confirmed && sim) {
            if (AdrSimHooks.get(AdrSimHooks.adsInRun)) confirm();
            else if (tick > ABANDON_AFTER_TICKS) { abandon(); return; }
        } else if (!confirmed && AdrSimHooks.get(AdrSimHooks.p3SimWorld)) {
            abandon(); return;
        } else if (!confirmed) {
            if (DungeonManager.isInDungeon()) confirm();
            else if (tick > ABANDON_AFTER_TICKS || areaKnownNotDungeon()) { abandon(); return; }
        }
        long now = System.currentTimeMillis();
        if (lastClockTick < 0 || Math.abs(now - (lastClockMs + (tick - lastClockTick) * 50L)) > 50) {
            emit("{\"k\":\"time\",\"t\":" + tick + ",\"ms\":" + now + "}");
            lastClockMs = now; lastClockTick = tick;
        }
        int st = serverTickCount();
        if (st != lastServerTicks + (tick - lastServerTickAt)) emit("{\"k\":\"st\",\"t\":" + tick + ",\"n\":" + st + "}");
        lastServerTicks = st; lastServerTickAt = tick;
        recordEther();
        recordFloorAndParty();
        if (tick % 10 == 0) recordRooms();
        recordPlayers(level);
        recordHotbar();
        recordSwings(level);
        if (tick % 5 == 0) recordMapPlayers(level);
        recordEntities(level);
        recordRoomEnter();
        if (confirmed) recordSkulls(level);
        if (confirmed && captureGeometry) geometry.tick(level, tick);
        flushPending();
    }

    private static boolean areaKnownNotDungeon() {
        String area = SkyblockUtils.getArea();
        return !area.isEmpty() && !area.toLowerCase(Locale.ROOT).contains("catacombs");
    }

    // your own view every rendered frame, written as one "cam" line per tick
    private final StringBuilder frames = new StringBuilder();
    private float lastFrameYaw = Float.NaN;
    private float lastFramePitch = Float.NaN;
    private String heldFrame;
    private long lastFrameNs = 0L;

    public void onFrame(float partialTick, float yaw, float pitch) {
        long now = System.nanoTime();
        int fps = Mth.clamp(TeslaMapsConfig.get().admAdrCameraFps, 20, 160);
        if (now - lastFrameNs < 960_000_000L / fps) return;
        lastFrameNs = now;
        String entry = "[" + f2(partialTick) + "," + f2(Mth.wrapDegrees(yaw)) + "," + f2(pitch) + "]";
        if (yaw == lastFrameYaw && pitch == lastFramePitch) { heldFrame = entry; return; }
        if (heldFrame != null) { if (!frames.isEmpty()) frames.append(','); frames.append(heldFrame); }
        heldFrame = null;
        lastFrameYaw = yaw; lastFramePitch = pitch;
        if (!frames.isEmpty()) frames.append(',');
        frames.append(entry);
    }

    private void flushFrames() {
        if (frames.isEmpty()) return;
        emit("{\"k\":\"cam\",\"t\":" + tick + ",\"d\":[" + frames + "]}");
        frames.setLength(0);
    }

    public void onBlockUpdate(BlockPos pos, BlockState state) {
        emit("{\"k\":\"block\",\"t\":" + tick + ",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + ",\"s\":" + paletteIndex(state) + "}");
    }

    // ------------------------------------------------------------------ server ticks
    private int lastServerTicks = 0;
    private int lastServerTickAt = 0;
    private long lastClockMs = 0L;
    private int lastClockTick = -1;

    /** Server tick pings since the session started; safe on any thread. */
    public int serverTickCount() {
        return (int) (ServerTickTimer.pings() - pingsAtStart);
    }

    // ------------------------------------------------------------------ what you do
    public void onSlotClick(int slot, int button, String type) {
        emit("{\"k\":\"slotclick\",\"t\":" + tick + ",\"slot\":" + slot + ",\"button\":" + button + ",\"type\":" + str(type) + "}");
    }

    public void onBlockUse(BlockPos pos) {
        flushSsPending(false, pos);
        emit("{\"k\":\"use\",\"t\":" + tick + ",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + "}");
    }

    public void onTeleport(double x, double y, double z, float yaw, float pitch, List<String> rel) {
        StringBuilder r = new StringBuilder();
        if (!rel.isEmpty()) {
            r.append(",\"rel\":[");
            for (int i = 0; i < rel.size(); i++) { if (i > 0) r.append(','); r.append(str(rel.get(i))); }
            r.append(']');
        }
        emit("{\"k\":\"tp\",\"t\":" + tick + ",\"x\":" + n(x) + ",\"y\":" + n(y) + ",\"z\":" + n(z) + ",\"yaw\":" + w(yaw) + ",\"pitch\":" + a(pitch) + r + "}");
    }

    public void onBatSound(double x, double y, double z, float volume) {
        emit("{\"k\":\"batsound\",\"t\":" + tick + ",\"x\":" + n(x) + ",\"y\":" + n(y) + ",\"z\":" + n(z) + ",\"v\":" + f2(volume) + "}");
    }

    public void onPickup(int itemId, int collectorId) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(collectorId);
        String by = e instanceof Player p ? str(p.getName().getString()) : Integer.toString(collectorId);
        emit("{\"k\":\"pickup\",\"t\":" + tick + ",\"id\":" + itemId + ",\"by\":" + by + "}");
    }

    private String lastEther = "";
    private ItemStack lastEtherStack;

    private void recordEther() {
        LocalPlayer pl = Minecraft.getInstance().player;
        ItemStack stack = pl == null ? null : pl.getMainHandItem();
        if (stack != null && stack == lastEtherStack) return;
        lastEtherStack = stack;
        CustomData cd = stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
        var tag = cd == null ? null : cd.copyTag();
        String entry = "\"merge\":" + (tag == null ? 0 : tag.getIntOr("ethermerge", 0)) + ",\"tuners\":" + (tag == null ? 0 : tag.getIntOr("tuned_transmission", 0));
        if (entry.equals(lastEther)) return;
        lastEther = entry;
        emit("{\"k\":\"ether\",\"t\":" + tick + "," + entry + "}");
    }

    public void onChat(String message, String colored, Integer n) {
        String c = colored != null && !colored.equals(message) ? ",\"c\":" + str(colored) : "";
        String st = n != null ? ",\"n\":" + n : "";
        emit("{\"k\":\"chat\",\"t\":" + tick + st + ",\"m\":" + str(message) + c + "}");
    }

    public void onChestEvent(BlockPos pos, int openCount) {
        emit("{\"k\":\"bev\",\"t\":" + tick + ",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + ",\"b\":" + openCount + "}");
    }

    public void onRoomEnter(String name) {
        emit("{\"k\":\"room\",\"t\":" + tick + ",\"name\":" + str(name == null ? "Unknown" : name) + "}");
    }

    private boolean roomSeen = false;
    private DungeonRoom lastRoom;

    private void recordRoomEnter() {
        if (!DungeonManager.isInDungeon()) return;
        DungeonRoom r = DungeonManager.getCurrentRoom();
        if (roomSeen && r == lastRoom) return;
        roomSeen = true;
        lastRoom = r;
        onRoomEnter(r == null ? null : r.getName());
    }

    public void onGuiClose() {
        flushMouse();
        lastSlots.clear(); lastCarried = null; lastMouseX = Float.NaN;
        emit("{\"k\":\"guiclose\",\"t\":" + tick + "}");
    }

    // ------------------------------------------------------------------ your open container

    public void onContainerOpen(String title, String menu, int w, int h, List<int[]> slots) {
        lastSlots.clear(); lastCarried = null; lastMouseX = Float.NaN;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(slots.get(i)[0]).append(',').append(slots.get(i)[1]).append(']');
        }
        emit("{\"k\":\"gui\",\"t\":" + tick + ",\"title\":" + str(title) + ",\"menu\":" + str(menu) + ",\"w\":" + w + ",\"h\":" + h + ",\"slots\":[" + sb + "]}");
    }

    private final HashMap<Integer, String> lastSlots = new HashMap<>();
    private String lastCarried;

    public void onContainerTick(List<ItemStack> items, ItemStack carried) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            String entry = slotEntry(i, items.get(i));
            if (entry.equals(lastSlots.put(i, entry))) continue;
            if (!sb.isEmpty()) sb.append(',');
            sb.append(entry);
        }
        if (!sb.isEmpty()) emit("{\"k\":\"slots\",\"t\":" + tick + ",\"s\":[" + sb + "]}");
        String c = "\"id\":" + str(vanillaId(carried)) + ",\"count\":" + (carried.isEmpty() ? 0 : carried.getCount());
        if (!c.equals(lastCarried)) { lastCarried = c; emit("{\"k\":\"carried\",\"t\":" + tick + "," + c + "}"); }
    }

    private String slotEntry(int i, ItemStack stack) {
        ResolvableProfile prof = stack.get(DataComponents.PROFILE);
        String tex = prof == null ? null : texturesOf(prof.partialProfile().properties());
        String name = stack.isEmpty() ? "" : FORMAT_CODES.matcher(stack.getHoverName().getString()).replaceAll("");
        int glint = !stack.isEmpty() && (stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) || stack.hasFoil()) ? 1 : 0;
        return "[" + i + "," + str(vanillaId(stack)) + "," + (stack.isEmpty() ? 0 : stack.getCount()) + "," + str(tex == null ? "" : tex) + "," + str(name) + "," + glint + "]";
    }

    private final StringBuilder mouse = new StringBuilder();
    private long lastMouseNs = 0L;
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;
    private String heldMouse;

    public void onContainerMouse(float partialTick, float x, float y) {
        long now = System.nanoTime();
        if (now - lastMouseNs < FRAME_NS) return;
        lastMouseNs = now;
        String entry = "[" + f2(partialTick) + "," + f2(x) + "," + f2(y) + "]";
        if (x == lastMouseX && y == lastMouseY) { heldMouse = entry; return; }
        if (heldMouse != null) { if (!mouse.isEmpty()) mouse.append(','); mouse.append(heldMouse); }
        heldMouse = null;
        lastMouseX = x; lastMouseY = y;
        if (!mouse.isEmpty()) mouse.append(',');
        mouse.append(entry);
    }

    private void flushMouse() {
        if (mouse.isEmpty()) return;
        emit("{\"k\":\"mouse\",\"t\":" + tick + ",\"d\":[" + mouse + "]}");
        mouse.setLength(0);
    }

    public void onContainerClick(float x, float y, int button) {
        emit("{\"k\":\"click\",\"t\":" + tick + ",\"x\":" + f2(x) + ",\"y\":" + f2(y) + ",\"button\":" + button + "}");
    }

    /** Ends the session: closes the file on the writer thread and gives it its final name. */
    public void finish() {
        if (!confirmed) { abandon(); return; }
        flushSsPending(true);
        emit("{\"k\":\"end\",\"t\":" + tick + ",\"ms\":" + System.currentTimeMillis() + "}");
        flushPending();
        Path temp = tempFile;
        if (temp == null) return;
        String finalName = startedAt.format(STAMP) + "_" + AdrRunFiles.safeFloor(floorName) + ".jsonl.gz";
        long lines = linesWritten;
        int ticks = tick;
        BufferedWriter wr = writer;
        io.execute(() -> {
            try {
                wr.close();
                Path target = temp.resolveSibling(finalName);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                double mb = Files.size(target) / 1_000_000.0;
                if (TeslaMapsConfig.get().admAdrSavedMessage)
                    Adr.msg("§7ADR: saved run §f" + finalName + " §7(" + String.format(Locale.ROOT, "%.1f", mb) + " MB, " + lines + " lines, " + ticks / 20 + "s)");
            } catch (Throwable t) {
                TeslaMaps.LOGGER.error("[adr] failed to finish run file", t);
                Adr.msg("§cADR: failed to save the run (" + t.getClass().getSimpleName() + ")");
            }
        });
        io.shutdown();
    }

    // ------------------------------------------------------------------ lifecycle

    private void confirm() {
        try {
            Files.createDirectories(dir);
            Path temp = dir.resolve("recording-" + startedAt.format(STAMP) + ".jsonl.gz.part");
            writer = AdrRunFiles.writer(temp);
            tempFile = temp;
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[adr] could not open run file", e);
            Adr.msg("§cADR: could not open the run file (" + e.getClass().getSimpleName() + ")");
            abandon();
            return;
        }
        confirmed = true;
        LocalPlayer pl = Minecraft.getInstance().player;
        String self = pl == null ? "?" : pl.getName().getString();
        String version = modVersion("teslamaps");
        String mc = modVersion("minecraft");
        writeNow("{\"k\":\"meta\",\"format\":2,\"mod\":" + str(version) + ",\"mc\":" + str(mc) + ",\"self\":" + str(self)
                + ",\"startMs\":" + (System.currentTimeMillis() - tick * 50L) + ",\"confirmedAtTick\":" + tick
                + ",\"geometry\":" + captureGeometry + ",\"farHalf\":" + FAR_HALF + ",\"stx\":1" + simMeta() + "}");
        for (String l : backlog) writeNow(l);
        backlog.clear();
        flushPending();
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private String simMeta() {
        if (!sim) return "";
        Object[] l = null;
        try { l = AdrSimHooks.lastLaunch.get(); } catch (Throwable ignored) {}
        if (l == null || l.length < 2) return ",\"ads\":{}";
        return ",\"ads\":{\"layout\":" + str(String.valueOf(l[0])) + ",\"seed\":" + l[1] + "}";
    }

    void abandon() {
        abandoned = true;
        backlog.clear();
        tracked.clear();
        io.shutdown();
    }

    private void emit(String line) {
        if (abandoned) return;
        if (confirmed) writeNow(line);
        else backlog.add(line);
    }

    private final StringBuilder pending = new StringBuilder(1 << 14);

    private void writeNow(String line) {
        if (writer == null) return;
        linesWritten++;
        pending.append(line).append('\n');
    }

    private void flushPending() {
        BufferedWriter w = writer;
        if (w == null || pending.isEmpty()) return;
        String chunk = pending.toString();
        pending.setLength(0);
        io.execute(() -> {
            try { w.write(chunk); } catch (Throwable t) { TeslaMaps.LOGGER.error("[adr] write failed", t); }
        });
    }

    // ------------------------------------------------------------------ per-tick snapshots

    private static String className(String c) {
        if (c == null) return "Unknown";
        return switch (c) {
            case "Archer", "Berserk", "Healer", "Mage", "Tank" -> c;
            default -> "Unknown";
        };
    }

    private void recordFloorAndParty() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        String floor = f == null || f == DungeonFloor.UNKNOWN ? null : f.name();
        if (floor != null && !floor.equals(floorName)) {
            floorName = floor;
            emit("{\"k\":\"floor\",\"t\":" + tick + ",\"floor\":" + str(floor) + "}");
        }
        if (!DungeonManager.isInDungeon()) return;
        List<PlayerTracker.DungeonPlayer> party = PlayerTracker.getPlayers();
        if (party.isEmpty()) return;
        StringBuilder key = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < party.size(); i++) {
            PlayerTracker.DungeonPlayer p = party.get(i);
            String cls = className(p.getDungeonClass());
            if (i > 0) { key.append('|'); body.append(','); }
            key.append(p.getName()).append(':').append(cls);
            body.append('[').append(str(p.getName())).append(',').append(str(cls)).append(']');
        }
        if (!key.toString().equals(partyKey)) {
            partyKey = key.toString();
            emit("{\"k\":\"party\",\"t\":" + tick + ",\"m\":[" + body + "]}");
        }
    }

    private String roomsKey = "";

    private static String roomType(DungeonRoom r) {
        if (r.getType() == null) return "UNKNOWN";
        return switch (r.getType()) {
            case YELLOW -> "CHAMPION";
            default -> r.getType().name();
        };
    }

    private static String checkmark(CheckmarkState c) {
        if (c == null) return "UNDISCOVERED";
        return switch (c) {
            case UNEXPLORED -> "UNDISCOVERED";
            case FAILED -> "RED";
            default -> c.name();
        };
    }

    /** Every known room: [name, type, shape, rotation, checkmark, [[x,z]...], found, max, key?] (Odin's names). */
    private void recordRooms() {
        var rooms = DungeonManager.getGrid().getAllRooms();
        if (rooms.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (DungeonRoom r : rooms) {
            if (i++ > 0) sb.append(',');
            String rot = RoomKeys.rotation(r);
            sb.append('[').append(str(r.getName() == null ? "" : r.getName())).append(',').append(str(roomType(r))).append(',')
                    .append(str(RoomKeys.shape(r))).append(',').append(str(rot == null ? "" : rot)).append(',')
                    .append(str(checkmark(r.getCheckmarkState()))).append(",[");
            List<int[]> tiles = r.getComponents();
            for (int j = 0; j < tiles.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append('[').append(tiles.get(j)[0]).append(',').append(tiles.get(j)[1]).append(']');
            }
            sb.append("],").append(r.getFoundSecrets() < 0 ? -1 : r.getFoundSecrets()).append(',').append(r.getRoomData() == null ? -1 : r.getSecrets());
            String k = RoomKeys.key(r);
            if (k != null) sb.append(',').append(str(k));
            sb.append(']');
        }
        String body = sb.toString();
        if (body.equals(roomsKey)) return;
        roomsKey = body;
        emit("{\"k\":\"rooms\",\"t\":" + tick + ",\"r\":[" + body + "]}");
    }

    private final HashMap<String, String> lastPlayer = new HashMap<>();

    private String lastHotbar = "";
    private final ItemStack[] lastHotbarStacks = new ItemStack[9];
    private int lastHotbarSel = -1;

    private void recordHotbar() {
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl == null) return;
        Inventory inv = pl.getInventory();
        boolean same = lastHotbarSel == inv.getSelectedSlot();
        for (int i = 0; same && i < 9; i++) if (lastHotbarStacks[i] != inv.getItem(i)) same = false;
        if (same) return;
        for (int i = 0; i < 9; i++) lastHotbarStacks[i] = inv.getItem(i);
        lastHotbarSel = inv.getSelectedSlot();
        StringBuilder items = new StringBuilder("[");
        StringBuilder tex = new StringBuilder("{");
        boolean anyTex = false;
        for (int i = 0; i < 9; i++) {
            if (i > 0) items.append(',');
            items.append(str(vanillaId(inv.getItem(i))));
            ResolvableProfile prof = inv.getItem(i).get(DataComponents.PROFILE);
            String t = prof == null ? null : texturesOf(prof.partialProfile().properties());
            if (t != null) {
                if (anyTex) tex.append(',');
                anyTex = true;
                tex.append('"').append(i).append("\":").append(str(t));
            }
        }
        items.append(']');
        tex.append('}');
        String body = "\"items\":" + items + ",\"sel\":" + inv.getSelectedSlot() + ",\"tex\":" + tex;
        if (body.equals(lastHotbar)) return;
        lastHotbar = body;
        emit("{\"k\":\"hotbar\",\"t\":" + tick + "," + body + "}");
    }

    private final HashMap<String, String> lastHeldHead = new HashMap<>();
    private final HashMap<String, ItemStack> lastHeldStack = new HashMap<>();

    private void recordHeldHead(Player p, String name) {
        if (lastHeldStack.put(name, p.getMainHandItem()) == p.getMainHandItem()) return;
        ResolvableProfile prof = p.getMainHandItem().get(DataComponents.PROFILE);
        String tex = prof == null ? null : texturesOf(prof.partialProfile().properties());
        if (tex == null) tex = "";
        String prev = lastHeldHead.put(name, tex);
        if (!tex.equals(prev) && !(prev == null && tex.isEmpty()))
            emit("{\"k\":\"held\",\"t\":" + tick + ",\"name\":" + str(name) + ",\"tex\":" + str(tex) + "}");
    }

    private static final class PlayerRaw {
        double x = Double.NaN, y, z;
        float yRot, xRot;
        boolean crouching;
        ItemStack stack;
        String held = "", vanilla = "";
    }

    private final HashMap<String, PlayerRaw> playerRaw = new HashMap<>();
    private final HashSet<String> skinsWritten = new HashSet<>();

    private void recordPlayers(ClientLevel level) {
        StringBuilder sb = new StringBuilder();
        int changed = 0;
        HashSet<String> seen = new HashSet<>();
        for (AbstractClientPlayer p : level.players()) {
            String name = p.getUUID().version() == 2 ? p.getName().getString() + "#" + p.getId() : p.getName().getString();
            seen.add(name);
            if (skinsWritten.add(name)) {
                String t = texturesOf(p.getGameProfile().properties());
                if (t != null) emit("{\"k\":\"skin\",\"t\":" + tick + ",\"name\":" + str(name) + ",\"tex\":" + str(t) + "}");
            }
            recordEquipment(p, "\"name\":" + str(name), name);
            recordHeldHead(p, name);
            PlayerRaw raw = playerRaw.computeIfAbsent(name, k -> new PlayerRaw());
            ItemStack stack = p.getMainHandItem();
            if (lastPlayer.containsKey(name) && raw.x == p.getX() && raw.y == p.getY() && raw.z == p.getZ() && raw.yRot == p.getYRot()
                    && raw.xRot == p.getXRot() && raw.stack == stack && raw.crouching == p.isCrouching()) continue;
            raw.x = p.getX(); raw.y = p.getY(); raw.z = p.getZ(); raw.yRot = p.getYRot(); raw.xRot = p.getXRot(); raw.crouching = p.isCrouching();
            if (raw.stack != stack) {
                raw.stack = stack;
                raw.vanilla = vanillaId(stack);
                String sbId = stack.isEmpty() ? "" : ItemUtil.skyblockId(stack);
                raw.held = stack.isEmpty() ? "" : (sbId == null || sbId.isEmpty() ? raw.vanilla : sbId);
            }
            String entry = "[" + str(name) + "," + m(p.getX()) + "," + m(p.getY()) + "," + m(p.getZ()) + "," + w(p.getYRot()) + "," + a(p.getXRot())
                    + "," + str(raw.held) + "," + p.getUUID().version() + "," + str(raw.vanilla) + "," + (raw.crouching ? 1 : 0) + "]";
            if (entry.equals(lastPlayer.put(name, entry))) continue;
            if (changed++ > 0) sb.append(',');
            sb.append(entry);
        }
        if (changed > 0) emit("{\"k\":\"p\",\"t\":" + tick + ",\"d\":[" + sb + "]}");
        Iterator<String> it = lastPlayer.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (seen.contains(name)) continue;
            it.remove();
            playerRaw.remove(name);
            emit("{\"k\":\"pgone\",\"t\":" + tick + ",\"name\":" + str(name) + "}");
        }
    }

    // a new swing shows as swinging with swingTime -1 or 0, so -1 then 0 is one swing
    private final HashMap<String, Integer> lastSwingTime = new HashMap<>();

    private void recordSwings(ClientLevel level) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AbstractClientPlayer p : level.players()) {
            String name = p.getName().getString();
            Integer prevBoxed = lastSwingTime.put(name, p.swinging ? p.swingTime : 99);
            int prev = prevBoxed == null ? 99 : prevBoxed;
            if (!p.swinging || p.swingTime > 0 || prev == -1) continue;
            if (count++ > 0) sb.append(',');
            sb.append(str(name));
        }
        if (count > 0) emit("{\"k\":\"sw\",\"t\":" + tick + ",\"d\":[" + sb + "]}");
    }

    // ------------------------------------------------------------------ Simon Says clicks

    private BlockPos ssPending;
    private boolean ssPendingPowered;
    private int ssPendingTick;

    /** Your click on a Simon Says button is about to go through startUseItem. */
    public void onSsAttempt(BlockPos pos) {
        flushSsPending(true);
        ClientLevel level = Minecraft.getInstance().level;
        BlockState state = level == null ? null : level.getBlockState(pos);
        ssPendingPowered = state != null && state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
        ssPending = pos.immutable();
        ssPendingTick = tick;
    }

    private void flushSsPending(boolean blocked) {
        flushSsPending(blocked, null);
    }

    /** Reached useItemOn for that button: not blocked. Still pending later: a mod blocked it. */
    private void flushSsPending(boolean blocked, BlockPos used) {
        BlockPos p = ssPending;
        if (p == null) return;
        if (used != null && !used.equals(p)) return;
        ssPending = null;
        emit("{\"k\":\"ssclick\",\"t\":" + ssPendingTick + ",\"x\":" + p.getX() + ",\"y\":" + p.getY() + ",\"z\":" + p.getZ()
                + ",\"blocked\":" + (blocked ? 1 : 0) + ",\"powered\":" + (ssPendingPowered ? 1 : 0) + "}");
    }

    public static boolean isSsButton(BlockPos pos) {
        return SS_BUTTONS.contains(pos);
    }

    public void onSsSound(String id, double x, double y, double z, float volume, float pitch) {
        emit("{\"k\":\"sssound\",\"t\":" + tick + ",\"id\":" + str(id) + ",\"x\":" + n(x) + ",\"y\":" + n(y) + ",\"z\":" + n(z) + ",\"v\":" + f2(volume) + ",\"p\":" + f2(pitch) + "}");
    }

    // ------------------------------------------------------------------ teammates off the dungeon map

    private final HashMap<String, String> lastMapPos = new HashMap<>();

    private void recordMapPlayers(ClientLevel level) {
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;
        PlayerTracker.DungeonPlayer[] players = PlayerTracker.getPlayersOrdered();
        if (players == null) return;
        LocalPlayer me = Minecraft.getInstance().player;
        String self = me == null ? "" : me.getName().getString();
        List<PlayerTracker.DungeonPlayer> others = new ArrayList<>();
        for (PlayerTracker.DungeonPlayer p : players) if (p != null && !p.getName().equals(self)) others.add(p);
        List<int[]> markers = new ArrayList<>();
        for (int[] pos : MapScanner.getMapPlayerPositions()) if (pos[3] != 1) markers.add(pos);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int mi = 0;
        for (PlayerTracker.DungeonPlayer p : others) {
            if (!p.isAlive()) { lastMapPos.remove(p.getName()); continue; }
            int[] marker = mi < markers.size() ? markers.get(mi) : null;
            mi++;
            boolean rendered = false;
            for (AbstractClientPlayer e : level.players()) if (e.getName().getString().equals(p.getName())) { rendered = true; break; }
            if (rendered || marker == null) { lastMapPos.remove(p.getName()); continue; }
            double[] world = MapScanner.mapToWorldPosition(marker[0], marker[1]);
            if (world == null) continue;
            String entry = "[" + str(p.getName()) + "," + n(world[0]) + "," + n(world[1]) + "," + a(marker[2] * 22.5f) + "]";
            if (entry.equals(lastMapPos.put(p.getName(), entry))) continue;
            if (count++ > 0) sb.append(',');
            sb.append(entry);
        }
        if (count > 0) emit("{\"k\":\"mp\",\"t\":" + tick + ",\"d\":[" + sb + "]}");
    }

    // ------------------------------------------------------------------ entities

    private static final class Tag {
        final int mob;
        final double dx, dy, dz;
        Tag(int mob, double dx, double dy, double dz) { this.mob = mob; this.dx = dx; this.dy = dy; this.dz = dz; }
    }

    private final HashMap<Integer, Tag> tags = new HashMap<>();

    private static Tag tagOf(ClientLevel level, ArmorStand stand) {
        for (int back : new int[]{1, 3}) {
            if (!(level.getEntity(stand.getId() - back) instanceof LivingEntity mob)) continue;
            if (mob instanceof ArmorStand || mob instanceof Player) continue;
            double dx = q(stand.getX() - mob.getX()), dy = q(stand.getY() - mob.getY()), dz = q(stand.getZ() - mob.getZ());
            if (Math.abs(dx) <= 0.05 && Math.abs(dz) <= 0.05 && dy >= 0.0 && dy <= 4.0) return new Tag(mob.getId(), dx, dy, dz);
        }
        return null;
    }

    private static final class Named {
        final Component of;
        final String name, colored;
        Named(Component of, String name, String colored) { this.of = of; this.name = name; this.colored = colored; }
    }

    private final HashMap<Integer, Named> names = new HashMap<>();

    private Named namesOf(Entity e) {
        Component c = e.getCustomName();
        Named n = names.get(e.getId());
        if (n != null && n.of == c) return n;
        n = new Named(c, c == null ? "" : c.getString(), c == null ? "" : Adr.legacyText(c));
        names.put(e.getId(), n);
        return n;
    }

    private final HashSet<Integer> seenIds = new HashSet<>();

    private static boolean inGround(Entity e) {
        return e instanceof AbstractArrow && ((AbstractArrowInvoker) e).teslamaps$isInGround();
    }

    private void recordEntities(ClientLevel level) {
        seenIds.clear();
        StringBuilder moved = new StringBuilder();
        int movedCount = 0;
        LocalPlayer me = Minecraft.getInstance().player;
        boolean oddTick = tick % 2 == 1;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof Player) continue;
            if (!Double.isFinite(e.getX()) || !Double.isFinite(e.getY()) || !Double.isFinite(e.getZ())) continue;
            int id = e.getId();
            seenIds.add(id);
            Named named0 = namesOf(e);
            String name = named0.name;
            String colored = named0.colored;
            String c = !colored.isEmpty() && !colored.equals(name) ? ",\"c\":" + str(colored) : "";
            Tracked t = tracked.get(id);
            float headYaw = e instanceof LivingEntity le ? le.getYHeadRot() : e.getYRot();
            if (t == null) {
                int flight;
                if (!name.isEmpty()) flight = NO_FLIGHT;
                else if (e instanceof AbstractArrow) flight = inGround(e) ? NO_FLIGHT : ARROW;
                else if (e instanceof AbstractHurtingProjectile) flight = POWERED;
                else flight = NO_FLIGHT;
                Tracked tr = new Tracked(q(e.getX()), q(e.getY()), q(e.getZ()), deg(e.getYRot()), colored, deg(headYaw), flight);
                tracked.put(id, tr);
                if (e instanceof AbstractHurtingProjectile hp && flight == POWERED) {
                    tr.accel = Math.round(hp.accelerationPower * 10000) / 10000.0;
                    tr.inertia = e instanceof WitherSkull ws && ws.isDangerous() ? 0.73 : 0.95;
                }
                if (flight != NO_FLIGHT) {
                    tr.launch(e.getX(), e.getY(), e.getZ(), e.getDeltaMovement());
                    emit("{\"k\":\"spawn\",\"t\":" + tick + ",\"id\":" + id + ",\"type\":" + str(typeOf(e)) + tr.flightLine() + "}");
                    continue;
                }
                String block = e instanceof FallingBlockEntity fb ? ",\"block\":" + str(BlockStateParser.serialize(fb.getBlockState())) : "";
                String item = "";
                if (e instanceof ItemEntity ie) {
                    ItemStack it = ie.getItem();
                    item = ",\"item\":" + str(FORMAT_CODES.matcher(it.getHoverName().getString()).replaceAll("")) + ",\"itemId\":" + str(vanillaId(it));
                }
                String namedPart = name.isEmpty() ? "" : ",\"name\":" + str(name) + c;
                String living = "";
                if (e instanceof LivingEntity le) {
                    living = ",\"headYaw\":" + w(headYaw) + (le.isBaby() ? ",\"baby\":1" : "");
                }
                emit("{\"k\":\"spawn\",\"t\":" + tick + ",\"id\":" + id + ",\"type\":" + str(typeOf(e)) + namedPart + ",\"x\":" + n(e.getX())
                        + ",\"y\":" + n(e.getY()) + ",\"z\":" + n(e.getZ()) + ",\"yaw\":" + w(e.getYRot()) + living + block + item + "}");
                if (e instanceof ArmorStand as) {
                    recordStand(as);
                    Tag tg = tagOf(level, as);
                    if (tg != null) {
                        tags.put(id, tg);
                        emit("{\"k\":\"tag\",\"t\":" + tick + ",\"id\":" + id + ",\"of\":" + tg.mob + ",\"dx\":" + tg.dx + ",\"dy\":" + tg.dy + ",\"dz\":" + tg.dz + "}");
                    }
                }
                if (e instanceof ItemFrame f) recordFrame(f);
                continue;
            }
            if (t.ballistic()) {
                if (!inGround(e)) {
                    t.fly();
                    if (e.getX() == t.lx && e.getY() == t.ly && e.getZ() == t.lz) continue;
                    double dx = e.getX() - t.px, dy = e.getY() - t.py, dz = e.getZ() - t.pz;
                    if (dx * dx + dy * dy + dz * dz > ARC_SLACK * ARC_SLACK) {
                        t.launch(e.getX(), e.getY(), e.getZ(), e.getDeltaMovement());
                        emit("{\"k\":\"arc\",\"t\":" + tick + ",\"id\":" + id + t.flightLine() + "}");
                    }
                    continue;
                }
                t.flight = NO_FLIGHT;
            }
            if (e instanceof LivingEntity le) recordEquipment(le, "\"id\":" + id, "#" + id);
            if (e instanceof ArmorStand as) recordStand(as);
            if (e instanceof ItemFrame f) recordFrame(f);
            if (!colored.equals(t.name)) {
                t.name = colored;
                emit("{\"k\":\"name\",\"t\":" + tick + ",\"id\":" + id + ",\"name\":" + str(name) + c + "}");
            }
            double qx = q(e.getX()), qy = q(e.getY()), qz = q(e.getZ());
            float qYaw = deg(e.getYRot()), qHead = deg(headYaw);
            Tag tag = tags.get(id);
            if (tag != null) {
                Entity mob = level.getEntity(tag.mob);
                if (mob != null && Math.abs(mob.getX() + tag.dx - e.getX()) < 0.006 && Math.abs(mob.getY() + tag.dy - e.getY()) < 0.006
                        && Math.abs(mob.getZ() + tag.dz - e.getZ()) < 0.006) {
                    t.x = qx; t.y = qy; t.z = qz;
                    continue;
                }
                tags.remove(id);
                emit("{\"k\":\"tag\",\"t\":" + tick + ",\"id\":" + id + ",\"of\":null}");
            }
            if (oddTick && me != null && e.distanceToSqr(me) > FAR_HALF * FAR_HALF) continue;
            if (qx != t.x || qy != t.y || qz != t.z || qYaw != t.yaw || qHead != t.headYaw) {
                t.x = qx; t.y = qy; t.z = qz; t.yaw = qYaw; t.headYaw = qHead;
                if (movedCount++ > 0) moved.append(',');
                moved.append('[').append(id).append(',').append(m(e.getX())).append(',').append(m(e.getY())).append(',')
                        .append(m(e.getZ())).append(',').append((int) qYaw);
                if (e instanceof LivingEntity) moved.append(',').append((int) qHead);
                moved.append(']');
            }
        }
        if (movedCount > 0) emit("{\"k\":\"e\",\"t\":" + tick + ",\"d\":[" + moved + "]}");
        Iterator<Integer> it = tracked.keySet().iterator();
        while (it.hasNext()) {
            int id = it.next();
            if (seenIds.contains(id)) continue;
            it.remove();
            names.remove(id);
            tags.remove(id);
            lastEquipment.remove("#" + id);
            lastStacks.remove("#" + id);
            lastStand.remove(id);
            lastStandRaw.remove(id);
            lastFrame.remove(id);
            emit("{\"k\":\"gone\",\"t\":" + tick + ",\"id\":" + id + "}");
        }
    }

    private final HashMap<Integer, String> lastFrame = new HashMap<>();

    private void recordFrame(ItemFrame f) {
        String entry = "\"item\":" + str(vanillaId(f.getItem())) + ",\"rot\":" + f.getRotation();
        if (entry.equals(lastFrame.put(f.getId(), entry))) return;
        emit("{\"k\":\"frame\",\"t\":" + tick + ",\"id\":" + f.getId() + "," + entry + "}");
    }

    private final HashMap<String, String> lastEquipment = new HashMap<>();
    private final HashMap<String, ItemStack[]> lastStacks = new HashMap<>();
    private final HashMap<Integer, String> lastStand = new HashMap<>();
    private final HashMap<Integer, Object[]> lastStandRaw = new HashMap<>();

    private void recordStand(ArmorStand e) {
        int f = (e.isSmall() ? 1 : 0) | (e.isInvisible() ? 2 : 0) | (e.showArms() ? 4 : 0) | (!e.showBasePlate() ? 8 : 0) | (e.isMarker() ? 16 : 0);
        Rotations[] poses = {e.getHeadPose(), e.getBodyPose(), e.getLeftArmPose(), e.getRightArmPose(), e.getLeftLegPose(), e.getRightLegPose()};
        Object[] raw = lastStandRaw.get(e.getId());
        if (raw != null && (int) raw[0] == f) {
            boolean same = true;
            for (int i = 0; i < 6; i++) if (raw[i + 1] != poses[i]) { same = false; break; }
            if (same) return;
        }
        Object[] nr = new Object[7];
        nr[0] = f;
        System.arraycopy(poses, 0, nr, 1, 6);
        lastStandRaw.put(e.getId(), nr);
        StringBuilder pose = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) pose.append(',');
            pose.append(a(poses[i].x())).append(',').append(a(poses[i].y())).append(',').append(a(poses[i].z()));
        }
        String body = "\"f\":" + f + ",\"pose\":[" + pose + "]";
        if (body.equals(lastStand.put(e.getId(), body))) return;
        emit("{\"k\":\"stand\",\"t\":" + tick + ",\"id\":" + e.getId() + "," + body + "}");
    }

    private final HashMap<BlockPos, String> lastSkull = new HashMap<>();

    // a twentieth of the chunks each tick, each chunk once a second
    private void recordSkulls(ClientLevel level) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        int slice = tick % 20;
        for (int x = cx - SKULL_CHUNKS; x <= cx + SKULL_CHUNKS; x++) {
            for (int z = cz - SKULL_CHUNKS; z <= cz + SKULL_CHUNKS; z++) {
                if (Math.floorMod(x * 7 + z, 20) != slice) continue;
                if (!(level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof SkullBlockEntity skull)) continue;
                    ResolvableProfile prof = skull.getOwnerProfile();
                    String tex = prof == null ? null : texturesOf(prof.partialProfile().properties());
                    if (tex == null) continue;
                    BlockPos pos = be.getBlockPos();
                    if (tex.equals(lastSkull.put(pos.immutable(), tex))) continue;
                    emit("{\"k\":\"skull\",\"t\":" + tick + ",\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + ",\"tex\":" + str(tex) + "}");
                }
            }
        }
    }

    private void recordEquipment(LivingEntity e, String who, String key) {
        ItemStack[] now = {e.getMainHandItem(), e.getItemBySlot(EquipmentSlot.HEAD), e.getItemBySlot(EquipmentSlot.CHEST),
                e.getItemBySlot(EquipmentSlot.LEGS), e.getItemBySlot(EquipmentSlot.FEET)};
        ItemStack[] was = lastStacks.get(key);
        if (was != null && was[0] == now[0] && was[1] == now[1] && was[2] == now[2] && was[3] == now[3] && was[4] == now[4]) return;
        lastStacks.put(key, now);
        ResolvableProfile prof = now[1].get(DataComponents.PROFILE);
        String headTex = prof == null ? null : texturesOf(prof.partialProfile().properties());
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 5; i++) { if (i > 0) body.append(','); body.append(str(vanillaId(now[i]))); }
        body.append(']');
        if (headTex != null) body.append(",\"headTex\":").append(str(headTex));
        String full = body.toString();
        String same = DYE.matcher(full).replaceAll("");
        String previous = lastEquipment.put(key, same);
        if (same.equals(previous) || (previous == null && full.equals(NO_EQUIPMENT))) return;
        emit("{\"k\":\"eq\",\"t\":" + tick + "," + who + ",\"eq\":" + full + "}");
    }

    /** The game item id (a vanilla item_model wins, others after "@"), plus "#rrggbb" when dyed. */
    static String vanillaId(ItemStack stack) {
        if (stack.isEmpty()) return "";
        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        String model = modelId != null && modelId.getNamespace().equals("minecraft") ? modelId.toString() : null;
        String id = model != null ? model : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (modelId != null && model == null) id += "@" + modelId;
        DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
        if (dye == null) return id;
        return id + "#" + Integer.toHexString((dye.rgb() & 0xFFFFFF) | 0x1000000).substring(1);
    }

    private final HashMap<String, String> skinHashes = new HashMap<>();

    /** A skin as the viewer needs it: texture hash, ":s" for slim; the whole value if unreadable. */
    private String texturesOf(PropertyMap props) {
        if (props == null) return null;
        Iterator<Property> it = props.get("textures").iterator();
        if (!it.hasNext()) return null;
        String value = it.next().value();
        if (value == null) return null;
        return skinHashes.computeIfAbsent(value, v -> {
            try {
                JsonObject skin = JsonParser.parseString(new String(Base64.getDecoder().decode(v))).getAsJsonObject()
                        .getAsJsonObject("textures").getAsJsonObject("SKIN");
                String url = skin.get("url").getAsString();
                String hash = url.substring(url.lastIndexOf('/') + 1);
                if (!SKIN_HASH.matcher(hash).matches()) return v;
                JsonObject meta = skin.has("metadata") ? skin.getAsJsonObject("metadata") : null;
                boolean slim = meta != null && meta.has("model") && "slim".equals(meta.get("model").getAsString());
                return hash + (slim ? ":s" : "");
            } catch (Exception ex) {
                return v;
            }
        });
    }

    private int paletteIndex(BlockState state) {
        Integer i = palette.get(state);
        if (i != null) return i;
        int idx = palette.size();
        palette.put(state, idx);
        emit("{\"k\":\"pal\",\"i\":" + idx + ",\"s\":" + str(BlockStateParser.serialize(state)) + "}");
        return idx;
    }

    // ------------------------------------------------------------------ formatting

    private static String typeOf(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    private static String n(double v) { return fixed(v, 3); }
    private static double q(double v) { return Math.round(v * 100) / 100.0; }

    private static String m(double v) {
        long r = Math.round(v * 100);
        return r % 100 == 0 ? Long.toString(r / 100) : Double.toString(r / 100.0);
    }

    private static float deg(float v) { return (float) Math.round(Mth.wrapDegrees(v)); }
    private static String w(float v) { return a(Mth.wrapDegrees(v)); }
    private static String a(float v) { return fixed(v, 1); }
    private static String f2(float v) { return fixed(v, 2); }

    private static String fixed(double v, int decimals) {
        if (!Double.isFinite(v)) return "0";
        long scale = POW10[decimals];
        long r = Math.round(v * scale);
        long abs = Math.abs(r);
        StringBuilder sb = new StringBuilder(20);
        if (r < 0) sb.append('-');
        sb.append(abs / scale);
        if (decimals == 0) return sb.toString();
        sb.append('.');
        String frac = Long.toString(abs % scale);
        for (int i = frac.length(); i < decimals; i++) sb.append('0');
        return sb.append(frac).toString();
    }

    static String str(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') sb.append("\\\"");
            else if (ch == '\\') sb.append("\\\\");
            else if (ch == '\n') sb.append("\\n");
            else if (ch < ' ') sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.append('"').toString();
    }
}
