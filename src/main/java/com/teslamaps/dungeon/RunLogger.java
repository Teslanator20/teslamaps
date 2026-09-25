package com.teslamaps.dungeon;

// Ported from Devonian's RunsLogger.kt
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RunLogger {

    private static final Pattern SCORE = Pattern.compile("^Team Score: (\\d+) \\((.{1,2})\\).*$");
    private static final Pattern DEFEATED = Pattern.compile("^☠ Defeated .+ in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");
    private static final Pattern DEATHS = Pattern.compile("^Deaths: (\\d+)$");
    private static final Pattern SECRETS = Pattern.compile("^Secrets Found: (\\d+)$");
    private static final Pattern TIME_PART = Pattern.compile("(\\d+)([hms])");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_RUNS = 5000;

    public static final class Run {
        public String date;
        public String floor;
        public long at;
        public int score;
        public String rank = "";
        public String time = "";
        public int seconds;
        public boolean pb;
        public int deaths;
        public int secrets = -1;
        public Map<String, String> party = new LinkedHashMap<>();
        public List<String> rooms = new ArrayList<>();
    }

    private static List<Run> runs;
    private static Run current;
    private static long currentAt;
    private static boolean saved;

    private RunLogger() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RunLogger").section("Score & Splits").when(() -> cfg().runLogger)
                .serverChat(RunLogger::onChat)
                .tick(RunLogger::tick)
                .config("Score & Splits", b -> {
                    b.label("Run Logger");
                    b.toggle("Run Logger", () -> cfg().runLogger, v -> cfg().runLogger = v)
                            .desc("Saves every finished run (score, time, deaths, secrets, party). Needs the extra stats in chat.");
                    b.info("/tmap runs [floor] [today|all] for S+ rate, secrets, fastest and average time");
                });
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String m = StringUtil.stripTrim(raw);
        if (m.isEmpty()) return;
        if (current == null && !saved && (m.startsWith("The Catacombs - ") || m.startsWith("Master Mode The Catacombs - "))) {
            start();
            return;
        }
        if (current == null) return;
        Matcher x;
        if ((x = SCORE.matcher(m)).matches()) {
            current.score = Integer.parseInt(x.group(1));
            current.rank = x.group(2);
        } else if ((x = DEFEATED.matcher(m)).matches()) {
            current.time = x.group(1).trim();
            current.seconds = parseTime(current.time);
            current.pb = x.group(2) != null;
        } else if ((x = DEATHS.matcher(m)).matches()) {
            current.deaths = Integer.parseInt(x.group(1));
        } else if ((x = SECRETS.matcher(m)).matches()) {
            current.secrets = Integer.parseInt(x.group(1));
            finish();
        }
    }

    private static void start() {
        Run r = new Run();
        r.at = System.currentTimeMillis();
        r.date = LocalDate.now().toString();
        DungeonFloor f = DungeonManager.getCurrentFloor();
        r.floor = f == null || f == DungeonFloor.UNKNOWN ? "E" : f.name();
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) r.party.put(p.getName(), p.getDungeonClass());
        ComponentGrid g = DungeonManager.getGrid();
        if (g != null) {
            for (DungeonRoom room : g.getAllRooms()) {
                if (room != null && room.getName() != null && !r.rooms.contains(room.getName())) r.rooms.add(room.getName());
            }
        }
        current = r;
        currentAt = r.at;
    }

    private static void tick() {
        if (current != null && (!DungeonManager.isInDungeon() || System.currentTimeMillis() - currentAt > 20000)) {
            if (current.seconds > 0) finish();
            else current = null;
        }
        if (!DungeonManager.isInDungeon()) saved = false;
    }

    private static void finish() {
        Run r = current;
        current = null;
        if (r == null || saved) return;
        saved = true;
        List<Run> list = load();
        list.add(r);
        while (list.size() > MAX_RUNS) list.remove(0);
        save();
    }

    private static int parseTime(String s) {
        int total = 0;
        Matcher m = TIME_PART.matcher(s);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            total += switch (m.group(2)) { case "h" -> v * 3600; case "m" -> v * 60; default -> v; };
        }
        return total;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("runs.json");
    }

    private static List<Run> load() {
        if (runs != null) return runs;
        runs = new ArrayList<>();
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                List<Run> in = GSON.fromJson(r, new TypeToken<List<Run>>() {}.getType());
                if (in != null) for (Run run : in) if (run != null) runs.add(run);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("run logger: could not read runs.json", e);
            }
        }
        return runs;
    }

    private static void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                GSON.toJson(runs, w);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("run logger: could not write runs.json", e);
        }
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("runs")
                .executes(c -> stats(c.getSource(), null, false))
                .then(ClientCommands.argument("floor", StringArgumentType.word())
                        .suggests((c, b) -> {
                            b.suggest("E");
                            for (DungeonFloor f : DungeonFloor.values()) if (f != DungeonFloor.UNKNOWN) b.suggest(f.name());
                            return b.buildFuture();
                        })
                        .executes(c -> stats(c.getSource(), StringArgumentType.getString(c, "floor"), false))
                        .then(ClientCommands.literal("today")
                                .executes(c -> stats(c.getSource(), StringArgumentType.getString(c, "floor"), false)))
                        .then(ClientCommands.literal("all")
                                .executes(c -> stats(c.getSource(), StringArgumentType.getString(c, "floor"), true))));
    }

    private static void say(FabricClientCommandSource src, String s) {
        src.sendFeedback(Component.literal(s));
    }

    private static int stats(FabricClientCommandSource src, String floor, boolean all) {
        String today = LocalDate.now().toString();
        String fl = floor == null ? null : floor.toUpperCase(Locale.ROOT);
        List<Run> list = new ArrayList<>();
        for (Run r : load()) {
            if (!all && !today.equals(r.date)) continue;
            if (fl != null && !fl.equals(r.floor)) continue;
            list.add(r);
        }
        String scope = (fl == null ? "all floors" : fl) + (all ? ", all time" : ", today");
        if (list.isEmpty()) {
            say(src, "§b[TeslaMaps] §7No logged runs (" + scope + ")" + (cfg().runLogger ? "" : " §8- Run Logger is off"));
            return 1;
        }
        int sPlus = 0, s = 0, other = 0, secrets = 0, secretRuns = 0, deaths = 0, fastest = Integer.MAX_VALUE, timed = 0;
        long totalTime = 0;
        for (Run r : list) {
            if ("S+".equals(r.rank)) sPlus++;
            else if ("S".equals(r.rank)) s++;
            else other++;
            if (r.secrets >= 0) { secrets += r.secrets; secretRuns++; }
            deaths += r.deaths;
            if (r.seconds > 0) { timed++; totalTime += r.seconds; fastest = Math.min(fastest, r.seconds); }
        }
        int n = list.size();
        say(src, "§b[TeslaMaps] §fRuns §7(" + scope + ")§f: §a" + n);
        say(src, " §6S+ " + sPlus + " §7(" + pct(sPlus, n) + ") §8| §eS " + s + " §8| §5Other " + other);
        if (secretRuns > 0) say(src, " §7Secrets/run §a" + String.format(Locale.ROOT, "%.2f", secrets / (double) secretRuns)
                + " §8| §7Deaths/run §c" + String.format(Locale.ROOT, "%.2f", deaths / (double) n));
        if (timed > 0) {
            say(src, " §dFastest " + fmt(fastest) + " §8| §6Avg " + fmt((int) (totalTime / timed)) + " §8| §eTotal " + fmt((int) totalTime));
            if (n > 1) {
                long span = list.get(n - 1).at - list.get(0).at + (totalTime / timed) * 1000L;
                if (span > 0) say(src, " §7Runs/h §a" + String.format(Locale.ROOT, "%.1f", n * 3600000.0 / span));
            }
        }
        return 1;
    }

    private static String pct(int a, int n) {
        return String.format(Locale.ROOT, "%.0f%%", a * 100.0 / n);
    }

    private static String fmt(int sec) {
        if (sec >= 3600) return String.format(Locale.ROOT, "%dh %02dm %02ds", sec / 3600, sec / 60 % 60, sec % 60);
        return String.format(Locale.ROOT, "%dm %02ds", sec / 60, sec % 60);
    }
}
