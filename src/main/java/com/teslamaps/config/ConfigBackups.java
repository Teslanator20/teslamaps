package com.teslamaps.config;

import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Rotating timestamped copies of config.json, only when it changed since the newest copy. */
public final class ConfigBackups {
    private ConfigBackups() {}

    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("backups");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final long SAVE_GAP_MS = 5 * 60_000L;
    private static long lastCopy = 0;

    public static void register() {
        com.teslamaps.registry.Feature.of("ConfigBackups")
                .config("Profiles", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Backups");
                    b.toggle("Config Backups", () -> c.configBackups, v -> c.configBackups = v)
                            .desc("Keeps timestamped copies of config.json (on launch, on quit and at most every 5 minutes while saving) when it changed. Restore one from the list.")
                            .tags("backup", "restore", "undo");
                    b.slider("  Keep Backups", 3f, 50f, () -> (float) c.configBackupsMax, v -> c.configBackupsMax = Math.round(v), 0);
                    b.buttonRow(new String[]{"Back Up Now", "Restore..."}, new Runnable[]{
                            () -> {
                                TeslaMapsConfig.save();
                                boolean made = copy(TeslaMapsConfig.path());
                                var p = net.minecraft.client.Minecraft.getInstance().player;
                                if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        made ? "§b[TeslaMaps] §fConfig backed up." : "§b[TeslaMaps] §7No changes since the last backup."));
                            },
                            () -> net.minecraft.client.Minecraft.getInstance().setScreen(
                                    new com.teslamaps.screen.ConfigBackupScreen(b.screen()))});
                });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            if (TeslaMapsConfig.get().configBackups) copy(TeslaMapsConfig.path());
        });
    }

    /** Before config.json gets overwritten: copy the old file, throttled. */
    static void beforeSave(Path config) {
        if (!TeslaMapsConfig.get().configBackups) return;
        if (System.currentTimeMillis() - lastCopy < SAVE_GAP_MS) return;
        copy(config);
    }

    static void afterLoad(Path config) {
        if (TeslaMapsConfig.get().configBackups) copy(config);
    }

    /** @return true when a new copy was written. */
    public static boolean copy(Path config) {
        try {
            if (!Files.exists(config)) return false;
            lastCopy = System.currentTimeMillis();
            byte[] now = Files.readAllBytes(config);
            List<Path> all = list();
            if (!all.isEmpty() && Arrays.equals(Files.readAllBytes(all.get(0)), now)) return false;
            Files.createDirectories(DIR);
            Path out = DIR.resolve("config_" + LocalDateTime.now().format(STAMP) + ".json");
            Files.write(out, now);
            all.add(0, out);
            int max = Math.max(1, TeslaMapsConfig.get().configBackupsMax);
            for (int i = max; i < all.size(); i++) Files.deleteIfExists(all.get(i));
            return true;
        } catch (IOException e) {
            TeslaMaps.LOGGER.warn("Config backup failed", e);
            return false;
        }
    }

    /** Newest first. */
    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(DIR)) return out;
        try (var s = Files.list(DIR)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("config_") && n.endsWith(".json");
            }).forEach(out::add);
        } catch (IOException e) {
            TeslaMaps.LOGGER.warn("Listing config backups failed", e);
        }
        out.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        return out;
    }

    /** Current state is backed up first, then the chosen file becomes config.json and is loaded. */
    public static boolean restore(Path backup) {
        try {
            String json = Files.readString(backup);
            com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            TeslaMapsConfig.save();
            copy(TeslaMapsConfig.path());
            Files.writeString(TeslaMapsConfig.path(), json);
            TeslaMapsConfig.reloadFromDisk();
            return true;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Config restore failed", e);
            return false;
        }
    }
}
