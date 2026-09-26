package com.teslamaps.features;

// Ported from SkyHanni's TrevorFeatures / TrevorSolver / TalbotCircles, pelt nametags from Skyblocker's MushroomDesertGlowAdder
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Trevor the Trapper: pelt animal highlight, area waypoint, Talbot circles, cooldown. */
public final class TrevorTrapper {

    private record Area(String name, Vec3 pos) {}
    private record Circle(Vec3 center, double radius) {}

    private static final Area[] AREAS = {
            new Area("Oasis", new Vec3(126, 77, -456)),
            new Area("Mushroom Gorge", new Vec3(300, 80, -509)),
            new Area("Overgrown Mushroom Cave", new Vec3(242, 60, -389)),
            new Area("Desert Settlement", new Vec3(184, 86, -384)),
            new Area("Glowing Mushroom Cave", new Vec3(199, 50, -512)),
            new Area("Desert Mountain", new Vec3(255, 148, -518)),
    };

    private static final Pattern QUEST = Pattern.compile("\\[NPC] Trevor: You can find your (\\w+) animal near the (.+)\\.");
    private static final Pattern TALBOT = Pattern.compile("The target is around (\\d+) blocks (above|below), at a (\\d+) degrees angle!");
    private static final Pattern PELT = Pattern.compile("(Trackable|Untrackable|Undetected|Endangered|Elusive) (Cow|Pig|Sheep|Rabbit|Chicken|Horse)");
    private static final String SAMPLE = "§3Trapper Cooldown: 12 seconds left";

    private static long nextReady;
    private static boolean readyPending;
    private static boolean questActive;
    private static boolean tabSeen;
    private static Area area;
    private static double minH, maxH, avgH;
    private static final List<Circle> circles = new ArrayList<>();
    private static LivingEntity target;
    private static String targetName;
    private static ArmorStand trevor;
    private static String cooldownLine;
    private static String trevorLabel;
    private static boolean keyDown;
    private static long lastWarp;
    private static int ticks;
    private static Object lastLevel;
    private static HudEditElement hud;

    private TrevorTrapper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean onIsland() {
        return SkyblockUtils.isInArea("The Farming Islands");
    }

