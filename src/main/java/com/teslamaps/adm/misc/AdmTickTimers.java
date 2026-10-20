package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

// Ported from Odin's TickTimers + ADM's misc/OdinTickTimers.kt (Goldor Count Up)
public final class AdmTickTimers {
    private static final Pattern NECRON = Pattern.compile("^\\[BOSS] Necron: I'm afraid, your journey ends now\\.$");
    private static final Pattern GOLDOR = Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");
    private static final Pattern CORE_OPENING = Pattern.compile("^The Core entrance is opening!$");
    private static final Pattern STORM_END = Pattern.compile("^\\[BOSS] Storm: I should have known that I stood no chance\\.$");
    private static final Pattern STORM_START = Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern STORM_PY = Pattern.compile("^\\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$");
    private static final Pattern MORT = Pattern.compile("^\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.$");
    /** Our tick-timer rows this replaces (TimerTriggers keys). */
    private static final Set<String> OURS = Set.of("goldortick", "stormpad", "necrondrop", "lightning", "purplepad", "secrettick");

    private static final AdmMisc.TickPump pump = new AdmMisc.TickPump();
    private static int necronTime = -1, goldorTickTime = -1, goldorStartTime = -1, padTickTime = -1;
    private static int lightningTickTime = -1, pyTickTime = -1, stormTick = -1, secretsCounter;
    private static boolean pyTriggered;
    private static int serverTicks;
    private static Integer goldorStart;

    private AdmTickTimers() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    /** True: our tick-timer row {@code key} stays off, ADM's Tick Timers draw it. */
    public static boolean overrides(String key) {
        return OURS.contains(key) && Adm.overrides(cfg().admTickTimers);
    }

    static void register() {
        AdmCore.onWorldLoad(AdmTickTimers::reset);
        TeslaMapsConfig c = cfg();
        hud("admTtNecronHud", "ADM Necron Hud", () -> c.admTtNecron,
                () -> necronTime >= 0 ? format(necronTime, 60, "§4Necron dropping in", null) : null, () -> format(35, 60, "§4Necron dropping in", null));
        hud("admTtGoldorHud", "ADM Goldor Hud", () -> c.admTtGoldor, AdmTickTimers::goldorText, () -> goldorTimer(35, 60, "§7Tick:"));
        hud("admTtPadHud", "ADM Storm Pad Hud", () -> c.admTtStormPad,
                () -> padTickTime >= 0 ? format(padTickTime, 20, "§bPad:", null) : null, () -> format(15, 20, "§bPad:", null));
        hud("admTtLightningHud", "ADM Storm Lightning Hud", () -> c.admTtLightning,
                () -> lightningTickTime >= 0 ? format(lightningTickTime, 560, "§bLightning:", null) : null, () -> format(560, 560, "§bLightning:", null));
        hud("admTtPyHud", "ADM Storm PY Hud", () -> c.admTtPy,
                () -> pyTickTime >= 0 ? format(pyTickTime, 95, "§bPY:", null) : null, () -> format(95, 95, "§bPY:", null));
        hud("admTtStormTickHud", "ADM Storm Tick Hud", () -> c.admTtStormTick,
                () -> stormTick >= 0 ? format(stormTick, 620, "§bStorm:", null) : null, () -> format(200, 620, "§bStorm:", "§a"));
        hud("admTtSecretsHud", "ADM Secrets Hud", () -> c.admTtSecrets, AdmTickTimers::secretsText, () -> format(15, 20, "§7Secret:", "§c"));
        Feature.of("AdmTickTimers").section(Adm.SECTION).when(() -> cfg().admTickTimers)
                .tick(AdmTickTimers::tick)
                .serverChat(AdmTickTimers::onChat)
                .config("ADM Timers", b -> {
                    b.label("Tick Timers");
                    b.toggle("Tick Timers", () -> c.admTickTimers, v -> c.admTickTimers = v)
                            .desc("Odin's Tick Timers for Necron, Goldor, Storm and Secrets, with ADM's Goldor Count Up. Replaces our own F7 tick timers while on. Each is its own HUD in /tmap gui.");
                    b.toggle("  Display in Ticks", () -> c.admTtTicks, v -> c.admTtTicks = v)
                            .desc("Display the timers in ticks instead of seconds.");
                    b.toggle("  Display Symbol", () -> c.admTtSymbol, v -> c.admTtSymbol = v)
                            .desc("Displays s or t after the timers.");
                    b.toggle("  Show Prefix", () -> c.admTtPrefix, v -> c.admTtPrefix = v)
                            .desc("Shows the prefix of the timers.");
                    b.toggle("  Necron Hud", () -> c.admTtNecron, v -> c.admTtNecron = v)
                            .desc("Displays a timer for Necron's drop.");
                    b.toggle("  Goldor Hud", () -> c.admTtGoldor, v -> c.admTtGoldor = v)
                            .desc("Displays a timer for Goldor's Core entrance opening.");
                    b.toggle("    Start timer", () -> c.admTtStartTimer, v -> c.admTtStartTimer = v)
                            .desc("Displays a timer counting down until devices/terms are able to be activated/completed.");
                    b.toggle("    Goldor Count Up", () -> c.admTtGoldorCountUp, v -> c.admTtGoldorCountUp = v)
                            .desc("The Goldor Hud's Tick timer counts up from Goldor's first line like a split, past 3 s; the colour still goes green / yellow / red with each tick.");
                    b.toggle("  Storm Pad Hud", () -> c.admTtStormPad, v -> c.admTtStormPad = v)
                            .desc("Displays a timer for Storm's Pad.");
                    b.toggle("  Storm Lightning Hud", () -> c.admTtLightning, v -> c.admTtLightning = v)
                            .desc("Displays a timer for Storm's Lightning.");
                    b.toggle("  Storm PY Hud", () -> c.admTtPy, v -> c.admTtPy = v)
                            .desc("Displays a timer for when to crush storm under the purple pillar.");
                    b.toggle("  Storm Tick Hud", () -> c.admTtStormTick, v -> c.admTtStormTick = v)
                            .desc("Displays a timer for Storm's second phase.");
                    b.toggle("  Secrets Hud", () -> c.admTtSecrets, v -> c.admTtSecrets = v)
                            .desc("Displays a timer for secret spawn ticks.");
                });
    }

