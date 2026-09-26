package com.teslamaps.features.qol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.ProfiledFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-profile state of the reminders and fairy souls, plus the account-wide upgrade. */
public final class QolStore {

    public static class Data {
        public Map<String, Long> clockReady = new HashMap<>();
        public Set<String> clockWarned = new HashSet<>();
        public String profileUpgrade;
        public long profileUpgradeEnd;
        public long cityNext;
        public Map<String, Long> cakes = new HashMap<>();
        public Map<String, Set<String>> fairyFound = new HashMap<>();
        public Map<String, Long> dailyTimers = new HashMap<>();
        public Set<String> enigmaFound = new HashSet<>();
    }

    public static class Account {
        public String accountUpgrade;
        public long accountUpgradeEnd;
        public double bazaarLimitCoins;
        public String bazaarLimitDay;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProfiledFile FILE = new ProfiledFile("qol_state.json", null);
    private static final Path ACCOUNT = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("qol_account.json");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(d|h|m|s|days?|hours?|minutes?|seconds?)\\b");

    private static Data data;
    private static Path dataPath;
    private static Account account;

    private QolStore() {}

    public static Data get() {
        if (data == null || FILE.stale()) {
            dataPath = FILE.path();
            data = read(dataPath, Data.class);
            if (data == null) data = new Data();
            if (data.clockReady == null) data.clockReady = new HashMap<>();
            if (data.clockWarned == null) data.clockWarned = new HashSet<>();
            if (data.cakes == null) data.cakes = new HashMap<>();
            if (data.fairyFound == null) data.fairyFound = new HashMap<>();
            if (data.dailyTimers == null) data.dailyTimers = new HashMap<>();
            if (data.enigmaFound == null) data.enigmaFound = new HashSet<>();
        }
        return data;
    }

    public static void save() {
        if (data != null && dataPath != null) write(dataPath, data);
    }

    public static Account account() {
        if (account == null) {
            account = read(ACCOUNT, Account.class);
            if (account == null) account = new Account();
        }
        return account;
    }

    public static void saveAccount() {
        if (account != null) write(ACCOUNT, account);
    }

    private static <T> T read(Path p, Class<T> type) {
        if (p == null || !Files.isRegularFile(p)) return null;
        try {
            return GSON.fromJson(Files.readString(p), type);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[QolStore] could not read {}: {}", p, e.toString());
            return null;
        }
    }

    private static void write(Path p, Object o) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(o));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[QolStore] could not write {}: {}", p, e.toString());
        }
    }

    /** "1d 12h", "20 hours", "41 minutes" -> ms; 0 if nothing matched. */
    public static long parseDuration(String s) {
        long ms = 0;
        Matcher m = DURATION.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += n * switch (m.group(2).charAt(0)) {
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                default -> 1_000L;
            };
        }
        return ms;
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000, d = s / 86400, h = s / 3600 % 24, m = s / 60 % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s % 60 + "s";
        return s + "s";
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + msg));
    }

    public static void chatWarp(String msg, String warp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + msg + " §7(click to /warp " + warp + ")")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/warp " + warp))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to warp to " + warp)))));
    }
}
