package com.teslamaps.features;

// Ported from SkyHanni's features/misc/customtodos (NEU's custom todos): chat trigger -> timer until ready
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.CustomTodo;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CustomTodos {

    public static final String[] MATCHERS = {"Contains", "Starts With", "Equals", "Regex"};
    private static final String PREFIX = "TM:TODO/";
    private static final String SAMPLE = "§eForge: §a1h 20m";

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int linesWidth;
    private static final Map<String, Pattern> regexCache = new HashMap<>();
    private static final Map<CustomTodo, Boolean> wasReady = new java.util.IdentityHashMap<>();
    private static int ticks;

    private CustomTodos() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("customTodo", "Custom Todos").section("Timers")
                .enabled(() -> cfg().customTodos)
                .size(() -> Math.max(linesWidth, Minecraft.getInstance().font.width(SAMPLE)), () -> Math.max(1, lines.size()) * 10)
                .preview((g, mc, x, y, s) -> Reminders.draw(g, mc, x, y, s, lines.isEmpty() ? List.of(SAMPLE) : lines,
                        lines.isEmpty() ? mc.font.width(SAMPLE) : linesWidth));
        Feature.of("CustomTodos").section("Timers").when(() -> cfg().customTodos)
                .tick(CustomTodos::tick)
                .serverChat(CustomTodos::onChat)
                .hud("customtodo", CustomTodos::render)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Custom Todos");
                    b.toggle("Custom Todos", () -> c.customTodos, v -> c.customTodos = v)
                            .desc("Your own timers: a chat line you pick starts a countdown until the todo is ready again (/tmap gui)");
                    b.button("  Edit Todos", () -> Minecraft.getInstance().setScreen(
                            new com.teslamaps.screen.CustomTodoScreen(b.screen())));
                    b.toggle("  Sound When Ready", () -> c.customTodoSound, v -> c.customTodoSound = v);
                    b.toggle("  Only Show Ready", () -> c.customTodoOnlyReady, v -> c.customTodoOnlyReady = v);
                });
    }

    private static String profile() {
        String k = SbProfile.key();
        return k == null || k.isEmpty() ? "default" : k;
    }

    public static long seconds(String time) {
        long ms = Reminders.parseDuration(time == null ? "" : time.trim());
        return ms < 0 ? -1 : ms / 1000;
    }

    private static boolean matches(CustomTodo t, String line) {
        String trig = t.trigger;
        if (trig == null || trig.isBlank()) return false;
        return switch (t.matcher) {
            case "Starts With" -> line.startsWith(trig);
            case "Equals" -> line.equals(trig);
            case "Regex" -> {
                Pattern p = regexCache.get(trig);
                if (p == null) {
                    try { p = Pattern.compile(trig); } catch (PatternSyntaxException e) { p = Pattern.compile("(?!)"); }
                    regexCache.put(trig, p);
                }
                yield p.matcher(line).find();
            }
            default -> line.contains(trig);
        };
    }

    private static void onChat(String text) {
        String line = StringUtil.stripTrim(text);
        boolean changed = false;
        for (CustomTodo t : cfg().customTodoList) {
            if (!t.enabled || !matches(t, line)) continue;
            long s = seconds(t.time);
            if (s < 0) continue;
            t.readyAt.put(profile(), System.currentTimeMillis() + s * 1000);
            wasReady.put(t, false);
            ExecutionLog.log("Todo: " + t.label);
            changed = true;
        }
        if (changed) TeslaMapsConfig.save();
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        String prof = profile();
        List<String> out = new ArrayList<>();
        int w = 0;
        boolean ping = false;
        Minecraft mc = Minecraft.getInstance();
        for (CustomTodo t : c.customTodoList) {
            if (!t.enabled || t.label.isBlank()) continue;
            Long at = t.readyAt.get(prof);
            boolean ready = at == null || at <= now;
            Boolean was = wasReady.put(t, ready);
            if (ready && was != null && !was && at != null) {
                ping = true;
                mc.player.sendSystemMessage(Component.literal("§e[Todo] §a" + t.label + " §7is ready"));
            }
            if (c.customTodoOnlyReady && !ready) continue;
            String s = "§e" + t.label + ": " + (ready ? "§aReady" : "§b" + Reminders.format(at - now));
            out.add(s);
            w = Math.max(w, mc.font.width(s));
        }
        if (ping && c.customTodoSound) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        lines = out;
        linesWidth = w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Reminders.draw(g, mc, hud.x(), hud.y(), hud.scale(), l, linesWidth);
    }

    public static void markDone(CustomTodo t) {
        long s = seconds(t.time);
        if (s >= 0) t.readyAt.put(profile(), System.currentTimeMillis() + s * 1000);
        wasReady.put(t, false);
    }

    public static void markReady(CustomTodo t) {
        t.readyAt.remove(profile());
        wasReady.remove(t);
    }

    public static String export(List<CustomTodo> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CustomTodo t : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("label", t.label);
            m.put("trigger", t.trigger);
            m.put("matcher", t.matcher);
            m.put("time", t.time);
            out.add(m);
        }
        return PREFIX + Base64.getEncoder().encodeToString(new Gson().toJson(out).getBytes(StandardCharsets.UTF_8));
    }

    /** Parsed todos from an export string, or null. */
    public static List<CustomTodo> parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (!s.startsWith(PREFIX)) return null;
        try {
            String json = new String(Base64.getDecoder().decode(s.substring(PREFIX.length())), StandardCharsets.UTF_8);
            List<CustomTodo> l = new Gson().fromJson(json, new TypeToken<List<CustomTodo>>() {}.getType());
            if (l == null) return null;
            l.removeIf(java.util.Objects::isNull);
            for (CustomTodo t : l) {
                t.readyAt = new HashMap<>();
                t.enabled = true;
                if (t.label == null) t.label = "";
                if (t.trigger == null) t.trigger = "";
                if (t.time == null) t.time = "1h";
                if (t.matcher == null || !List.of(MATCHERS).contains(t.matcher)) t.matcher = "Contains";
            }
            return l;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
