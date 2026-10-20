package com.teslamaps.adm.misc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.TeslaRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// Ported from ADM's misc/AgroSphere.kt: F7 P1/P2 aggro boundary sphere around Maxor/Storm
public final class AgroSphere {
    private static final int LAT_BANDS = 24;
    private static final int LON_BANDS = 48;
    private static final Pattern STORM_LIGHTNING = Pattern.compile("^\\[BOSS] Storm: (?:ENERGY HEED MY CALL!|THUNDER LET ME BE YOUR CATALYST!)$");
    private static final Pattern MAXOR = Pattern.compile("^\\[BOSS] Maxor: ");
    private static final Pattern STORM_START = Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern STORM_END = Pattern.compile("^\\[BOSS] Storm: I should have known that I stood no chance\\.$");
    private static final Pattern GOLDOR_START = Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");

    private static WitherBoss boss;
    private static List<Player> players = List.of();
    /** 1 = P1 (Maxor), 2 = P2 (Storm), 0 = none / after Storm. */
    private static int activePhase;
    private static boolean sawBossLine, leftPhase, stormLightning;

    private AgroSphere() {}

    static void register() {
        AdmCore.onWorldLoad(() -> { activePhase = 0; sawBossLine = false; leftPhase = false; stormLightning = false; boss = null; players = List.of(); });
        Feature.of("AdmAgroSphere").section(Adm.SECTION).when(() -> TeslaMapsConfig.get().admAgroSphere)
                .serverChat(AgroSphere::onChat)
                .tick(AgroSphere::tick)
                .worldCtx(w -> render(w.pose(), w.cam()))
                .config(Adm.SECTION, b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Agro Sphere");
                    b.toggle("Agro Sphere", () -> c.admAgroSphere, v -> c.admAgroSphere = v)
                            .desc("F7 only: in P1/P2, a see-through sphere around Maxor/Storm through the closest player: the aggro boundary. If you're closest, it shows your margin to the 2nd closest.");
                    b.color("  Sphere Color", () -> c.admSphereColor, v -> c.admSphereColor = v)
                            .desc("Sphere colour when someone else has aggro.");
                    b.color("  Sphere Color (Your Aggro)", () -> c.admAggroColor, v -> c.admAggroColor = v)
                            .desc("Sphere colour when you are the closest and it shows the 2nd closest.");
                });
    }

    private static void onChat(String raw) {
        String m = AdmMisc.strip(raw);
        if (STORM_START.matcher(m).matches()) { activePhase = 2; sawBossLine = true; leftPhase = false; stormLightning = false; }
        else if (STORM_LIGHTNING.matcher(m).matches()) stormLightning = true;
        else if (STORM_END.matcher(m).matches() || GOLDOR_START.matcher(m).matches()) { activePhase = 0; sawBossLine = true; }
        else if (MAXOR.matcher(m).find() && !sawBossLine) { activePhase = 1; sawBossLine = true; leftPhase = false; }
    }

    /** Odin's getF7Phase: the phase by your height, 0 outside F7's boss. */
    static int heightPhase() {
        Player p = Minecraft.getInstance().player;
        if (p == null || DungeonManager.getCurrentFloor() == null || DungeonManager.getCurrentFloor().getLevel() != 7
                || !DungeonManager.isInBossArea()) return 0;
        double y = p.getY();
        if (y > 210) return 1;
        if (y > 155) return 2;
        if (y > 100) return 3;
        if (y > 45) return 4;
        return 5;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (DungeonManager.getCurrentFloor() != DungeonFloor.F7) { boss = null; players = List.of(); return; }
        int here = heightPhase();
        if (sawBossLine && activePhase != 0 && here != activePhase) leftPhase = true;
        int phase = leftPhase ? 0 : sawBossLine ? activePhase : here;
        if (phase == 2 && sawBossLine && !stormLightning) phase = 0;
        String name = phase == 1 ? "Maxor" : phase == 2 ? "Storm" : null;
        Player me = mc.player;
        if (name == null || me == null || mc.level == null) { boss = null; players = List.of(); return; }

        WitherBoss named = null, any = null;
        double namedD = Double.MAX_VALUE, anyD = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof WitherBoss w) || !w.isAlive() || !Witherborn.isBoss(w)) continue;
            double d = w.distanceToSqr(me);
            if (d < anyD) { anyD = d; any = w; }
            if (w.getName().getString().toLowerCase().contains(name.toLowerCase()) && d < namedD) { namedD = d; named = w; }
        }
        boss = named != null ? named : any;
        if (boss == null) { players = List.of(); return; }

        List<Player> list = new ArrayList<>();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (!dp.isAlive()) continue;
            for (Player p : mc.level.players()) {
                if (p.getName().getString().equals(dp.getName())) { list.add(p); break; }
            }
        }
        players = list;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        WitherBoss b = boss;
        Minecraft mc = Minecraft.getInstance();
        if (b == null || !b.isAlive() || mc.player == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 center = b.getPosition(pt);
        double closest = Double.MAX_VALUE, second = Double.MAX_VALUE;
        boolean closestIsMe = false;
        for (Player p : players) {
            if (!p.isAlive()) continue;
            double d = p.getPosition(pt).distanceTo(center);
            if (d < closest) {
                second = closest;
                closest = d;
                closestIsMe = p == mc.player;
            } else if (d < second) second = d;
        }
        if (closest == Double.MAX_VALUE) return;
        double radius;
        boolean mine;
        if (!closestIsMe) { radius = closest; mine = false; }
        else {
            if (second == Double.MAX_VALUE) return;
            radius = second;
            mine = true;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        drawSphere(pose, cam, center, radius, TeslaMapsConfig.parseColor(mine ? c.admAggroColor : c.admSphereColor));
    }

    private static void drawSphere(PoseStack pose, Vec3 cam, Vec3 center, double radius, int argb) {
        if (radius <= 0.05) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.renderBuffers() == null) return;
        MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, bl = (argb & 0xFF) / 255f;
        float a = ((argb >>> 24) & 0xFF) / 255f;
        pose.pushPose();
        pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
        PoseStack.Pose last = pose.last();
        VertexConsumer buf = src.getBuffer(TeslaRenderLayers.FILLED_DEPTH);
        float rad = (float) radius;
        float[][] q = new float[4][];
        for (int lat = 0; lat < LAT_BANDS; lat++) {
            for (int lon = 0; lon < LON_BANDS; lon++) {
                q[0] = point(rad, lat, lon);
                q[1] = point(rad, lat + 1, lon);
                q[2] = point(rad, lat + 1, lon + 1);
                q[3] = point(rad, lat, lon + 1);
                for (int i = 0; i < 4; i++) buf.addVertex(last, q[i][0], q[i][1], q[i][2]).setColor(r, g, bl, a);
                for (int i = 3; i >= 0; i--) buf.addVertex(last, q[i][0], q[i][1], q[i][2]).setColor(r, g, bl, a);
            }
        }
        src.endBatch(TeslaRenderLayers.FILLED_DEPTH);
        pose.popPose();
    }

    private static float[] point(float r, int lat, int lon) {
        double theta = Math.PI * lat / LAT_BANDS;
        double phi = 2 * Math.PI * lon / LON_BANDS;
        return new float[]{
                (float) (r * Math.sin(theta) * Math.cos(phi)),
                (float) (r * Math.cos(theta)),
                (float) (r * Math.sin(theta) * Math.sin(phi))};
    }
}
