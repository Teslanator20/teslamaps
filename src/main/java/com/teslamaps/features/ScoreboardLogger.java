package com.teslamaps.features;

// Ported from NoammAddons' ScoreboardLogger.kt
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ScoreboardLogger {

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("logs");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static BufferedWriter writer;
    private static List<String> lastRaw;
    private static List<String> last;
    private static Object lastLevel;

    private ScoreboardLogger() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ScoreboardLogger").when(() -> cfg().scoreboardLogger)
                .tick(ScoreboardLogger::tick)
                .config("Dev & Debug", b -> {
                    b.toggle("Scoreboard Change Logger", () -> cfg().scoreboardLogger, v -> {
                        cfg().scoreboardLogger = v;
                        if (!v) reset();
                    }).desc("Continuous /tmap debug dumptab: every scoreboard change goes into config/teslamaps/logs, one file per world")
                            .tags("debug", "dump", "sidebar");
                    b.toggle("  Full Snapshot", () -> cfg().scoreboardLoggerFull, v -> cfg().scoreboardLoggerFull = v)
                            .desc("Off = only the changed lines");
                    b.toggle("  Keep Color Codes", () -> cfg().scoreboardLoggerColors, v -> cfg().scoreboardLoggerColors = v);
                    b.toggle("  Only In Dungeons", () -> cfg().scoreboardLoggerDungeonsOnly, v -> cfg().scoreboardLoggerDungeonsOnly = v);
                    b.button("  Open Logs Folder", ScoreboardLogger::openFolder);
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("debug").then(ClientCommands.literal("dumptab")
                        .then(ClientCommands.literal("live").executes(c -> {
                            cfg().scoreboardLogger = !cfg().scoreboardLogger;
                            if (!cfg().scoreboardLogger) reset();
                            TeslaMapsConfig.save();
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fScoreboard logger "
                                    + (cfg().scoreboardLogger ? "§aon §7(config/teslamaps/logs)" : "§coff")));
                            return 1;
                        }))))));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        TeslaMapsConfig c = cfg();
        if (c.scoreboardLoggerDungeonsOnly && !DungeonManager.isInDungeon()) return;

        List<String> raw = ScoreboardUtils.getScoreboardLines();
        if (raw == lastRaw) return;
        lastRaw = raw;

        List<String> now = new ArrayList<>(raw.size());
        for (String line : raw) now.add(c.scoreboardLoggerColors ? line : StringUtil.strip(line));
        if (now.equals(last)) return;
        List<String> previous = last == null ? List.of() : last;
        last = now;
        if (now.isEmpty() && previous.isEmpty()) return;

        List<String> out = new ArrayList<>();
        if (c.scoreboardLoggerFull) {
            for (int i = 0; i < now.size(); i++) out.add((i + 1) + ". " + now.get(i));
        } else {
            int max = Math.max(previous.size(), now.size());
            for (int i = 0; i < max; i++) {
                String o = i < previous.size() ? previous.get(i) : null;
                String n = i < now.size() ? now.get(i) : null;
                if (n != null && n.equals(o)) continue;
                out.add((i + 1) + ". " + (n == null ? "(removed)" : n));
            }
        }
        write(out);
    }

    private static void write(List<String> lines) {
        try {
            if (writer == null) {
                Files.createDirectories(DIR);
                writer = Files.newBufferedWriter(DIR.resolve("scoreboard_" + LocalDateTime.now().format(FILE_STAMP) + ".log"),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            writer.write("== " + LocalTime.now() + " ==\n");
            for (String line : lines) writer.write("  " + line + "\n");
            writer.write("\n");
            writer.flush();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Scoreboard logger could not write", e);
            reset();
        }
    }

    private static void reset() {
        if (writer != null) {
            try { writer.close(); } catch (Exception ignored) {}
        }
        writer = null;
        lastRaw = null;
        last = null;
    }

    private static void openFolder() {
        try {
            Files.createDirectories(DIR);
            net.minecraft.util.Util.getPlatform().openFile(DIR.toFile());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Could not open logs folder", e);
        }
    }
}
