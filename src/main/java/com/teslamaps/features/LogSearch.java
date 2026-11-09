package com.teslamaps.features;

// Ported from Devonian's LogSearch
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.LogSearchScreen;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPInputStream;

public final class LogSearch {

    public record Result(String text, int line, File file) {}

    public static final int MAX_RESULTS = 5000;
    private static final Pattern LOG_NAME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-\\d+\\.log\\.gz$");
    private static final Pattern SLASHED = Pattern.compile("^/(.+)/([a-z]*)$");
    private static final String CHAT = "[CHAT] ";

    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final AtomicInteger FOUND = new AtomicInteger();
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static ExecutorService pool;

    private LogSearch() {}

    /** Null = ok; otherwise the error text. Results land in {@code out}. */
    public static String search(String query, boolean regex, boolean ignoreCase, ConcurrentLinkedQueue<Result> out) {
        int gen = GENERATION.incrementAndGet();
        FOUND.set(0);
        if (query == null || query.isBlank()) return "Empty search";
        Predicate<String> filter;
        if (regex) {
            try {
                Pattern p = Pattern.compile(query, ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
                filter = s -> p.matcher(s).find();
            } catch (PatternSyntaxException e) {
                return "Bad regex: " + e.getDescription();
            }
        } else if (ignoreCase) {
            String q = query.toLowerCase(Locale.ROOT);
            filter = s -> s.toLowerCase(Locale.ROOT).contains(q);
        } else {
            filter = s -> s.contains(query);
        }

        File dir = new File(Minecraft.getInstance().gameDirectory, "logs");
        File[] files = dir.listFiles(f -> f.isFile() && (f.getName().equals("latest.log") || LOG_NAME.matcher(f.getName()).matches()));
        if (files == null || files.length == 0) return "No log files found";
        // newest first
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        if (pool == null) {
            pool = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "TeslaMaps-LogSearch");
                t.setDaemon(true);
                return t;
            });
        }
        PENDING.set(files.length);
        for (File f : files) pool.submit(() -> {
            try {
                scan(f, filter, out, gen);
            } finally {
                PENDING.decrementAndGet();
            }
        });
        return null;
    }

    public static boolean running() {
        return PENDING.get() > 0;
    }

    public static void cancel() {
        GENERATION.incrementAndGet();
    }

    private static void scan(File f, Predicate<String> filter, ConcurrentLinkedQueue<Result> out, int gen) {
        try (InputStream in = open(f);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if ((n & 1023) == 0 && GENERATION.get() != gen) return;
                int i = line.indexOf(CHAT);
                if (i < 0 || i > 80) continue;
                String text = StringUtil.strip(line.substring(i + CHAT.length()));
                if (!filter.test(text)) continue;
                if (GENERATION.get() != gen || FOUND.incrementAndGet() > MAX_RESULTS) return;
                out.add(new Result(text, n, f));
            }
        } catch (IOException ignored) {
        }
    }

    private static InputStream open(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        return f.getName().endsWith(".gz") ? new GZIPInputStream(in) : in;
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("logsearch")
                .executes(ctx -> open(ctx.getSource(), ""))
                .then(ClientCommands.argument("criteria", StringArgumentType.greedyString())
                        .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "criteria"))));
    }

    private static int open(FabricClientCommandSource src, String criteria) {
        if (!TeslaMapsConfig.get().logSearch) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §7Log Search is off (Chat settings)"));
            return 0;
        }
        String q = criteria.trim();
        boolean regex = false, ignoreCase = true;
        var m = SLASHED.matcher(q);
        if (m.matches()) {
            regex = true;
            q = m.group(1);
            ignoreCase = m.group(2).isEmpty() || m.group(2).contains("i");
        }
        String query = q;
        boolean rx = regex, ic = ignoreCase;
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.setScreen(new LogSearchScreen(query, rx, ic)));
        return 1;
    }
}
