package com.teslamaps.features;

// Ported from Skysoft's KeepTerrainLoaded.kt
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ClientPacketListenerChunkAccessor;
import com.teslamaps.utils.SkyblockUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Keeps visited chunks past the server view distance, cached on disk per island. */
public final class KeepTerrainLoaded {

    private static final int MAX_CONCURRENT_LOADS = 8;
    private static final int MAX_SCAN_CHECKS_PER_TICK = 64;
    private static final int MAX_SAVES_PER_TICK = 8;
    private static final int MAX_PENDING_SAVES = 512;
    private static final int MAX_DISTANCE = 32;
    private static final int MAX_PACKET_BYTES = 4 * 1024 * 1024;

    // tab-list area names; instanced or per-run worlds (dungeons, kuudra, mineshafts) stay out
    private static final Set<String> ISLANDS = Set.of(
            "hub", "the end", "dwarven mines", "dungeon hub", "the farming islands", "crystal hollows",
            "the park", "deep caverns", "gold mine", "garden", "spider's den", "jerry's workshop",
            "the rift", "crimson isle", "backwater bayou", "galatea", "moonglade marsh", "torrhus canyon",
            "safari", "lotus atoll");

    private static final boolean BOBBY = FabricLoader.getInstance().isModLoaded("bobby");

    private record Active(ClientLevel level, String island, Storage storage) {}
    private record PendingSave(Storage storage, RegistryAccess access, int sections, ClientboundLevelChunkWithLightPacket packet) {}

    private static final Set<ChunkPos> retained = new LinkedHashSet<>();
    private static final Set<Long> loading = new HashSet<>();
    private static final LinkedHashMap<Long, PendingSave> pendingSaves = new LinkedHashMap<>();
    private static final Map<String, Storage> storages = new HashMap<>();
    private static Active active;
    private static String island;
    private static boolean restoringServerDistance, applyingCached;
    private static ChunkPos scanCenter;
    private static int scanDistance = -1, scanIndex;
    private static int[] scanOffsets = new int[0];

    private KeepTerrainLoaded() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        com.teslamaps.registry.Feature.of("KeepTerrainLoaded")
                .tickAlways(KeepTerrainLoaded::tick)
                .config("Render", b -> {
                    b.label("World Performance");
                    b.toggle("Keep Terrain Loaded", () -> cfg().keepTerrainLoaded, v -> cfg().keepTerrainLoaded = v)
                            .desc("Caches chunks the server sent on disk per island and keeps them loaded past the server view distance"
                                    + " (up to your render distance). Off while Bobby is installed. Not in dungeons, Kuudra or mineshafts.")
                            .tags("bobby", "chunks", "render distance", "view distance");
                    b.info("Cache: config/teslamaps/terrain_cache");
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (h, mc) -> mc.execute(KeepTerrainLoaded::close));
    }

    private static boolean enabled() {
        return !BOBBY && cfg().keepTerrainLoaded && cfg().section("Render");
    }

    private static String currentIsland() {
        if (!enabled() || !SkyblockUtils.isOnHypixel()) return null;
        String a = SkyblockUtils.getArea();
        if (a == null || a.isEmpty()) return null;
        String key = a.toLowerCase(Locale.ROOT);
        return ISLANDS.contains(key) ? key : null;
    }

    public static int storageViewDistance(int viewDistance) {
        if (restoringServerDistance || island == null) return viewDistance;
        return Math.max(viewDistance, cacheDistance(Minecraft.getInstance()));
    }

    public static boolean didRetain(ClientLevel level, ChunkPos pos) {
        if (active == null || active.level != level || island == null || !island.equals(active.island)) return false;
        LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
        if (chunk == null) return false;
        chunk.clearAllBlockEntities();
        retained.add(pos);
        return true;
    }

