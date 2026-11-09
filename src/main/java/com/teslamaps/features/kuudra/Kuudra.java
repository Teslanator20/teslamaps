package com.teslamaps.features.kuudra;

// Phases: IQ's KuudraPhase enum + Odin/Shizo KuudraUtils (entity scan, tier from the scoreboard)
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kuudra run state: area, tier, phase with timestamps, the boss entity and the supply giants. */
public final class Kuudra {

    public enum Phase {
        NONE("None"), SUPPLIES("Supplies"), BUILD("Build"), EATEN("Eaten"), STUN("Stun"), DPS("DPS"),
        SKIP("Skip"), BOSS("Boss"), COMPLETED("Completed");

        public final String label;
        Phase(String label) { this.label = label; }

        public boolean running() { return this != NONE && this != COMPLETED; }
        public boolean combat() { return this == STUN || this == DPS || this == SKIP || this == BOSS; }
    }

    public static final String FISH_UP = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!";
    public static final String SUPPLIES_DONE = "[NPC] Elle: OMG! Great work collecting my supplies!";
    public static final String BALLISTA_READY = "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!";
    public static final String POW = "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!";
    public static final String LAIR = "[NPC] Elle: What just happened!? Is this Kuudra's real lair?";
    public static final String GOOD_JOB = "[NPC] Elle: Good job everyone.";
    public static final String HEAD_OVER = "[NPC] Elle: Head over to the main platform";
    public static final String NOT_AGAIN = "[NPC] Elle: Not again!";

    private static final Pattern TIER = Pattern.compile("Kuudra's Hollow \\(T(\\d)\\)");
    private static final Pattern EATEN_BY = Pattern.compile("^(.{1,16}) has been eaten by Kuudra!$");

    /** (previous, current) on every phase change, main thread. */
    public static final List<BiConsumer<Phase, Phase>> LISTENERS = new ArrayList<>();

    private static boolean inKuudra;
    private static int tier;
    private static Phase phase = Phase.NONE;
    private static boolean failed;
    private static final long[] START_MS = new long[Phase.values().length];
    private static final long[] START_TICK = new long[Phase.values().length];
    private static long startPings;
    private static MagmaCube boss;
    private static final List<Giant> GIANTS = new ArrayList<>();
    private static Object lastLevel;
    private static int ticks;

    private Kuudra() {}

    public static void register() {
        FeatureRegistry.category("Kuudra", "Skyblock", true);
        Feature.of("KuudraRun").tick(Kuudra::tick).serverChat(Kuudra::onChat);
    }

    public static boolean inKuudra() { return inKuudra; }
    public static int tier() { return tier; }
    public static Phase phase() { return phase; }
    public static boolean failed() { return failed; }
    public static MagmaCube boss() { return boss; }
    public static List<Giant> giants() { return GIANTS; }

    /** Wall time the phase started, 0 = not reached this run. */
    public static long startMs(Phase p) { return START_MS[p.ordinal()]; }
    public static long startTick(Phase p) { return START_TICK[p.ordinal()]; }
    public static long runStartMs() { return START_MS[Phase.SUPPLIES.ordinal()]; }
    /** Server tick pings seen when the run started. */
    public static long startPings() { return startPings; }

    public static long phaseElapsedMs() {
        long s = START_MS[phase.ordinal()];
        return s == 0 ? 0 : System.currentTimeMillis() - s;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (ticks++ % 20 == 0) {
            boolean area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT).contains("kuudra");
            boolean board = false;
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                String s = StringUtil.strip(line);
                if (s == null || !s.contains("Kuudra's Hollow")) continue;
                board = true;
                Matcher m = TIER.matcher(s);
                if (m.find()) tier = m.group(1).charAt(0) - '0';
                break;
            }
            boolean now = area || board;
            if (inKuudra && !now) reset();
            inKuudra = now;
        }
        if (!inKuudra || ticks % 10 != 0 || mc.level == null) return;
        GIANTS.clear();
        MagmaCube found = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof MagmaCube cube) {
                if (cube.getSize() == 30 && cube.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0) found = cube;
            } else if (e instanceof Giant g && g.getMainHandItem().getHoverName().getString().endsWith("Head")) {
                GIANTS.add(g);
            }
        }
        if (found != null || boss == null || boss.isRemoved()) boss = found;
    }

    private static void onChat(String raw) {
        if (!inKuudra) return;
        String s = StringUtil.stripTrim(raw);
        if (s.equals(FISH_UP)) {
            java.util.Arrays.fill(START_MS, 0);
            java.util.Arrays.fill(START_TICK, 0);
            failed = false;
            startPings = ServerTickTimer.pings();
            set(Phase.SUPPLIES);
        } else if (s.equals(SUPPLIES_DONE)) {
            set(Phase.BUILD);
        } else if (s.equals(BALLISTA_READY)) {
            set(tier == 5 ? Phase.EATEN : Phase.DPS);
        } else if (phase == Phase.EATEN && !s.startsWith("Elle ") && EATEN_BY.matcher(s).matches()) {
            set(Phase.STUN);
        } else if ((phase == Phase.STUN || phase == Phase.EATEN) && s.endsWith("destroyed one of Kuudra's pods!")) {
            set(Phase.DPS);
        } else if (s.equals(POW)) {
            set(Phase.SKIP);
        } else if (s.equals(LAIR)) {
            set(Phase.BOSS);
        } else if (phase.running() && (s.equals("KUUDRA DOWN!") || s.startsWith(GOOD_JOB))) {
            set(Phase.COMPLETED);
        } else if (phase.running() && s.equals("DEFEAT")) {
            failed = true;
            set(Phase.COMPLETED);
        }
    }

    private static void set(Phase p) {
        if (p == phase) return;
        Phase prev = phase;
        phase = p;
        START_MS[p.ordinal()] = System.currentTimeMillis();
        START_TICK[p.ordinal()] = ServerTickTimer.now();
        for (BiConsumer<Phase, Phase> l : LISTENERS) {
            try {
                l.accept(prev, p);
            } catch (RuntimeException ex) {
                com.teslamaps.TeslaMaps.LOGGER.warn("kuudra phase listener failed", ex);
            }
        }
    }

    private static void reset() {
        if (phase != Phase.NONE) set(Phase.NONE);
        java.util.Arrays.fill(START_MS, 0);
        java.util.Arrays.fill(START_TICK, 0);
        inKuudra = false;
        tier = 0;
        failed = false;
        boss = null;
        GIANTS.clear();
    }
}
