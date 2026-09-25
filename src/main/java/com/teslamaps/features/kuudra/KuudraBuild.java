package com.teslamaps.features.kuudra;

// Ported from Odin's BuildHelper.kt + KuudraUtils.kt, countdown from IQ's build overlay
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraBuild {

    private KuudraBuild() {}

    private static final Pattern BUILD = Pattern.compile("Building Progress:? ?(\\d+)% \\((\\d+) Players? Helping\\)");
    private static final Pattern PILE = Pattern.compile("PROGRESS: (\\d+)%");
    private static final Pattern TIER = Pattern.compile("Kuudra's Hollow \\(T(\\d)\\)");
    private static final Vec3 BALLISTA = new Vec3(-101.5, 82.0, -105.5);
    private static final String[] SAMPLE = {"§bBuild: §e56%", "§bBuilders: §a3", "§bFreshers: §e1"};
    private static final long COUNTDOWN_MS = 6200;

    public static int phase = 0;
    public static int tier = 0;
    public static int buildPercent = 0;
    public static int builders = 0;

    record Pile(ArmorStand stand, int percent, int color, String label) {}

    private static List<Pile> piles = List.of();
    private static final List<ArmorStand> hidden = new ArrayList<>();
    private static String[] hudLines = null;
    private static String ballistaBuild, ballistaBuilders;
    private static long phase2At = 0;
    private static boolean stunAlerted = false;
    private static Object lastLevel;
    private static int ticks;
    private static HudEditElement hud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean inKuudra() {
        return SkyblockUtils.getArea().contains("Kuudra");
    }

    private static boolean stateNeeded() {
        TeslaMapsConfig c = cfg();
        return c.kuuBuildHelper || c.kuuFreshTools || c.kuuElleHighlight || c.kuuSupplyMessage;
    }

    public static void register() {
        FeatureRegistry.category("Kuudra", "Skyblock", true);
        hud = HudEditElement.of("kuuBuildHud", "Kuudra Build").section("Kuudra")
                .enabled(() -> cfg().kuuBuildHelper && cfg().kuuBuildHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.length * 10 + 4)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("KuudraState").section("Kuudra").when(KuudraBuild::stateNeeded)
                .serverChat(KuudraBuild::onChat)
                .tick(KuudraBuild::tickState);
        Feature.of("KuudraBuild").section("Kuudra").when(() -> cfg().kuuBuildHelper)
                .tick(KuudraBuild::tick)
                .world(KuudraBuild::render)
                .hud("kuudrabuild", KuudraBuild::renderHud)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Build Phase");
                    b.toggle("Build Helper", () -> c.kuuBuildHelper, v -> c.kuuBuildHelper = v)
                            .desc("Ballista build progress, builders and freshers, beams on unfinished piles and a stun call");
                    b.toggle("  Build HUD", () -> c.kuuBuildHud, v -> c.kuuBuildHud = v)
                            .desc("Build %, builders and freshers. Move it in /tmap gui");
                    b.toggle("  Start Countdown", () -> c.kuuBuildCountdown, v -> c.kuuBuildCountdown = v)
                            .desc("\"Starting in x.xxs\" in the HUD while the build phase animation plays");
                    b.toggle("  Pile Beams", () -> c.kuuBuildBeams, v -> c.kuuBuildBeams = v)
                            .desc("Beam and % over every unfinished pile, red to green");
                    b.toggle("  Hide Hypixel Pile Tags", () -> c.kuuBuildHideTags, v -> c.kuuBuildHideTags = v);
                    b.toggle("  Text on Ballista", () -> c.kuuBuildBallistaText, v -> c.kuuBuildBallistaText = v);
                    b.slider("  Stun Alert %", 0, 100, () -> c.kuuBuildStunPercent, v -> c.kuuBuildStunPercent = v, 0)
                            .desc("\"Go to stun\" title at this build % from T3 up, 0 = off");
                });
    }

    private static void onChat(String raw) {
        if (!inKuudra()) return;
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("[NPC] Elle: ")) return;
        switch (t) {
            case "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> {
                phase = 1;
                KuudraSupply.runStart = System.currentTimeMillis();
            }
            case "[NPC] Elle: OMG! Great work collecting my supplies!" -> {
                phase = 2;
                phase2At = System.currentTimeMillis();
            }
            case "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> phase = 3;
            case "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!" -> phase = 4;
            default -> {}
        }
    }

    private static void tickState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (++ticks % 20 != 0 || !inKuudra()) return;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (!line.contains("Kuudra's Hollow")) continue;
            Matcher m = TIER.matcher(ScoreboardUtils.cleanLine(line));
            if (m.find()) tier = m.group(1).charAt(0) - '0';
        }
    }

    private static void reset() {
        phase = 0; tier = 0; buildPercent = 0; builders = 0;
        piles = List.of(); hidden.clear();
        hudLines = null; ballistaBuild = null; ballistaBuilders = null;
        phase2At = 0; stunAlerted = false;
        KuudraSupply.runStart = 0;
        KuudraFresh.reset();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (phase != 2 || !inKuudra()) {
            hudLines = null;
            if (!piles.isEmpty()) piles = List.of();
            restoreTags();
            return;
        }
        if (ticks % 10 == 0) scan(mc, c);
        if (!c.kuuBuildHideTags) restoreTags();

        long since = System.currentTimeMillis() - phase2At;
        List<String> lines = new ArrayList<>(4);
        if (c.kuuBuildCountdown && since < COUNTDOWN_MS) {
            long rem = COUNTDOWN_MS - since;
            double f = rem / (double) COUNTDOWN_MS;
            String col = f > 0.75 ? "§a" : f > 0.5 ? "§e" : f > 0.25 ? "§6" : "§c";
            lines.add("§bStarting in " + col + String.format(Locale.ROOT, "%.2fs", rem / 1000.0));
        }
        lines.add("§bBuild: " + colorBuild(buildPercent) + "%");
        lines.add("§bBuilders: " + colorCount(builders));
        lines.add("§bFreshers: " + colorCount(KuudraFresh.activeCount()));
        hudLines = lines.toArray(new String[0]);
        ballistaBuild = "§bBuild " + colorBuild(buildPercent) + "%";
        ballistaBuilders = "§bBuilders " + colorCount(builders);

        if (!stunAlerted && c.kuuBuildStunPercent > 0 && tier >= 3 && buildPercent >= c.kuuBuildStunPercent) {
            stunAlerted = true;
            mc.gui.setTitle(Component.literal("§3§lGo to stun"));
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
        }
    }

    private static void scan(Minecraft mc, TeslaMapsConfig c) {
        if (mc.level == null) return;
        List<Pile> found = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
            String name = StringUtil.strip(stand.getCustomName().getString());
            if (name.contains("PROGRESS: ")) {
                Matcher m = PILE.matcher(name);
                if (m.find()) {
                    int pct = Integer.parseInt(m.group(1));
                    found.add(new Pile(stand, pct, pileColor(pct), colorBuild(pct) + "%"));
                    if (c.kuuBuildHideTags && stand.isCustomNameVisible()) {
                        stand.setCustomNameVisible(false);
                        hidden.add(stand);
                    }
                }
            } else if (name.contains("Building Progress")) {
                Matcher m = BUILD.matcher(name);
                if (m.find()) {
                    buildPercent = Integer.parseInt(m.group(1));
                    builders = Integer.parseInt(m.group(2));
                }
            }
        }
        piles = found;
    }

    private static void restoreTags() {
        if (hidden.isEmpty()) return;
        for (ArmorStand s : hidden) if (!s.isRemoved()) s.setCustomNameVisible(true);
        hidden.clear();
    }

    private static int pileColor(int pct) {
        float f = Math.max(0, Math.min(100, pct)) / 100f;
        int r = (int) (255 * (1 - f)), g = (int) (255 * f);
        return 0x99000000 | (r << 16) | (g << 8);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (phase != 2) return;
        TeslaMapsConfig c = cfg();
        if (c.kuuBuildBeams) {
            for (Pile p : piles) {
                if (p.stand.isRemoved()) continue;
                ESPRenderer.drawBeaconBeam(pose, p.stand.blockPosition(), p.color, cam);
                ESPRenderer.drawText(pose, p.label, p.stand.position().add(0, 1.0, 0), 1.5f, cam);
            }
        }
        String b1 = ballistaBuild, b2 = ballistaBuilders;
        if (c.kuuBuildBallistaText && b1 != null) {
            ESPRenderer.drawText(pose, b1, BALLISTA, 3f, cam, false);
            ESPRenderer.drawText(pose, b2, BALLISTA.add(0, -1, 0), 3f, cam, false);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        String[] lines = hudLines;
        if (lines == null || hud == null || !cfg().kuuBuildHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
    }

    static int width(String[] lines) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        return w + 6;
    }

    static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String[] lines) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(lines), lines.length * 10 + 4);
        for (int i = 0; i < lines.length; i++) g.text(mc.font, lines[i], 3, 3 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    static String colorBuild(int v) {
        return (v >= 75 ? "§a" : v >= 50 ? "§e" : v >= 25 ? "§6" : "§c") + v;
    }

    private static String colorCount(int v) {
        return (v >= 3 ? "§a" : v >= 2 ? "§e" : "§c") + v;
    }
}