    public static void register() {
        hud = HudEditElement.of("trevorCooldownHud", "Trapper Cooldown").section("Garden")
                .enabled(() -> cfg().trevorHelper && cfg().trevorCooldownHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 4f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("TrevorTrapper").section("Garden").when(() -> cfg().trevorHelper)
                .tick(TrevorTrapper::tick)
                .serverChat(TrevorTrapper::onChat)
                .world(TrevorTrapper::render)
                .hudHiddenInInventory("trevorcooldown", TrevorTrapper::renderHud)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Trevor the Trapper");
                    b.toggle("Trevor the Trapper Helper", () -> c.trevorHelper, v -> c.trevorHelper = v)
                            .desc("The Farming Islands: highlights your pelt animal, shows its area, Talbot's Theodolite circles and Trevor's cooldown")
                            .tags("trevor", "trapper", "pelt", "talbot", "mushroom desert");
                    b.espStyle("Animal", () -> c.trevorMobEsp);
                    b.toggle("  Area Waypoint", () -> c.trevorAreaWaypoint, v -> c.trevorAreaWaypoint = v)
                            .desc("Beam at the area Trevor names, height narrowed by Talbot's Theodolite");
                    b.toggle("  Talbot Circles", () -> c.trevorTalbotCircles, v -> c.trevorTalbotCircles = v);
                    b.toggle("  Mob Died Title", () -> c.trevorMobDiedTitle, v -> c.trevorMobDiedTitle = v);
                    b.toggle("  Trapper Ready Title", () -> c.trevorReadyTitle, v -> c.trevorReadyTitle = v);
                    b.toggle("  Cooldown Above Trevor", () -> c.trevorCooldownLabel, v -> c.trevorCooldownLabel = v);
                    b.toggle("  Cooldown HUD", () -> c.trevorCooldownHud, v -> c.trevorCooldownHud = v)
                            .desc("Position in /tmap gui");
                    b.keybind("  Warp To Trapper Key", () -> c.trevorWarpKey, v -> c.trevorWarpKey = v)
                            .desc("Sends /warp trapper when pressed");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!onIsland()) {
            keyDown = false;
            cooldownLine = null;
            return;
        }
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();

        boolean down = mc.screen == null && KeyNames.isDown(c.trevorWarpKey);
        if (down && !keyDown && now - lastWarp > 3000 && mc.getConnection() != null) {
            lastWarp = now;
            mc.getConnection().sendCommand("warp trapper");
        }
        keyDown = down;

        if (readyPending && now >= nextReady && !questActive) {
            readyPending = false;
            if (c.trevorReadyTitle) title("§2Trapper Ready");
        }
        String state;
        if (questActive) state = "§cActive Quest";
        else if (now < nextReady) {
            long s = (nextReady - now + 999) / 1000;
            state = "§3" + s + (s == 1 ? " second left" : " seconds left");
        } else state = "§2Ready";
        trevorLabel = state;
        cooldownLine = (questActive ? "§c" : now < nextReady ? "§3" : "§2") + "Trapper Cooldown: "
                + (questActive ? "Active Quest" : now < nextReady ? StringUtil.strip(state) : "Trapper Ready");

        if (target != null) {
            if (!target.isAlive() || target.isRemoved()) target = null;
            else c.trevorMobEsp.tickEntity(target);
        }
        if (ticks++ % 10 != 0) return;
        readTab();
        trevor = null;
        LivingEntity found = null;
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            String n = StringUtil.stripTrim(as.getCustomName().getString());
            if (n.equals("Trevor")) {
                trevor = as;
                continue;
            }
            if (found != null) continue;
            Matcher m = PELT.matcher(n);
            if (!m.find()) continue;
            LivingEntity mob = NametagMobs.mobForTag(as);
            if (mob != null) {
                found = mob;
                targetName = m.group(1) + " " + m.group(2);
            }
        }
        if (found != null && target == null && questActive) title("§2Saw " + targetName + "!");
        target = found;
    }

    private static void readTab() {
        boolean active = false;
        Area a = null;
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            if (l.startsWith("Time Left: ")) active = true;
            String zone = l.startsWith("Location: ") ? l.substring(10).trim() : l;
            for (Area ar : AREAS) if (ar.name().equals(zone)) a = ar;
        }
        if (active) tabSeen = true;
        if (!tabSeen) return;
        questActive = active;
        area = active ? (a != null ? a : area) : null;
    }

    private static void onChat(String text) {
        if (!onIsland()) return;
        String s = StringUtil.stripTrim(text);
        TeslaMapsConfig c = cfg();
        if (s.equals("Return to the Trapper soon to get a new animal to hunt!")) {
            resetSolver();
            questActive = false;
            area = null;
            if (c.trevorMobDiedTitle) title("§2Mob Died");
            return;
        }
        if (s.equals("You ran out of time and the animal disappeared!")) {
            resetSolver();
            questActive = false;
            area = null;
            return;
        }
        if (s.equals("You are at the exact height!")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) avgH = mc.player.getY();
            return;
        }
        Matcher m = QUEST.matcher(s);
        if (m.matches()) {
            nextReady = System.currentTimeMillis() + 20_000;
            readyPending = true;
            questActive = true;
            resetSolver();
            for (Area ar : AREAS) if (ar.name().equals(m.group(2))) area = ar;
            return;
        }
        m = TALBOT.matcher(s);
        if (m.matches()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            int h = Integer.parseInt(m.group(1));
            boolean above = m.group(2).equals("above");
            int angle = Integer.parseInt(m.group(3));
            Vec3 me = mc.player.position();
            double mobY = above ? me.y + h : me.y - h;
            if (maxH == 0) {
                maxH = mobY + 2.5;
                minH = mobY - 2.5;
            } else if (mobY + 2.5 >= minH && mobY + 2.5 <= maxH) {
                maxH = mobY + 2.5;
            } else if (mobY - 2.5 >= minH && mobY - 2.5 <= maxH) {
                minH = mobY - 2.5;
            } else {
                maxH = mobY + 2.5;
                minH = mobY - 2.5;
            }
            avgH = (minH + maxH) / 2;
            double r = Math.tan(Math.toRadians(90.0 - angle)) * h;
            if (r >= 0 && r <= 500) circles.add(new Circle(new Vec3(me.x, above ? me.y + h : me.y - h, me.z), r));
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !onIsland()) return;
        TeslaMapsConfig c = cfg();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        LivingEntity t = target;
        if (t != null) {
            EspStyle.of(c.trevorMobEsp).drawEntity(pose, t, cam, pt);
            ESPRenderer.drawText(pose, "§a" + targetName, t.getPosition(pt).add(0, t.getBbHeight() + 1.2, 0), 1.2f, cam);
        } else {
            Area a = area;
            if (c.trevorAreaWaypoint && a != null) {
                Vec3 p = avgH != 0 ? new Vec3(a.pos().x, avgH, a.pos().z) : a.pos();
                ESPRenderer.drawBeaconBeam(pose, BlockPos.containing(p), 0xFFFFAA00, cam);
                ESPRenderer.drawText(pose, "§6" + a.name(), p.add(0.5, 1.5, 0.5), 1.5f, cam);
            }
            if (c.trevorTalbotCircles) {
                for (Circle ci : circles) {
                    ESPRenderer.drawCircle(pose, ci.center().x, ci.center().y, ci.center().z, ci.radius(), 0xFFFFA500, 2f, cam);
                }
            }
        }
        ArmorStand tv = trevor;
        String lbl = trevorLabel;
        if (c.trevorCooldownLabel && tv != null && lbl != null && tv.distanceToSqr(mc.player) < 15 * 15) {
            ESPRenderer.drawText(pose, lbl, tv.getPosition(pt).add(0, 0.6, 0), 1f, cam);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = cooldownLine;
        Minecraft mc = Minecraft.getInstance();
        if (l == null || hud == null || !cfg().trevorCooldownHud || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static void title(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 30, 10);
        mc.gui.setTitle(Component.literal(text));
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
    }

    private static void resetSolver() {
        minH = maxH = avgH = 0;
        circles.clear();
        target = null;
    }

    private static void reset() {
        resetSolver();
        questActive = false;
        area = null;
        trevor = null;
        cooldownLine = null;
    }
}