    private static void hud(String prefix, String label, BooleanSupplier on, Supplier<String> live, Supplier<String> sample) {
        HudEditElement el = HudEditElement.of(prefix, label).section(Adm.SECTION).ctx(HudEditElement.HudCtx.DUNGEON)
                .enabled(() -> cfg().admTickTimers && on.getAsBoolean())
                .size(() -> Minecraft.getInstance().font.width(sample.get()), () -> 9)
                .scaleRange(0.5f, 5f)
                .preview((g, mc, x, y, s) -> Timers.draw(g, x, y, s, sample.get()));
        Feature.of("AdmTickTimers." + prefix).section(Adm.SECTION).when(() -> cfg().admTickTimers && on.getAsBoolean())
                .hudHiddenInInventory(prefix.toLowerCase(Locale.ROOT), (g, d) -> {
                    if (Minecraft.getInstance().options.hideGui) return;
                    String s = live.get();
                    if (s != null) Timers.draw(g, el.x(), el.y(), el.scale(), s);
                });
    }

    private static String goldorText() {
        if (goldorStartTime >= 0 && cfg().admTtStartTimer) return format(goldorStartTime, 100, "§aStart:", null);
        if (goldorTickTime >= 0) return goldorTimer(goldorTickTime, 60, "§7Tick:");
        return null;
    }

    /** Goldor Count Up: the Tick timer counts up from Goldor's first line, coloured by the time to the next tick. */
    private static String goldorTimer(int time, int max, String prefix) {
        if (!cfg().admTtGoldorCountUp) return format(time, max, prefix, null);
        String colour = time >= max * 0.66f ? "§a" : time >= max * 0.33f ? "§6" : "§c";
        int elapsed = goldorStart == null ? 70 : serverTicks - goldorStart;
        return format(elapsed, max, prefix, colour);
    }

    private static String secretsText() {
        if (DungeonManager.isInBossArea() || !DungeonManager.isInDungeon()) return null;
        int time = 20 - secretsCounter % 20;
        String colour = time < 5 ? "§a" : time < 10 ? "§6" : "§c";
        return format(time, 20, "§7Secret:", colour);
    }

    private static String format(int time, int max, String prefix, String overrideColor) {
        TeslaMapsConfig c = cfg();
        String colour = overrideColor != null ? overrideColor
                : time >= max * 0.66 ? "§a" : time >= max * 0.33 ? "§6" : "§c";
        String t = c.admTtTicks ? time + (c.admTtSymbol ? "t" : "")
                : String.format(Locale.ROOT, "%.2f", time / 20f) + (c.admTtSymbol ? "s" : "");
        return (c.admTtPrefix ? prefix + " " : "") + colour + t;
    }

    private static void onChat(String raw) {
        String v = AdmMisc.strip(raw);
        if (MORT.matcher(v).matches()) secretsCounter = 0;
        else if (NECRON.matcher(v).matches()) necronTime = 60;
        else if (GOLDOR.matcher(v).matches()) { goldorTickTime = 60; goldorStart = serverTicks; }
        else if (CORE_OPENING.matcher(v).matches()) { goldorStartTime = -1; goldorTickTime = -1; }
        else if (STORM_END.matcher(v).matches()) { goldorStartTime = 104; padTickTime = -1; stormTick = -1; }
        else if (STORM_START.matcher(v).matches()) { padTickTime = 20; lightningTickTime = 560; stormTick = 0; }
        else if (!pyTriggered && STORM_PY.matcher(v).matches()) { pyTriggered = true; pyTickTime = 95; }
    }

    private static void tick() {
        int n = pump.poll();
        TeslaMapsConfig c = cfg();
        for (int i = 0; i < n; i++) {
            serverTicks++;
            if (!DungeonManager.isInDungeon()) continue;
            secretsCounter++;
            if (!DungeonManager.isInBossArea()) continue;
            if (goldorTickTime == 0 && goldorStartTime <= 0 && c.admTtGoldor) goldorTickTime = 60;
            if (goldorStartTime >= 0) goldorStartTime--;
            if (goldorTickTime >= 0) goldorTickTime--;
            if (padTickTime == 0 && c.admTtStormPad) padTickTime = 20;
            if (padTickTime >= 0) padTickTime--;
            if (lightningTickTime >= 0) lightningTickTime--;
            if (pyTickTime >= 0) pyTickTime--;
            if (necronTime >= 0) necronTime--;
            if (stormTick >= 0) stormTick++;
        }
    }

    /** The sim stopped (or a practice started): every tick timer off, as if no boss line had come. */
    public static void stopAll() {
        reset();
    }

    private static void reset() {
        goldorStartTime = -1; goldorTickTime = -1; padTickTime = -1; lightningTickTime = -1;
        pyTickTime = -1; pyTriggered = false; necronTime = -1; secretsCounter = 0; stormTick = -1;
        goldorStart = null;
    }
}
