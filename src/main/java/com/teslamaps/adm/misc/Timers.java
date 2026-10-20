package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's misc/Timers.kt: run countdowns in server ticks, each its own HUD
public final class Timers {
    private static final int CAMP_TICKS = 50 * 20;
    private static final int PORTAL_TICKS = 4 * 20;
    private static final int PORTAL_TEXT = 36;
    private static final int EXIT_CORE_TEXT = 16;
    private static final int MAXOR_MOVE = 85;
    private static final int CRYSTALS_BACK = 40;
    private static final int NECRON_DPS = 89;
    private static final int PAD_LEAD = 41;
    private static final int PURPLE_AFTER_LIGHTNING = 109;
    private static final int PAD_TOTAL = 60;
    private static final float DAMAGEABLE = 500f;
    private static final int HIT_DEBOUNCE = 30;
    // ADM's storm/StormCrush.kt
    private static final int CHECK_PERIOD = 20;
    private static final int PILLAR_TOP = 204;
    private static final int PILLAR_FLOOR = 169;
    private static final int PURPLE_COLUMN_X = 97 + 3;
    private static final int PURPLE_COLUMN_Z = 62 + 3;

    private static final String WATCHER = "[BOSS] The Watcher: ";
    private static final String WATCHER_DONE = "[BOSS] The Watcher: You have proven yourself. You may pass.";
    private static final String MAXOR_START = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!";
    private static final String STORM_START = "[BOSS] Storm: Pathetic Maxor, just like expected.";
    private static final String STORM_DEAD = "[BOSS] Storm: I should have known that I stood no chance.";
    private static final Set<String> STORM_LIGHTNING = Set.of("[BOSS] Storm: ENERGY HEED MY CALL!", "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!");
    private static final Set<String> STORM_CRUSHED = Set.of("[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!");
    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE_OPENING = "The Core entrance is opening!";
    private static final String GATE = "The gate has been destroyed!";
    private static final Pattern TASK = Pattern.compile("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$");
    private static final Set<String> NECRON_START = Set.of(
            "[BOSS] Necron: You went further than any human before, congratulations.",
            "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.");

    private static final AdmMisc.TickPump pump = new AdmMisc.TickPump();
    private static int serverTicks;
    private static Integer bossAt;
    private static boolean bloodSeen, portalOpen;
    private static int portalUntil;
    private static boolean coreOpen;
    private static int exitCoreUntil;
    private static Integer maxorMoveAt;
    private static boolean inMaxor;
    private static Integer crystalsAt;
    private static int lastHit = Integer.MIN_VALUE / 2;
    private static float maxorHealth;
    private static Integer stormStart;
    private static boolean stormDead;
    private static Integer lightningAt;
    private static int crushes;
    private static Integer purpleStepAt;
    private static Integer necronDpsAt;
    // section times (ADM takes them from TermInfo)
    private static boolean inP3;
    private static int sectionStart, completed, total = 7;
    private static boolean gateBlown;
    private static String shownTime;
    private static long shownAt;

