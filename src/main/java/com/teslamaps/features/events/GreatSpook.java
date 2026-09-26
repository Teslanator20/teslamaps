package com.teslamaps.features.events;

// Ported from SkyHanni's TheGreatSpook.kt, quick maths answer idea from Skyblocker's MathTeacherHelper
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.Calculator;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GreatSpook {

    private static final String REPO = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/Events.json";
    private static final Pattern FEAR = Pattern.compile("^\\s*Fear: \\D*([\\d,.]+)");
    private static final Pattern SPEAKING = Pattern.compile("^\\[FEAR] Public Speaking Demon: (?:Speak|Say something interesting) (.+)!$");
    private static final String[] SPEECHES = {
            "I looove TeslaMaps!",
            "Do you know TeslaMaps? A cool mod for SkyBlock!",
            "Today is a good day to kill Spooky monsters.",
    };
    private static final List<String> SAMPLE_TIMER = List.of("§5Next fear in: §b4m 12s", "§5Fear: §d45");
    private static final List<String> SAMPLE_LEFT = List.of("§5Great Spook time left: §b3d 4h");

    private static volatile long start, end;
    private static volatile boolean loading;
    private static long lastTry;
    private static long nextMob;
    private static int fear = -1;
    private static boolean wasCounting;
    private static int speech;
    private static int ticks;
    private static List<String> timerLines = List.of(), leftLines = List.of();
    private static int timerW, leftW;
    private static HudEditElement timerHud, leftHud;

    private GreatSpook() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        timerHud = HudEditElement.of("primalFearTimer", "Primal Fear Timer").section("Events")
                .enabled(() -> cfg().primalFearTimer)
                .size(() -> timerLines.isEmpty() ? TextHud.width(SAMPLE_TIMER) : timerW,
                        () -> TextHud.height(timerLines.isEmpty() ? SAMPLE_TIMER.size() : timerLines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE_TIMER, TextHud.width(SAMPLE_TIMER)));
        leftHud = HudEditElement.of("greatSpookTimeLeft", "Great Spook Time Left").section("Events")
                .enabled(() -> cfg().greatSpookTimeLeft)
                .size(() -> leftLines.isEmpty() ? TextHud.width(SAMPLE_LEFT) : leftW, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE_LEFT, TextHud.width(SAMPLE_LEFT)));
        Feature.of("GreatSpookSolver").section("Events")
                .when(() -> cfg().primalFearMathSolver || cfg().primalFearSpeakingSolver)
                .serverChat(GreatSpook::onSolverChat);
        Feature.of("GreatSpook").section("Events")
                .when(() -> cfg().primalFearTimer || cfg().primalFearPling || cfg().greatSpookTimeLeft)
                .tick(GreatSpook::tick)
                .serverChat(GreatSpook::onChat)
                .hudHiddenInInventory("greatspook", GreatSpook::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("The Great Spook");
                    b.toggle("Quick Maths Solver", () -> c.primalFearMathSolver, v -> c.primalFearMathSolver = v)
                            .desc("Answers the QUICK MATHS! Primal Fear in chat with a clickable [Click to send] that sends /ac <answer>.")
                            .tags("primal", "fear", "spook", "math", "solver");
                    b.toggle("Public Speaking Solver", () -> c.primalFearSpeakingSolver, v -> c.primalFearSpeakingSolver = v)
                            .desc("For the Public Speaking Demon: a clickable chat line that sends a random sentence to /ac.")
                            .tags("primal", "fear", "spook", "speaking", "solver");
                    b.toggle("Primal Fear Timer", () -> c.primalFearTimer, v -> c.primalFearTimer = v)
                            .desc("HUD with the time until the next Primal Fear can spawn (6 min minus 3s per Fear). "
                                    + "Needs the Fear stat in the tab list Stats widget. Move it in /tmap gui.")
                            .tags("primal", "fear", "spook", "cooldown");
                    b.toggle("  Show Fear Stat", () -> c.primalFearShowFear, v -> c.primalFearShowFear = v);
                    b.toggle("Primal Fear Ready Sound", () -> c.primalFearPling, v -> c.primalFearPling = v)
                            .desc("Pling when the Primal Fear cooldown runs out, also with the HUD off.");
                    b.toggle("Great Spook Time Left", () -> c.greatSpookTimeLeft, v -> c.greatSpookTimeLeft = v)
                            .desc("HUD with the real time left until The Great Spook ends (dates from the SkyHanni repo).")
                            .tags("spook", "event", "timer");
                });
    }

    private static boolean active() {
        long now = System.currentTimeMillis();
        return now >= start && now <= end;
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (loading || end != 0 || now - lastTry < 600_000) return;
        lastTry = now;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement json = RepoCache.fetch("Events", REPO, 20);
                JsonObject o = json.getAsJsonObject().getAsJsonObject("great_spook");
                if (o != null) {
                    start = o.get("start_time").getAsLong();
                    end = o.get("end_time").getAsLong();
                }
            } catch (Exception ignored) {
            } finally {
                loading = false;
            }
        });
    }

    private static void onSolverChat(String raw) {
        TeslaMapsConfig c = cfg();
        String t = StringUtil.stripTrim(raw);
        if (c.primalFearMathSolver && t.startsWith("QUICK MATHS! Solve: ")) {
            String expr = t.substring("QUICK MATHS! Solve: ".length());
            Double v = Calculator.eval(expr);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null) return;
                if (v == null) {
                    mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] Failed to solve " + expr));
                    return;
                }
                String answer = Long.toString(Math.round(v));
                send(mc, "§e[TeslaMaps] " + expr + " = §b" + answer + " ", answer);
            });
            return;
        }
        if (c.primalFearSpeakingSolver && t.startsWith("[FEAR] Public Speaking Demon: ") && SPEAKING.matcher(t).matches()) {
            String line = SPEECHES[speech++ % SPEECHES.length];
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) send(mc, "§e[TeslaMaps] Public Speaking: ", line);
            });
        }
    }

    private static void send(Minecraft mc, String prefix, String text) {
        mc.player.sendSystemMessage(Component.literal(prefix).append(Component.literal("§a[Click to send]")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/ac " + text))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7/ac " + text))))));
    }

    private static void onChat(String raw) {
        if (StringUtil.stripTrim(raw).equals("FEAR. A Primal Fear has been summoned!")) {
            nextMob = System.currentTimeMillis() + 6 * 60_000L;
            wasCounting = true;
        }
    }

    private static void tick() {
        load();
        if (++ticks % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!active()) {
            timerLines = List.of();
            leftLines = List.of();
            return;
        }
        fear = -1;
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = FEAR.matcher(StringUtil.strip(line));
            if (!m.find()) continue;
            try { fear = (int) Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
            break;
        }
        long now = System.currentTimeMillis();
        long left = nextMob - Math.max(0, fear) * 3000L - now;
        if (left <= 0 && wasCounting) {
            wasCounting = false;
            if (c.primalFearPling) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        }
        if (c.primalFearTimer) {
            String first = left > 0 ? "§5Next fear in: §b" + time(left, false) : "§5§lPrimal Fear Ready!";
            timerLines = c.primalFearShowFear
                    ? List.of(first, fear >= 0 ? "§5Fear: §d" + fear : "§cFear stat not in tab list")
                    : List.of(first);
            timerW = TextHud.width(timerLines);
        }
        if (c.greatSpookTimeLeft) {
            leftLines = List.of("§5Great Spook time left: §b" + time(end - now, true));
            leftW = TextHud.width(leftLines);
        }
    }

    private static String time(long ms, boolean days) {
        long s = Math.max(0, ms / 1000);
        if (days && s >= 86400) return (s / 86400) + "d " + (s / 3600 % 24) + "h";
        if (s >= 3600) return (s / 3600) + "h " + (s / 60 % 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TeslaMapsConfig c = cfg();
        if (c.primalFearTimer && !timerLines.isEmpty() && timerHud != null)
            TextHud.draw(g, mc, timerHud.x(), timerHud.y(), timerHud.scale(), timerLines, timerW);
        if (c.greatSpookTimeLeft && !leftLines.isEmpty() && leftHud != null)
            TextHud.draw(g, mc, leftHud.x(), leftHud.y(), leftHud.scale(), leftLines, leftW);
    }
}
