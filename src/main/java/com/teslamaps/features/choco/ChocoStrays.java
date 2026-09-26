package com.teslamaps.features.choco;

// After SkyHanni's stray/CFStrayWarning, CFStrayTimer, CFStrayTracker and Skyblocker's getStrayRabbitHighlight.
// Rarity of an uncaught stray comes from skull textures learned in Hoppity's Collection and from caught strays.
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.properties.Property;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChocoStrays {

    public static final String[] LEVELS = {"All", "Uncommon+", "Rare+", "Epic+", "Legendary+", "Golden Only", "Off"};
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "GOLDEN"};
    private static final String[] RARITY_COLORS = {"§f", "§a", "§9", "§5", "§6", "§d", "§b", "§6§l"};
    private static final ChatFormatting[] FORMATS = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA};
    private static final int GOLDEN = 7;
    private static final Pattern COLLECTION = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Hoppity's Collection");
    private static final Pattern RARITY_LINE = Pattern.compile("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)(?: RABBIT)?$");
    private static final Pattern STRAY_CHOC = Pattern.compile("You caught a stray .*?\\+([\\d,]+) Chocolate");
    private static final Pattern STRAY_COLOR = Pattern.compile("You caught a stray §([0-9a-f])");
    private static final String MARKER = "You caught a stray ";
    private static final long TIMER_MS = 30_000;

    private static final Gson GSON = new Gson();
    private static Map<String, Integer> textures;
    private static boolean texturesDirty;

    private static final Map<Integer, String> caughtSlots = new HashMap<>();
    private static final Map<String, Integer> session = new LinkedHashMap<>();
    private static long sessionChocolate;
    private static List<Integer> active = List.of();
    private static boolean warn, golden, flash;
    private static int ticks, dingTick;
    private static Object lastScreen;
    private static long blockedAt;
    private static String blockedText = "";

    // stray timer
    private static boolean timerPending;
    private static long timerLeft = TIMER_MS, timerLast;
    private static long lastDing;

    private static HudEditElement timerHud, trackerHud;
    private static List<String> trackerLines = List.of();
    private static List<String> timerLines = List.of();
    private static int timerWidth;
    private static int trackerWidth;
    private static final List<String> TIMER_SAMPLE = List.of("§eStray Timer", "§b12.34s");
    private static final List<String> TRACKER_SAMPLE = List.of("§6§lStray Tracker", "§fCommon §e3 §aUncommon §e2", "§9Rare §e1 §6§lGolden §e1",
            "§6Chocolate: §f12,345,678");

    private ChocoStrays() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        timerHud = HudEditElement.of("cfStrayTimer", "Stray Timer").section(ChocoData.CATEGORY)
                .enabled(() -> cfg().cfStrayTimer)
                .size(() -> TextHud.width(TIMER_SAMPLE), () -> TextHud.height(2))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, TIMER_SAMPLE, TextHud.width(TIMER_SAMPLE)));
        trackerHud = HudEditElement.of("cfStrayTracker", "Stray Tracker").section(ChocoData.CATEGORY)
                .enabled(() -> cfg().cfStrayTracker)
                .size(() -> trackerLines.isEmpty() ? TextHud.width(TRACKER_SAMPLE) : trackerWidth,
                        () -> TextHud.height(trackerLines.isEmpty() ? TRACKER_SAMPLE.size() : trackerLines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, trackerLines.isEmpty() ? TRACKER_SAMPLE : trackerLines,
                        trackerLines.isEmpty() ? TextHud.width(TRACKER_SAMPLE) : trackerWidth));
        Feature.of("ChocoStrays").section(ChocoData.CATEGORY)
                .when(() -> cfg().cfStrayWarning || cfg().cfStrayTimer || cfg().cfStrayTracker)
                .tickAlways(ChocoStrays::tick)
                .serverChat(ChocoStrays::onChat)
                .hudForeground("cfstrays", ChocoStrays::renderHud)
                .config(ChocoData.CATEGORY, b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Stray Rabbits");
                    b.toggle("Stray Rabbit Warning", () -> c.cfStrayWarning, v -> c.cfStrayWarning = v)
                            .desc("Highlights CLICK ME! and Golden strays in the factory. Rarity is learned from Hoppity's Collection and caught strays, unknown ones only count for 'All'.");
                    b.dropdown("  Warn From", LEVELS, () -> c.cfStrayLevel, v -> c.cfStrayLevel = v);
                    b.toggle("  Repeating Ding", () -> c.cfStraySound, v -> c.cfStraySound = v);
                    b.toggle("  Screen Flash", () -> c.cfStrayFlash, v -> c.cfStrayFlash = v);
                    b.dropdown("    Flash From", LEVELS, () -> c.cfStrayFlashLevel, v -> c.cfStrayFlashLevel = v);
                    b.toggle("  Block Closing While A Stray Is Up", () -> c.cfStrayBlockClose, v -> c.cfStrayBlockClose = v)
                            .desc("Esc, the inventory key and the Close button do nothing while a stray waits. Hold Shift to close anyway.");
                    b.toggle("Stray Timer", () -> c.cfStrayTimer, v -> c.cfStrayTimer = v)
                            .desc("30 s countdown in the factory after you found an egg, restarts when you close the menu. Position in /tmap gui");
                    b.slider("  Ding Below (s)", 0, 15, () -> (float) c.cfStrayTimerDing, v -> c.cfStrayTimerDing = Math.round(v));
                    b.toggle("  Block Closing While Running", () -> c.cfStrayTimerBlockClose, v -> c.cfStrayTimerBlockClose = v);
                    b.toggle("Stray Tracker", () -> c.cfStrayTracker, v -> c.cfStrayTracker = v)
                            .desc("Caught strays per rarity and their chocolate, this session and in total. Shown in the factory, position in /tmap gui");
                    b.button("  Reset Stray Tracker", () -> {
                        ChocoData.State s = ChocoData.state();
                        s.strays.clear();
                        s.strayChocolate = 0;
                        session.clear();
                        sessionChocolate = 0;
                        ChocoData.changed();
                        buildTracker();
                    });
                });
    }

    private static boolean passes(String level, int rarity) {
        return switch (level) {
            case "All" -> true;
            case "Uncommon+" -> rarity >= 1;
            case "Rare+" -> rarity >= 2;
            case "Epic+" -> rarity >= 3;
            case "Legendary+" -> rarity >= 4;
            case "Golden Only" -> rarity == GOLDEN;
            default -> false;
        };
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (mc.level == null) { timerPending = false; }
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            caughtSlots.clear();
            active = List.of();
            warn = golden = flash = false;
        }
        tickTimer();
        if (warn && cfg().cfStraySound && ChocoData.inFactory()) {
            dingTick = (dingTick + 1) % (golden ? 3 : 5);
            if (dingTick == 0) mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    golden ? SoundEvents.NOTE_BLOCK_HARP.value() : SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f));
        }
        if (ticks % 5 != 0) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (ticks % 100 == 0 && texturesDirty) saveTextures();
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (COLLECTION.matcher(title).matches()) {
            if (ticks % 20 == 0) learnCollection(cs);
            return;
        }
        if (!ChocoData.inFactory()) return;
        scanFactory(cs);
        if (cfg().cfStrayTracker && (trackerLines.isEmpty() || ticks % 20 == 0)) buildTracker();
    }

    private static void scanFactory(AbstractContainerScreen<?> cs) {
        TeslaMapsConfig c = cfg();
        List<Integer> act = new ArrayList<>();
        boolean w = false, g = false, f = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.index > 26) continue;
            ItemStack st = slot.getItem();
            int i = slot.index;
            if (st.isEmpty()) { caughtSlots.remove(i); continue; }
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (name.endsWith("CAUGHT!")) {
                if (name.equals(caughtSlots.get(i))) continue;
                caughtSlots.put(i, name);
                onCaught(st);
                continue;
            }
            caughtSlots.remove(i);
            boolean isGolden = name.startsWith("Golden Rabbit");
            if (!isGolden && !name.equals("CLICK ME!")) continue;
            int rarity = isGolden ? GOLDEN : rarityOf(st);
            act.add(i);
            if (passes(c.cfStrayLevel, rarity)) {
                w = true;
                if (isGolden) g = true;
            }
            if (c.cfStrayFlash && passes(c.cfStrayFlashLevel, rarity)) f = true;
        }
        active = act;
        warn = c.cfStrayWarning && w;
        golden = g;
        flash = c.cfStrayWarning && f;
    }

    private static void onCaught(ItemStack st) {
        timerPending = false;
        int rarity = -1;
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        List<String> lore = ItemUtil.loreLines(st);
        String joined = String.join(" ", lore);
        if (name.startsWith("Golden Rabbit") || joined.contains("stray Golden Rabbit")) rarity = GOLDEN;
        else {
            ItemLore il = ItemUtil.lore(st);
            if (il != null) {
                for (Component line : il.lines()) {
                    if (!line.getString().contains(MARKER)) continue;
                    rarity = colorAfter(line);
                    if (rarity < 0) {
                        Matcher m = STRAY_COLOR.matcher(line.getString());
                        if (m.find()) rarity = legacy(m.group(1).charAt(0));
                    }
                    break;
                }
            }
            if (rarity >= 0) learn(st, rarity);
        }
        long choc = 0;
        Matcher m = STRAY_CHOC.matcher(joined);
        if (m.find()) choc = (long) ChocoData.num(m.group(1));
        if (!cfg().cfStrayTracker) return;
        String key = rarity < 0 ? "UNKNOWN" : RARITIES[rarity];
        ChocoData.State s = ChocoData.state();
        s.strays.merge(key, 1, Integer::sum);
        s.strayChocolate += choc;
        session.merge(key, 1, Integer::sum);
        sessionChocolate += choc;
        ChocoData.changed();
        buildTracker();
    }

    private static int colorAfter(Component line) {
        StringBuilder sb = new StringBuilder();
        int[] res = {-1};
        line.visit((style, str) -> {
            if (sb.indexOf(MARKER) >= 0 && !str.isBlank()) {
                res[0] = rarityOf(style.getColor());
                return Optional.of(Boolean.TRUE);
            }
            sb.append(str);
            return Optional.empty();
        }, Style.EMPTY);
        return res[0];
    }

    private static int rarityOf(TextColor color) {
        if (color == null) return -1;
        for (int i = 0; i < FORMATS.length; i++) {
            Integer v = FORMATS[i].getColor();
            if (v != null && v == color.getValue()) return i;
        }
        return -1;
    }

    private static int legacy(char c) {
        return switch (c) {
            case 'f' -> 0; case 'a' -> 1; case '9' -> 2; case '5' -> 3; case '6' -> 4; case 'd' -> 5; case 'b' -> 6;
            default -> -1;
        };
    }

    private static void buildTracker() {
        ChocoData.State s = ChocoData.state();
        List<String> out = new ArrayList<>();
        out.add("§6§lStray Tracker");
        out.add("§7Session: " + counts(session) + " §6+" + ChocoData.commas(sessionChocolate));
        out.add("§7Total: " + counts(s.strays));
        out.add("§6Chocolate: §f" + ChocoData.commas(s.strayChocolate));
        trackerLines = out;
        trackerWidth = TextHud.width(out);
    }

    private static String counts(Map<String, Integer> m) {
        if (m.isEmpty()) return "§8none";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RARITIES.length; i++) {
            Integer n = m.get(RARITIES[i]);
            if (n == null) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(RARITY_COLORS[i]).append(RARITIES[i].charAt(0)).append("§r§f").append(n);
        }
        Integer u = m.get("UNKNOWN");
        if (u != null) sb.append(sb.isEmpty() ? "" : " ").append("§7?").append(u);
        return sb.toString();
    }

    // stray timer

    private static void onChat(String raw) {
        if (!cfg().cfStrayTimer) return;
        String t = StringUtil.stripTrim(raw);
        if ((t.startsWith("HOPPITY'S HUNT You found a ") && t.contains(" Egg")) || t.startsWith("[NPC] Hoppity: Simply exquisite")) {
            timerPending = true;
            timerLeft = TIMER_MS;
            timerLast = 0;
        }
    }

    private static void tickTimer() {
        if (!timerPending || !cfg().cfStrayTimer) return;
        long now = System.currentTimeMillis();
        if (!ChocoData.inFactory()) {
            timerLeft = TIMER_MS;
            timerLast = 0;
            return;
        }
        if (timerLast != 0) timerLeft -= now - timerLast;
        timerLast = now;
        if (timerLeft <= 0) {
            timerLeft = 0;
            timerPending = false;
            return;
        }
        timerLines = List.of("§eStray Timer", "§b" + String.format(java.util.Locale.ROOT, "%.2f", timerLeft / 1000.0) + "s");
        timerWidth = TextHud.width(timerLines);
        if (timerLeft < cfg().cfStrayTimerDing * 1000L && now - lastDing > 1000) {
            lastDing = now;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2f, 1f));
        }
    }

    private static boolean protecting() {
        if (!ChocoData.inFactory()) return false;
        TeslaMapsConfig c = cfg();
        if (c.cfStrayWarning && c.cfStrayBlockClose && warn) { blockedText = "§cStray Rabbit Prevented Close"; return true; }
        if (c.cfStrayTimer && c.cfStrayTimerBlockClose && timerPending && timerLeft > 0) { blockedText = "§cStray Timer Prevented Close"; return true; }
        return false;
    }

    /** Esc / inventory key in the factory while a stray is up. */
    public static boolean blockKey(net.minecraft.client.input.KeyEvent event) {
        if (!cfg().section(ChocoData.CATEGORY)) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean close = event.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event);
        if (!close || mc.hasShiftDown() || !protecting()) return false;
        blocked();
        return true;
    }

    public static boolean blockClick(Slot slot) {
        if (slot == null || slot.container instanceof Inventory || !cfg().section(ChocoData.CATEGORY)) return false;
        String name = StringUtil.stripTrim(slot.getItem().getHoverName().getString());
        if (!name.equals("Close") && !name.equals("Go Back")) return false;
        if (Minecraft.getInstance().hasShiftDown() || !protecting()) return false;
        blocked();
        return true;
    }

    private static void blocked() {
        blockedAt = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1f, 1f));
    }

    static void render(GuiGraphicsExtractor ctx) {
        if (!ChocoData.inFactory() || !cfg().section(ChocoData.CATEGORY)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (cfg().cfStrayWarning && !active.isEmpty()) {
            List<Slot> slots = cs.getMenu().slots;
            for (int i : active) ChocoFactory.mark(ctx, acc, slots, i, 0x55FF55);
        }
        if (flash) {
            double t = System.currentTimeMillis() / 1000.0;
            int a = (int) Math.max(0, Math.min(255, (2 + Math.sin(t * 3)) * 255 / 8));
            ctx.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (a << 24) | 0xFF5555);
        }
        if (System.currentTimeMillis() - blockedAt < 3000) {
            String sub = "§7Hold §eShift §7to bypass";
            int w = mc.getWindow().getGuiScaledWidth();
            int y = Math.max(2, acc.getY() - 22);
            ctx.centeredText(mc.font, blockedText, w / 2, y, 0xFFFFFFFF);
            ctx.centeredText(mc.font, sub, w / 2, y + 10, 0xFFFFFFFF);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!ChocoData.inFactory()) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (c.cfStrayTimer && timerPending && timerHud != null && !timerLines.isEmpty()) {
            TextHud.draw(g, mc, timerHud.x(), timerHud.y(), timerHud.scale(), timerLines, timerWidth);
        }
        if (c.cfStrayTracker && !trackerLines.isEmpty() && trackerHud != null) {
            TextHud.draw(g, mc, trackerHud.x(), trackerHud.y(), trackerHud.scale(), trackerLines, trackerWidth);
        }
    }

    // texture -> rarity

    private static String textureId(ItemStack st) {
        ResolvableProfile rp = st.get(DataComponents.PROFILE);
        if (rp == null) return null;
        Collection<Property> props = rp.partialProfile().properties().get("textures");
        if (props.isEmpty()) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(props.iterator().next().value()));
            int i = decoded.indexOf("texture/");
            if (i < 0) return null;
            int end = i + 8;
            while (end < decoded.length() && Character.isLetterOrDigit(decoded.charAt(end))) end++;
            return decoded.substring(i + 8, end);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Integer> textures() {
        if (textures == null) textures = loadTextures();
        return textures;
    }

    private static int rarityOf(ItemStack st) {
        String id = textureId(st);
        if (id == null) return -1;
        Integer r = textures().get(id);
        return r == null ? -1 : r;
    }

    private static void learn(ItemStack st, int rarity) {
        String id = textureId(st);
        if (id == null) return;
        Integer old = textures().put(id, rarity);
        if (old == null || old != rarity) texturesDirty = true;
    }

    private static void learnCollection(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty() || st.get(DataComponents.PROFILE) == null) continue;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = RARITY_LINE.matcher(l.trim());
                if (!m.matches()) continue;
                for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(m.group(1))) learn(st, i);
                break;
            }
        }
    }

    private static Path texturePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("rabbit_rarities.json");
    }

    private static Map<String, Integer> loadTextures() {
        Path p = texturePath();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                Map<String, Integer> m = GSON.fromJson(r, new TypeToken<HashMap<String, Integer>>() {}.getType());
                if (m != null) return m;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[ChocoStrays] load failed: {}", e.toString());
            }
        }
        return new HashMap<>();
    }

    private static void saveTextures() {
        texturesDirty = false;
        try {
            Path p = texturePath();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(textures(), w);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[ChocoStrays] save failed: {}", e.toString());
        }
    }
}