    private Timers() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    static void register() {
        AdmCore.onWorldLoad(Timers::reset);
        TeslaMapsConfig c = cfg();
        hud("admClearHud", "ADM Clear Countdown", () -> c.admClearCountdown, Timers::clearText, "§a41.3");
        hud("admPortalHud", "ADM Portal Text", () -> c.admPortalText,
                () -> !DungeonManager.isInBossArea() && portalUntil - serverTicks > 0 ? "§d§lPORTAL" : null, "§d§lPORTAL");
        hud("admExitCoreHud", "ADM Exit Core Text", () -> c.admExitCoreText,
                () -> exitCoreUntil - serverTicks > 0 ? "§d§lEXIT CORE" : null, "§d§lEXIT CORE");
        hud("admMaxorHud", "ADM Maxor Move", () -> c.admMaxorMove, () -> timer(left(maxorMoveAt, MAXOR_MOVE), MAXOR_MOVE), "§7Maxor: §a5.00s");
        hud("admCrystalHud", "ADM Crystal Spawn", () -> c.admCrystalSpawn, () -> timer(left(crystalsAt, CRYSTALS_BACK), CRYSTALS_BACK), "§7Crystals: §c1.25s");
        hud("admPurplePadHud", "ADM Purple Pad", () -> c.admPurplePad, () -> timer(padLeft(), PAD_TOTAL), "§7Purple: §c1.50s");
        hud("admSectionHud", "ADM Section Time", () -> c.admSectionTime, Timers::sectionText, "§514.35");
        hud("admNecronHud", "ADM Necron DPS", () -> c.admNecronDps, () -> timer(left(necronDpsAt, NECRON_DPS), NECRON_DPS), "§7Necron: §c1.75s");
        Feature.of("AdmTimers").section(Adm.SECTION).when(() -> cfg().admTimers)
                .tick(Timers::tick)
                .serverChat(Timers::onChat)
                .config("ADM Timers", b -> {
                    b.label("Timers");
                    b.toggle("Timers", () -> c.admTimers, v -> c.admTimers = v)
                            .desc("Countdowns through a run: clear, exit core, Maxor moving, crystals, Purple's pad, terminal section times, and Necron becoming damageable. Each is its own HUD in /tmap gui.");
                    b.toggle("  Clear Countdown", () -> c.admClearCountdown, v -> c.admClearCountdown = v)
                            .desc("From blood opening, counts down 54 s to the boss (50 s camp, 4 s portal): green, yellow under 30, red under 20. Hidden if blood isn't done by 50 s; set to 4 s when the portal spawns.");
                    b.toggle("  Portal Text", () -> c.admPortalText, v -> c.admPortalText = v)
                            .desc("\"PORTAL\" in pink on screen for 1.8 s when the portal spawns.");
                    b.toggle("  Exit Core Text", () -> c.admExitCoreText, v -> c.admExitCoreText = v)
                            .desc("\"EXIT CORE\" in pink on screen for 0.8 s once the core has opened and everyone has leapt in.");
                    b.toggle("  Maxor Move", () -> c.admMaxorMove, v -> c.admMaxorMove = v)
                            .desc("Counts down to Maxor starting to move, 4.25 s after his first line.");
                    b.toggle("  Crystal Spawn", () -> c.admCrystalSpawn, v -> c.admCrystalSpawn = v)
                            .desc("Counts down to the top crystals coming back after a laser hit, 2 s.");
                    b.toggle("  Purple Pad", () -> c.admPurplePad, v -> c.admPurplePad = v)
                            .desc("Storm: counts down to the pad read to be on Purple's pad for (t 599, then t 619 if that one's missed), so he's crushed the moment he gets there. Gone once the pillar comes down.");
                    b.toggle("  Section Time", () -> c.admSectionTime, v -> c.admSectionTime = v)
                            .desc("When a terminal section is done, how long it took, in purple. S1 from Goldor's first line, the rest from the last section's end.");
                    b.slider("    Section Time Seconds", 0.5f, 10f, () -> c.admSectionTimeSeconds, v -> c.admSectionTimeSeconds = Math.round(v * 2f) / 2f, 1)
                            .desc("How long a section's time stays up.");
                    b.toggle("  Necron DPS", () -> c.admNecronDps, v -> c.admNecronDps = v)
                            .desc("Counts down to Necron first taking damage, 4.45 s after his first line (\"You went further than any human before, congratulations.\").");
                });
    }

    private static void hud(String prefix, String label, BooleanSupplier on, Supplier<String> live, String sample) {
        HudEditElement el = HudEditElement.of(prefix, label).section(Adm.SECTION).ctx(HudEditElement.HudCtx.DUNGEON)
                .enabled(() -> cfg().admTimers && on.getAsBoolean())
                .size(() -> Minecraft.getInstance().font.width(sample), () -> 9)
                .scaleRange(0.5f, 8f)
                .preview((g, mc, x, y, s) -> draw(g, x, y, s, sample));
        Feature.of("AdmTimers." + prefix).section(Adm.SECTION).when(() -> cfg().admTimers && on.getAsBoolean())
                .hudHiddenInInventory(prefix.toLowerCase(Locale.ROOT), (g, d) -> {
                    if (Minecraft.getInstance().options.hideGui) return;
                    String s = live.get();
                    if (s != null) draw(g, el.x(), el.y(), el.scale(), s);
                });
    }

