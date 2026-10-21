package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/SimWorld.kt

import com.teslamaps.adm.sim.run.RunLauncher;
import com.teslamaps.adm.sim.run.WorldEnv;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class SimWorld {
    private SimWorld() {}

    public static final String NAME = "p3sim";
    private static final String MARKER = "p3sim-arena.txt";
    private static final int WORLD_VERSION = 3;

    private static volatile String arenaVersion;

    public static String arenaVersion() {
        String v = arenaVersion;
        if (v != null) return v;
        CRC32 crc = new CRC32();
        for (String f : List.of("arena.bin", "arena-ext.bin", "arena-fixes.json")) {
            try (InputStream in = SimWorld.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/" + f)) {
                if (in != null) crc.update(in.readAllBytes());
            } catch (Exception ignored) {
            }
        }
        v = Long.toHexString(crc.getValue()) + "-" + WORLD_VERSION;
        arenaVersion = v;
        return v;
    }

    public static volatile boolean opening = false;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    public static void open() { open(false); }

    public static void open(boolean forRun) {
        if (!forRun) RunLauncher.abort();
        Minecraft mc = mc();
        mc.execute(() -> {
            if (P3Sim.inSim()) return;
            if (mc.level != null) mc.disconnectFromWorld(Component.literal("Opening ADS"));
            opening = true;
            try { openOrCreate(); } catch (Throwable t) { failed("open", t); }
        });
    }

    private static void openOrCreate() throws Exception {
        var source = mc().getLevelSource();
        if (Files.isDirectory(source.getBaseDir().resolve(NAME))) {
            try (var access = source.createAccess(NAME)) { access.deleteLevel(); }
        }
        create();
    }

    public static void leave() {
        Minecraft mc = mc();
        mc.execute(() -> {
            if (!P3Sim.inSim()) return;
            mc.disconnectFromWorld(Component.literal("Leaving ADS"));
            mc.setScreen(new TitleScreen());
        });
    }

    public static void rebuild() { rebuild(false); }

    public static void rebuild(boolean forRun) {
        if (!forRun) RunLauncher.abort();
        Minecraft mc = mc();
        mc.execute(() -> {
            if (P3Sim.inSim() && !P3Sim.localSim()) {
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8[§6ADS§8] §7Only the host can rebuild the world."));
                return;
            }
            if (mc.level != null) mc.disconnectFromWorld(Component.literal("Rebuilding ADS"));
            opening = true;
            try {
                try (var access = mc.getLevelSource().createAccess(NAME)) { access.deleteLevel(); }
                create();
            } catch (Throwable t) { failed("rebuild", t); }
        });
    }

    private static void create() {
        Minecraft mc = mc();
        mc.setScreen(new GenericMessageScreen(Component.literal(RunLauncher.pending != null ? "Building the dungeon..." : "Building the F7 boss...")));
        boolean envPack = installEnvPack();
        WorldDataConfiguration dataConfiguration = !envPack ? WorldDataConfiguration.DEFAULT
                : new WorldDataConfiguration(new DataPackConfig(List.of("vanilla", WorldEnv.PACK_ID), List.of()), WorldDataConfiguration.DEFAULT.enabledFeatures());
        LevelSettings settings = new LevelSettings(
                NAME, GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(RunLauncher.pending != null ? Difficulty.NORMAL : Difficulty.PEACEFUL, false, true),
                true, dataConfiguration);
        mc.createWorldOpenFlows().createFreshLevel(NAME, settings, new WorldOptions(0L, false, false), provider -> {
            var biomes = provider.lookupOrThrow(Registries.BIOME);
            var plains = biomes.getOrThrow(Biomes.PLAINS);
            FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.of(HolderSet.empty()), plains, List.of());
            return WorldPresets.createFlatWorldDimensions(provider).replaceOverworldGenerator(provider, new FlatLevelSource(flat));
        }, new TitleScreen());
    }

    private static boolean installEnvPack() {
        try {
            String vanilla;
            try (InputStream in = SimWorld.class.getResourceAsStream("/data/minecraft/dimension_type/overworld.json")) {
                if (in == null) throw new IllegalStateException("the game's overworld dimension type was not found");
                vanilla = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            WorldEnv.installPack(mc().getLevelSource().getBaseDir().resolve(NAME), vanilla, format.major(), format.minor());
            return true;
        } catch (Throwable t) {
            Ec.LOG.warn("[ec] ADS world data pack not written; the world keeps the vanilla sky", t);
            return false;
        }
    }

    private static void failed(String what, Throwable t) {
        Ec.LOG.error("[ec] p3sim {} failed", what, t);
        opening = false;
        RunLauncher.abort();
        mc().setScreen(new TitleScreen());
    }

    public static boolean isBuilt(Path worldDir) {
        try {
            return Files.readString(worldDir.resolve(MARKER)).trim().equals(arenaVersion());
        } catch (Throwable t) {
            return false;
        }
    }

    public static void markBuilt(Path worldDir) {
        Ec.safely("p3sim marker", () -> {
            try { Files.writeString(worldDir.resolve(MARKER), arenaVersion()); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
