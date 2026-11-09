package com.teslamaps.features;

// After SkyHanni's GardenBpsTracker.kt, GardenUptimeManager.kt and GardenUptimeCommand.kt
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GardenUptime {

    public static final class Day {
        public long crop, pest, visitor, blocks;
        long total() { return crop + pest + visitor; }
    }

    private static final int CROP = 0, PEST = 1, VISITOR = 2;
    private static final int LINE = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("garden_uptime.json");

    private static Map<String, Day> days;
    private static final Day session = new Day();
    private static long lastActivity;
    private static long lastSave;
    private static boolean dirty;
    private static int ticks;
    private static ClientLevel lastLevel;
    private static Feature feature;
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;

    private GardenUptime() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("gardenUptimeHud", "Crop Break Tracker").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().gardenUptime)
                .size(() -> Math.max(width, 110) + 4, () -> 4 * LINE + 2)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of("§6Crop Break Tracker", "§7Blocks Broken: §e12,345",
                        "§7Blocks/Second: §e19.40", "§7Uptime: §e1h 12m §7(today 3h 5m)"), 150));
        feature = Feature.of("GardenUptime").section("Garden").when(() -> cfg().gardenUptime)
                .tick(GardenUptime::tick)
                .serverChat(GardenUptime::onChat)
                .hudHiddenInInventory("gardenuptime", GardenUptime::render)
                .config("Garden", b -> {
                    b.label("Garden Uptime");
                    b.toggle("Crop Break Tracker", () -> cfg().gardenUptime, v -> cfg().gardenUptime = v)
                            .desc("Blocks broken, blocks per second and farming time per session and per day. Crop, pest and visitor time count, AFK time does not. /tmap gardenuptime [days]")
                            .tags("bps", "uptime", "afk", "blocks");
                    b.slider("  AFK Timeout (sec)", 5f, 300f, () -> (float) cfg().gardenUptimeAfkSeconds,
                            v -> cfg().gardenUptimeAfkSeconds = Math.round(v), 0);
                    b.button("  Reset Session", GardenUptime::resetSession);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("gardenuptime")
                        .executes(c -> { print(7); return 1; })
                        .then(ClientCommands.argument("days", IntegerArgumentType.integer(1, 30))
                                .executes(c -> { print(IntegerArgumentType.getInteger(c, "days")); return 1; })))));
    }

    /** From FarmSpeed, one crop actually broken. */
    public static void onCropBreak() {
        if (feature == null || !cfg().gardenUptime || !feature.active()) return;
        activity(CROP);
        session.blocks++;
        today().blocks++;
    }

    private static void activity(int mode) {
        long now = System.currentTimeMillis();
        long gap = now - lastActivity;
        if (lastActivity > 0 && gap > 0 && gap <= cfg().gardenUptimeAfkSeconds * 1000L) {
            add(session, mode, gap);
            add(today(), mode, gap);
        }
        lastActivity = now;
        dirty = true;
    }

    private static void add(Day d, int mode, long ms) {
        switch (mode) {
            case PEST -> d.pest += ms;
            case VISITOR -> d.visitor += ms;
            default -> d.crop += ms;
        }
    }

    private static Map<String, Day> days() {
        if (days == null) {
            days = new HashMap<>();
            try {
                if (Files.exists(PATH)) {
                    Map<String, Day> read = GSON.fromJson(Files.readString(PATH), new TypeToken<Map<String, Day>>() {}.getType());
                    if (read != null) days = read;
                }
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.LOGGER.error("Failed to load garden uptime", e);
            }
        }
        return days;
    }

    private static Day today() {
        return days().computeIfAbsent(LocalDate.now().toString(), k -> new Day());
    }

    private static void save() {
        dirty = false;
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(days()));
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.error("Failed to save garden uptime", e);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (dirty && now - lastSave > 60_000L) {
            lastSave = now;
            save();
        }
        if (mc.level != lastLevel || !SkyblockUtils.isInGarden()) {
            lastLevel = mc.level;
            lastActivity = 0;
            if (!SkyblockUtils.isInGarden()) {
                if (dirty) save();
                lines = List.of();
                return;
            }
        }
        if (++ticks % 10 != 0) return;
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs && cs.getMenu().slots.size() > 29
                && StringUtil.stripTrim(cs.getMenu().slots.get(29).getItem().getHoverName().getString()).equals("Accept Offer")) {
            activity(VISITOR);
        }
        long up = session.total();
        double bps = session.crop > 1000 ? session.blocks / (session.crop / 1000.0) : 0;
        List<String> out = new ArrayList<>(4);
        out.add("§6Crop Break Tracker");
        out.add("§7Blocks Broken: §e" + String.format(Locale.ROOT, "%,d", session.blocks));
        if (bps > 0) out.add("§7Blocks/Second: §e" + String.format(Locale.ROOT, "%.2f", bps));
        out.add("§7Uptime: §e" + time(up) + " §7(today " + time(today().total()) + ")");
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        width = w;
        lines = out;
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInGarden()) return;
        String text = StringUtil.stripTrim(raw);
        if (text.startsWith("You received ") && text.contains(" for killing a")) activity(PEST);
    }

    private static void resetSession() {
        session.crop = session.pest = session.visitor = session.blocks = 0;
        lastActivity = 0;
    }

    private static void print(int n) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            Map<String, Day> all = days();
            LocalDate date = LocalDate.now();
            long total = 0;
            StringBuilder sb = new StringBuilder("§b[TeslaMaps] §eGarden uptime, last §a" + n + " §edays:");
            for (int i = 0; i < n; i++) {
                LocalDate day = date.minusDays(i);
                Day d = all.get(day.toString());
                long up = d == null ? 0 : d.total();
                total += up;
                sb.append("\n  §e").append(i == 0 ? "Today" : day.toString()).append(": §b").append(time(up));
                if (d != null && up > 0) {
                    sb.append(" §7(crops ").append(time(d.crop)).append(", pests ").append(time(d.pest))
                            .append(", visitors ").append(time(d.visitor)).append(", ")
                            .append(String.format(Locale.ROOT, "%,d", d.blocks)).append(" blocks)");
                }
            }
            sb.append("\n§bTotal: §e").append(time(total)).append("  §bAverage: §e").append(time(total / n));
            mc.player.sendSystemMessage(Component.literal(sb.toString()));
        });
    }

    private static String time(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return (s % 60) + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || lines.isEmpty() || mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