    public static void onServerChunk(ClientLevel level, ClientboundLevelChunkWithLightPacket packet) {
        if (applyingCached || active == null || active.level != level || island == null || !island.equals(active.island)) return;
        ChunkPos pos = new ChunkPos(packet.getX(), packet.getZ());
        retained.remove(pos);
        pendingSaves.put(pos.pack(), new PendingSave(active.storage, level.registryAccess(), level.getSectionsCount(), packet));
        while (pendingSaves.size() > MAX_PENDING_SAVES) {
            Iterator<Long> it = pendingSaves.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    private static void tick() {
        if (active == null && pendingSaves.isEmpty() && !enabled()) {
            island = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        processPendingSaves();
        ClientLevel level = mc.level;
        island = level == null ? null : currentIsland();
        if (level == null || island == null) {
            deactivate(mc);
            return;
        }
        if (active == null || active.level != level || !island.equals(active.island)) activate(mc, level, island);
        if (active == null || active.level != level) return;

        Integer server = serverDistance(mc);
        if (server == null) return;
        int distance = cacheDistance(mc);
        level.getChunkSource().updateViewRadius(Math.max(server, distance));
        if (mc.player == null) return;
        ChunkPos center = mc.player.chunkPosition();
        if (!center.equals(scanCenter) || scanDistance != distance) resetScan(center, distance);
        processLoads(mc, level);
    }

    private static void activate(Minecraft mc, ClientLevel level, String isl) {
        if (active != null && active.level == level) {
            releaseRetained(level);
            resizeToServer(mc, level);
        } else {
            retained.clear();
        }
        if (serverDistance(mc) == null) return;
        active = new Active(level, isl, storages.computeIfAbsent(isl, KeepTerrainLoaded::openStorage));
        int distance = cacheDistance(mc);
        level.getChunkSource().updateViewRadius(Math.max(serverDistance(mc), distance));
        if (mc.player != null) resetScan(mc.player.chunkPosition(), distance);
    }

    private static Storage openStorage(String isl) {
        String dir = isl.replaceAll("[^a-z0-9]+", "_");
        return new Storage(FabricLoader.getInstance().getConfigDir().resolve("teslamaps/terrain_cache").resolve(dir));
    }

    private static void processPendingSaves() {
        for (int i = 0; i < MAX_SAVES_PER_TICK; i++) {
            Iterator<Map.Entry<Long, PendingSave>> it = pendingSaves.entrySet().iterator();
            if (!it.hasNext()) return;
            PendingSave s = it.next().getValue();
            it.remove();
            try {
                s.storage.save(new ChunkPos(s.packet.getX(), s.packet.getZ()), encode(s.packet, s.access), s.sections);
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Could not cache terrain chunk", t);
            }
        }
    }

    private static void processLoads(Minecraft mc, ClientLevel level) {
        Storage storage = active.storage;
        String isl = active.island;
        int checks = 0;
        while (scanIndex < scanOffsets.length / 2 && loading.size() < MAX_CONCURRENT_LOADS && checks++ < MAX_SCAN_CHECKS_PER_TICK) {
            int ox = scanOffsets[scanIndex * 2], oz = scanOffsets[scanIndex * 2 + 1];
            scanIndex++;
            ChunkPos pos = new ChunkPos(scanCenter.x() + ox, scanCenter.z() + oz);
            if (level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) != null) continue;
            long key = pos.pack();
            if (!loading.add(key)) continue;
            storage.load(pos, level.getSectionsCount()).whenComplete((bytes, err) -> mc.execute(() -> {
                loading.remove(key);
                if (err != null) TeslaMaps.LOGGER.warn("Could not load cached terrain chunk", err);
                else if (bytes != null) applyCached(mc, level, isl, pos, bytes);
            }));
        }
    }

    private static void applyCached(Minecraft mc, ClientLevel level, String isl, ChunkPos pos, byte[] bytes) {
        if (active == null || active.level != level || !isl.equals(active.island) || !inScan(pos)) return;
        if (level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) != null) return;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        try {
            ClientboundLevelChunkWithLightPacket packet = decode(bytes, level.registryAccess());
            if (packet.getX() != pos.x() || packet.getZ() != pos.z()) return;
            applyingCached = true;
            try {
                packet.handle(connection);
            } finally {
                applyingCached = false;
            }
            LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
            if (chunk != null) chunk.clearAllBlockEntities();
            retained.add(pos);
        } catch (Throwable t) {
            applyingCached = false;
            TeslaMaps.LOGGER.warn("Could not apply cached terrain chunk", t);
        }
    }

    private static void resetScan(ChunkPos center, int distance) {
        scanCenter = center;
        scanIndex = 0;
        if (scanDistance != distance) {
            scanDistance = distance;
            scanOffsets = offsets(distance);
        }
    }

    private static boolean inScan(ChunkPos pos) {
        return scanCenter != null && Math.abs(pos.x() - scanCenter.x()) <= scanDistance
                && Math.abs(pos.z() - scanCenter.z()) <= scanDistance;
    }

    private static void deactivate(Minecraft mc) {
        if (active != null && mc.level == active.level) {
            releaseRetained(active.level);
            resizeToServer(mc, active.level);
        }
        active = null;
        scanCenter = null;
        scanDistance = -1;
        scanOffsets = new int[0];
        scanIndex = 0;
    }

    private static void releaseRetained(ClientLevel level) {
        for (ChunkPos pos : retained) {
            level.getChunkSource().drop(pos);
            removeLight(level, pos);
        }
        retained.clear();
    }

    private static void removeLight(ClientLevel level, ChunkPos pos) {
        level.queueLightUpdate(() -> {
            if (level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) != null) return;
            LevelLightEngine light = level.getLightEngine();
            light.setLightEnabled(pos, false);
            for (int y = light.getMinLightSection(); y < light.getMaxLightSection(); y++) {
                SectionPos s = SectionPos.of(pos, y);
                light.queueSectionData(LightLayer.BLOCK, s, null);
                light.queueSectionData(LightLayer.SKY, s, null);
            }
            for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++) {
                light.updateSectionStatus(SectionPos.of(pos, y), true);
            }
        });
    }

    private static void resizeToServer(Minecraft mc, ClientLevel level) {
        Integer server = serverDistance(mc);
        if (server == null) return;
        restoringServerDistance = true;
        try {
            level.getChunkSource().updateViewRadius(server);
        } finally {
            restoringServerDistance = false;
        }
    }

    private static Integer serverDistance(Minecraft mc) {
        ClientPacketListener c = mc.getConnection();
        return c == null ? null : ((ClientPacketListenerChunkAccessor) c).teslamaps$serverChunkRadius();
    }

    private static int cacheDistance(Minecraft mc) {
        return Math.min(mc.options.renderDistance().get(), MAX_DISTANCE);
    }

    private static void close() {
        pendingSaves.clear();
        loading.clear();
        for (Storage s : storages.values()) {
            try {
                s.close();
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Could not close terrain cache", t);
            }
        }
        storages.clear();
        retained.clear();
        active = null;
        island = null;
        restoringServerDistance = false;
        applyingCached = false;
        scanCenter = null;
        scanDistance = -1;
        scanOffsets = new int[0];
        scanIndex = 0;
    }

    // center first, then rings outwards; flat x/z pairs
    private static int[] offsets(int distance) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{0, 0});
        for (int r = 1; r <= distance; r++) {
            for (int x = -r; x <= r; x++) {
                out.add(new int[]{x, -r});
                out.add(new int[]{x, r});
            }
            for (int z = -r + 1; z < r; z++) {
                out.add(new int[]{-r, z});
                out.add(new int[]{r, z});
            }
        }
        int[] flat = new int[out.size() * 2];
        for (int i = 0; i < out.size(); i++) {
            flat[i * 2] = out.get(i)[0];
            flat[i * 2 + 1] = out.get(i)[1];
        }
        return flat;
    }

    private static byte[] encode(ClientboundLevelChunkWithLightPacket packet, RegistryAccess access) {
        ByteBuf source = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(source, access);
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buf, packet);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            source.release();
        }
    }

    private static ClientboundLevelChunkWithLightPacket decode(byte[] bytes, RegistryAccess access) {
        if (bytes.length > MAX_PACKET_BYTES) throw new IllegalArgumentException("cached chunk too large");
        ByteBuf source = Unpooled.wrappedBuffer(bytes);
        try {
            return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(source, access));
        } finally {
            source.release();
        }
    }

    private static final class Storage extends IOWorker {
        Storage(Path dir) {
            super(new RegionStorageInfo("teslamaps", Level.OVERWORLD, "terrain"), dir, false);
        }

        void save(ChunkPos pos, byte[] packet, int sections) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("protocol", SharedConstants.getProtocolVersion());
            tag.putInt("sections", sections);
            tag.putByteArray("packet", packet);
            store(pos, tag).exceptionally(t -> {
                TeslaMaps.LOGGER.warn("Could not write terrain cache chunk", t);
                return null;
            });
        }

        CompletableFuture<byte[]> load(ChunkPos pos, int sections) {
            return loadAsync(pos).thenApply(stored -> {
                CompoundTag tag = stored.orElse(null);
                if (tag == null) return null;
                if (tag.getIntOr("protocol", -1) != SharedConstants.getProtocolVersion()) return null;
                if (tag.getIntOr("sections", -1) != sections) return null;
                byte[] b = tag.getByteArray("packet").orElse(null);
                return b != null && b.length <= MAX_PACKET_BYTES ? b : null;
            });
        }
    }
}