    static void draw(GuiGraphicsExtractor g, int x, int y, float scale, String s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(Minecraft.getInstance().font, s, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static String clearText() {
        if (DungeonManager.isInBossArea() || bossAt == null) return null;
        int t = bossAt - serverTicks;
        if (t <= 0) return null;
        double left = t / 20.0;
        String colour = left > 30 ? "§a" : left > 20 ? "§e" : "§c";
        return colour + String.format(Locale.ROOT, "%.1f", left);
    }

    private static String sectionText() {
        if (shownTime == null || System.currentTimeMillis() - shownAt > cfg().admSectionTimeSeconds * 1000) return null;
        return shownTime;
    }

    private static Integer left(Integer at, int total) {
        if (at == null) return null;
        int l = at - serverTicks;
        return l >= 1 && l <= total ? l : null;
    }

    private static Integer checkFrom(int t) {
        if (stormStart == null) return null;
        return stormStart + Math.ceilDiv(t - stormStart, CHECK_PERIOD) * CHECK_PERIOD;
    }

    private static int[] purpleReads() {
        if (lightningAt == null || crushes > 0 || stormDead) return null;
        Integer check = checkFrom(lightningAt + PURPLE_AFTER_LIGHTNING);
        if (check == null) return null;
        int[] reads = {check - PAD_LEAD - CHECK_PERIOD, check - PAD_LEAD};
        if ((purpleStepAt == null ? Integer.MIN_VALUE : purpleStepAt) > reads[0]) return null;
        return reads;
    }

    private static Integer padLeft() {
        int[] reads = purpleReads();
        if (reads == null) return null;
        for (int r : reads) if (r >= serverTicks) return r - serverTicks;
        return null;
    }

    private static String colour(int left, int total) {
        if (left >= total * 0.66f) return "§a";
        if (left >= total * 0.33f) return "§6";
        return "§c";
    }

    private static String timer(Integer left, int total) {
        if (left == null) return null;
        return colour(left, total) + String.format(Locale.ROOT, "%.2fs", left / 20.0);
    }

    private static void onChat(String raw) {
        String message = AdmMisc.strip(raw);
        if (message.equals(WATCHER_DONE)) {
            bloodSeen = true;
            portalOpen = true;
            bossAt = serverTicks + PORTAL_TICKS;
            portalUntil = serverTicks + PORTAL_TEXT;
        } else if (message.startsWith(WATCHER) && !bloodSeen) {
            bloodSeen = true;
            bossAt = serverTicks + CAMP_TICKS + PORTAL_TICKS;
        } else if (message.equals(MAXOR_START)) {
            inMaxor = true;
            maxorMoveAt = serverTicks + MAXOR_MOVE;
            maxorHealth = 0f;
        } else if (message.equals(STORM_START)) {
            inMaxor = false; crystalsAt = null; stormStart = serverTicks - 1; stormDead = false;
            lightningAt = null; crushes = 0; purpleStepAt = null;
        } else if (message.equals(STORM_DEAD)) stormDead = true;
        else if (STORM_LIGHTNING.contains(message)) { if (stormStart != null && lightningAt == null) lightningAt = serverTicks; }
        else if (STORM_CRUSHED.contains(message)) { if (stormStart != null) crushes++; }
        else if (message.equals(GOLDOR_START)) {
            stormStart = null;
            inP3 = true;
            sectionStart = serverTicks;
            completed = 0; total = 7; gateBlown = false;
        } else if (message.equals(CORE_OPENING)) {
            coreOpen = true;
            if (inP3) sectionDone();
            inP3 = false;
        } else if (NECRON_START.contains(message)) {
            if (necronDpsAt == null) necronDpsAt = serverTicks + NECRON_DPS;
        } else if (inP3 && message.equals(GATE)) {
            if (completed > 0 && completed == total) sectionDone();
            else gateBlown = true;
        } else if (inP3) {
            Matcher m = TASK.matcher(message);
            if (!m.matches()) return;
            int cur = Integer.parseInt(m.group(4)), max = Integer.parseInt(m.group(5));
            if ((cur == max && gateBlown) || cur < completed) sectionDone();
            else { completed = cur; total = max; }
        }
    }

    /** A section's time measured elsewhere (P3 Sim), shown as Section Time. */
    public static void showSectionTime(int ticks) {
        shownTime = "§5" + String.format(Locale.ROOT, "%.2f", ticks / 20.0);
        shownAt = System.currentTimeMillis();
    }

    public static void clearSectionTime() {
        shownTime = null;
    }

    private static void sectionDone() {
        shownTime = "§5" + String.format(Locale.ROOT, "%.2f", (serverTicks - sectionStart) / 20.0);
        shownAt = System.currentTimeMillis();
        sectionStart = serverTicks;
        completed = 0; total = 7; gateBlown = false;
    }

    private static void tick() {
        int n = pump.poll();
        for (int i = 0; i < n; i++) {
            serverTicks++;
            if (bossAt != null) {
                if (!portalOpen && bossAt - serverTicks <= PORTAL_TICKS) bossAt = null;
                else if (bossAt - serverTicks <= 0) bossAt = null;
            }
        }
        watchMaxor();
        watchCore();
    }

    private static void watchMaxor() {
        if (!inMaxor) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        WitherBoss maxor = null;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof WitherBoss w && Witherborn.isBoss(w)) { maxor = w; break; }
        }
        if (maxor == null) return;
        float hp = maxor.getHealth();
        if (hp >= DAMAGEABLE && maxorHealth < DAMAGEABLE && serverTicks - lastHit >= HIT_DEBOUNCE) {
            lastHit = serverTicks;
            crystalsAt = serverTicks + CRYSTALS_BACK;
        }
        maxorHealth = hp;
    }

