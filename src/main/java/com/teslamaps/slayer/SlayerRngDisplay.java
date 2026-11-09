package com.teslamaps.slayer;

// Ported from SkyHanni's SlayerRngMeterDisplay.kt; goal XP read from the RNG Meter menu instead of NEU rngscore
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerRngDisplay {

    public static final class Meter {
        String item = "?";
        long goal = -1;
        long current = -1;
        long gain = -1;
    }

    private static final Pattern STORED = Pattern.compile("^RNG Meter - ([\\d,.kKmM]+) Stored XP$");
    private static final Pattern PROGRESS = Pattern.compile("([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)$");
    private static final List<String> SAMPLE = List.of("§dWarden Heart §7in §e1,234 §7bosses! §8(12.3%)");

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<HashMap<String, Meter>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("slayer_rng.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("slayer_rng.json"));

    private static Map<String, Meter> meters = new HashMap<>();
    private static boolean loaded;
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static Object lastScreen;
    private static int screenTicks;
    private static String hideLine;
    private static long droppedAt;
    private static Feature feature;
    private static HudEditElement hud;

    private SlayerRngDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("slayerRngDisplay", "Slayer RNG Meter").section("Combat")
                .enabled(() -> cfg().slayerRngDisplay)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        feature = Feature.of("SlayerRngDisplay").section("Combat").when(() -> cfg().slayerRngDisplay)
                .tick(SlayerRngDisplay::tick)
                .serverChat(SlayerRngDisplay::onChat)
                .hudHiddenInInventory("slayerrngdisplay", SlayerRngDisplay::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Slayer RNG Meter Display", () -> c.slayerRngDisplay, v -> c.slayerRngDisplay = v)
                            .desc("During a quest: \"<item> in N bosses!\" for the selected RNG Meter drop, and \"RNG Meter dropped at X%\" in chat. "
                                    + "Open the slayer's RNG Meter menu (/rng) once to read the item. Position in /tmap gui")
                            .tags("slayer", "rng", "meter", "bosses");
                    b.toggle("  Warn Without Selected Item", () -> c.slayerRngDisplayWarnEmpty, v -> c.slayerRngDisplayWarnEmpty = v);
                    b.toggle("  Hide RNG Meter Chat Lines", () -> c.slayerRngDisplayHideChat, v -> c.slayerRngDisplayHideChat = v);
                });
    }

    private static String typeForTitle(String name) {
        return switch (name) {
            case "Revenant Horror" -> "REVENANT";
            case "Tarantula Broodfather" -> "TARANTULA";
            case "Sven Packmaster" -> "SVEN";
            case "Voidgloom Seraph" -> "VOIDGLOOM";
            case "Inferno Demonlord" -> "DEMONLORD";
            case "Riftstalker Bloodfiend", "Bloodfiend" -> "VAMPIRE";
            default -> null;
        };
    }

    private static String currentType() {
        String key = SlayerQuest.key();
        return key == null ? null : key.substring(0, key.indexOf('_'));
    }

    private static Meter meter(String type) {
        ensure();
        return meters.computeIfAbsent(type, k -> new Meter());
    }

    /** From ChatFilter: drop the "RNG Meter - x Stored XP" line onChat just marked. */
    public static boolean hide(String stripped) {
        if (hideLine == null || feature == null || !feature.active()) return false;
        boolean hit = stripped.trim().equals(hideLine);
        if (hit) hideLine = null;
        return hit;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("RNG Meter - ")) return;
        Matcher m = STORED.matcher(t);
        String type = currentType();
        if (!m.matches() || type == null) return;
        long now = (long) NumberUtil.parseSuffixed(m.group(1).replace(",", ""));
        Meter mt = meter(type);
        long old = mt.current;
        mt.current = now;
        boolean selected = !mt.item.isEmpty() && !mt.item.equals("?");
        boolean block = cfg().slayerRngDisplayHideChat && selected;
        Minecraft mc = Minecraft.getInstance();
        if (old != -1) {
            if (!selected && cfg().slayerRngDisplayWarnEmpty && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cNo Slayer RNG Meter item selected!"));
                mc.gui.hud.setTimes(0, 40, 5);
                mc.gui.hud.setTitle(Component.literal("§cNo RNG Meter Item!"));
            }
            long diff = now - old;
            if (diff > 0) {
                mt.gain = diff;
            } else {
                mt.current = 0;
                block = false;
                double pct = mt.goal > 0 ? Math.min(1.0, (double) old / mt.goal) * 100 : -1;
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§dRNG Meter §7dropped at §e"
                        + (pct < 0 ? "?" : String.format(java.util.Locale.ROOT, "%.2f%%", pct))
                        + " §7XP (" + fmt(old) + "/" + (mt.goal > 0 ? fmt(mt.goal) : "?") + ")"));
                droppedAt = System.currentTimeMillis();
            }
        }
        hideLine = block ? t : null;
        save();
        rebuild();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs) {
            if (cs != lastScreen) {
                lastScreen = cs;
                screenTicks = 0;
            }
            // Hypixel fills the menu a moment after it opens
            if (++screenTicks == 10 || screenTicks == 30) readMenu(cs);
        } else {
            lastScreen = null;
        }
        if (++ticks % 20 == 0) rebuild();
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.endsWith(" RNG Meter")) return;
        String type = typeForTitle(title.substring(0, title.length() - 10));
        if (type == null) return;
        ItemStack selected = null;
        for (Slot s : cs.getMenu().slots) {
            if (s.container == mc().player.getInventory()) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            for (String l : ItemUtil.loreLines(st)) {
                if (l.trim().equals("SELECTED")) { selected = st; break; }
            }
            if (selected != null) break;
        }
        Meter mt = meter(type);
        if (selected == null) {
            mt.item = "";
            mt.goal = -1;
        } else {
            mt.item = StringUtil.stripTrim(selected.getHoverName().getString());
            boolean next = false;
            for (String l : ItemUtil.loreLines(selected)) {
                String p = l.trim();
                if (next || p.startsWith("Slayer XP:")) {
                    Matcher m = PROGRESS.matcher(p);
                    if (m.find()) {
                        mt.current = (long) NumberUtil.parseSuffixed(m.group(1).replace(",", ""));
                        mt.goal = (long) NumberUtil.parseSuffixed(m.group(2).replace(",", ""));
                        break;
                    }
                }
                next = p.startsWith("Progress:");
            }
        }
        save();
        rebuild();
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static void rebuild() {
        String type = currentType();
        int tier = SlayerQuest.tier();
        if (type == null || tier <= 2) {
            lines = List.of();
            return;
        }
        Meter mt = meter(type);
        String text;
        if (mt.item.equals("?")) text = "§cOpen the RNG Meter menu!";
        else if (mt.item.isEmpty()) text = droppedAt > 0 && System.currentTimeMillis() - droppedAt < 60_000
                ? "§a§lRNG Item dropped!" : "§eNo RNG Item selected!";
        else if (mt.goal <= 0) text = "§d" + mt.item + " §7- §cOpen the RNG Meter menu!";
        else if (mt.current < 0 || mt.gain <= 0) text = "§d" + mt.item + " §7- §ckill 2 bosses to measure";
        else {
            long missing = Math.max(0, mt.goal - mt.current);
            long bosses = Math.max(1, (long) Math.ceil((double) missing / mt.gain));
            double pct = Math.min(100.0, mt.current * 100.0 / mt.goal);
            text = "§d" + mt.item + " §7in §e" + fmt(bosses) + " §7bosses! §8("
                    + String.format(java.util.Locale.ROOT, "%.1f%%", pct) + ")";
        }
        List<String> l = List.of(text);
        lines = l;
        width = TextHud.width(l);
    }

    private static String fmt(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        meters = new HashMap<>();
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                Map<String, Meter> read = GSON.fromJson(Files.readString(p), TYPE);
                if (read != null) {
                    read.values().removeIf(v -> v == null);
                    for (Meter v : read.values()) if (v.item == null) v.item = "?";
                    meters = read;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to load slayer RNG meters: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(meters, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save slayer RNG meters: {}", e.toString());
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
