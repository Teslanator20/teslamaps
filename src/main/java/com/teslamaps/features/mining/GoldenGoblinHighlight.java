package com.teslamaps.features.mining;

// Ported from SkyHanni's GoldenGoblinHighlight.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Ties the "A Golden Goblin has spawned!" line to the goblin that spawned with it and marks that one. */
public final class GoldenGoblinHighlight {

    private static final long TIMEOUT = 10_000;

    private static final Map<Integer, Long> seenAt = new HashMap<>();
    private static long lastChat, lastSpawn;
    private static LivingEntity lastGoblin, target;
    private static int ticks;
    private static Object lastLevel;

    private GoldenGoblinHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GoldenGoblinHighlight").section("Mining").when(() -> cfg().goldenGoblinHighlight)
                .tick(GoldenGoblinHighlight::tick)
                .serverChat(GoldenGoblinHighlight::onChat)
                .world(GoldenGoblinHighlight::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Highlight Your Golden Goblin", () -> c.goldenGoblinHighlight, v -> c.goldenGoblinHighlight = v)
                            .desc("Marks the Golden or Diamond Goblin that spawned for you (matched to the spawn message within 10 s). Turn on Tracer below for a line to it.")
                            .tags("golden goblin", "diamond goblin", "goblin", "highlight", "esp", "line");
                    b.espStyle("Goblin", () -> c.goldenGoblinEsp);
                });
    }

    private static boolean isGoblinName(String n) {
        return n.contains("Golden Goblin") || n.contains("Diamond Goblin");
    }

    private static void onChat(String text) {
        if (!Mining.onMiningIsland()) return;
        String t = StringUtil.strip(text).trim();
        if (!t.equals("A Golden Goblin has spawned!") && !t.equals("A Diamond Goblin has spawned!")) return;
        lastChat = System.currentTimeMillis();
        handle();
    }

    private static void handle() {
        long now = System.currentTimeMillis();
        if (now - lastChat > TIMEOUT || now - lastSpawn > TIMEOUT || lastGoblin == null) return;
        lastChat = lastSpawn = 0;
        target = lastGoblin;
        lastGoblin = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seenAt.clear();
            target = lastGoblin = null;
        }
        if (target != null && (!target.isAlive() || target.isRemoved())) target = null;
        if (!Mining.onMiningIsland()) return;
        if (++ticks % 5 == 0) scan(mc);
        if (target != null) EspStyle.of(cfg().goldenGoblinEsp).tickEntity(target);
    }

    private static void scan(Minecraft mc) {
        Set<Integer> alive = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) continue;
            LivingEntity body;
            if (e instanceof ArmorStand) {
                if (!e.hasCustomName() || !isGoblinName(StringUtil.strip(e.getCustomName().getString()))) continue;
                body = bodyBelow(mc, e);
                if (body == null) continue;
            } else {
                if (!isGoblinName(StringUtil.strip(le.getName().getString()))) continue;
                body = le;
            }
            int id = body.getId();
            if (!alive.add(id)) continue;
            if (!seenAt.containsKey(id)) {
                seenAt.put(id, now);
                lastGoblin = body;
                lastSpawn = now;
                handle();
            }
        }
        seenAt.keySet().retainAll(alive);
    }

    private static LivingEntity bodyBelow(Minecraft mc, Entity stand) {
        LivingEntity best = null;
        double bestD = 9;
        for (Entity o : mc.level.getEntities(stand, stand.getBoundingBox().inflate(1.0, 3.0, 1.0))) {
            if (!(o instanceof LivingEntity le) || o instanceof ArmorStand || o == mc.player) continue;
            double d = o.distanceToSqr(stand);
            if (d < bestD) { bestD = d; best = le; }
        }
        return best;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        LivingEntity t = target;
        if (t == null || !t.isAlive() || t.isRemoved()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        EspStyle.of(cfg().goldenGoblinEsp).drawEntity(pose, t, cam, pt);
    }
}