    private static void watchCore() {
        if (!coreOpen) return;
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        Player me = mc.player;
        if (level == null || me == null) return;
        String myName = me.getName().getString();
        if (!AdmCore.isDead(myName) && !inCore(me.getX(), me.getY(), me.getZ())) return;
        for (PlayerTracker.DungeonPlayer mate : PlayerTracker.getPlayers()) {
            if (mate.getName().equals(myName) || AdmCore.isDead(mate.getName())) continue;
            Player p = null;
            for (Player q : level.players()) if (q.getName().getString().equals(mate.getName())) { p = q; break; }
            if (p == null || !inCore(p.getX(), p.getY(), p.getZ())) return;
        }
        coreOpen = false;
        exitCoreUntil = serverTicks + EXIT_CORE_TEXT;
    }

    private static boolean inCore(double x, double y, double z) {
        return x >= 39 && x < 71 && y >= 110 && y < 155.5 && z >= 54 && z < 118;
    }

    /** Level.setBlock on the client: Purple's pillar stepping down goes through moving pistons. */
    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        if (stormStart == null || !state.is(Blocks.MOVING_PISTON) || pos.getY() < PILLAR_FLOOR || pos.getY() > PILLAR_TOP) return;
        if (pos.getX() == PURPLE_COLUMN_X && pos.getZ() == PURPLE_COLUMN_Z) purpleStepAt = serverTicks;
    }

    public static boolean listening() {
        return stormStart != null;
    }

    private static void reset() {
        bossAt = null; bloodSeen = false; portalOpen = false; portalUntil = 0; coreOpen = false; exitCoreUntil = 0;
        maxorMoveAt = null; inMaxor = false; crystalsAt = null; lastHit = Integer.MIN_VALUE / 2; maxorHealth = 0f;
        stormStart = null; stormDead = false; necronDpsAt = null;
        lightningAt = null; crushes = 0; purpleStepAt = null;
        inP3 = false; shownTime = null;
    }
}
