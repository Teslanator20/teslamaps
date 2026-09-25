package com.teslamaps.slayer;

// Ported from SkyHanni's SpiderEggSacHighlighter.kt and SlayerSpiderFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class TarantulaHelper {

    private static final String STUCK = "You need to kill the Broodfather's hatchlings before it can be damaged again!";

    private static List<ArmorStand> sacs = List.of();
    private static Entity lastHit;
    private static Entity stuck;
    private static Vec3 stuckPos;
    private static int ticks;

    private TarantulaHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Spider && cfg().tarantulaHighlights) lastHit = entity;
            return InteractionResult.PASS;
        });
        Feature.of("TarantulaHelper").section("Combat").when(() -> cfg().tarantulaHighlights)
                .tick(TarantulaHelper::tick)
                .world(TarantulaHelper::render)
                .serverChat(TarantulaHelper::onChat)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Tarantula Highlights", () -> c.tarantulaHighlights, v -> c.tarantulaHighlights = v)
                            .desc("Your Tarantula fight: marks Egg Sacs and the Broodfather while hatchlings make it invincible")
                            .tags("tarantula", "spider", "egg sac", "broodfather", "slayer");
                    b.toggle("  Egg Sacs", () -> c.tarantulaEggSacs, v -> c.tarantulaEggSacs = v);
                    b.color("  Egg Sac Color", () -> c.colorTarantulaEggSac, v -> c.colorTarantulaEggSac = v);
                    b.toggle("  Invincible Broodfather (T3+)", () -> c.tarantulaInvincible, v -> c.tarantulaInvincible = v)
                            .desc("After \"kill the Broodfather's hatchlings\", until the boss moves again");
                    b.color("  Invincible Color", () -> c.colorTarantulaInvincible, v -> c.colorTarantulaInvincible = v);
                });
    }

    private static boolean fighting() {
        String b = SlayerQuest.boss();
        return SlayerQuest.bossActive() && b != null && (b.contains("Tarantula") || b.contains("Conjoined"));
    }

    private static void onChat(String raw) {
        if (!cfg().tarantulaInvincible || !StringUtil.strip(raw).trim().equals(STUCK)) return;
        Entity target = lastHit != null && lastHit.isAlive() ? lastHit : null;
        if (target == null) return;
        stuck = target;
        stuckPos = target.position();
    }

    private static void tick() {
        if (stuck != null && (!stuck.isAlive() || stuck.position().distanceToSqr(stuckPos) > 0.01)) {
            stuck = null;
            stuckPos = null;
        }
        if (++ticks % 5 != 0) return;
        if (!cfg().tarantulaEggSacs || !fighting()) {
            sacs = List.of();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<ArmorStand> near = new ArrayList<>();
        List<ArmorStand> shoot = new ArrayList<>();
        List<ArmorStand> timers = new ArrayList<>();
        for (ArmorStand s : EntityCache.armorStands()) {
            if (s.distanceToSqr(mc.player) > 20 * 20) continue;
            near.add(s);
            if (!s.hasCustomName()) continue;
            String n = StringUtil.strip(s.getName().getString()).trim();
            if (n.equalsIgnoreCase("SHOOT ME!")) shoot.add(s);
            else if (isTimer(n)) timers.add(s);
        }
        List<ArmorStand> out = new ArrayList<>();
        for (ArmorStand t : timers) {
            boolean paired = false;
            for (ArmorStand s : shoot) if (s.distanceToSqr(t) < 2.5 * 2.5) { paired = true; break; }
            if (!paired) continue;
            out.add(t);
            for (ArmorStand s : near) if (!s.hasCustomName() && s.distanceToSqr(t) < 1.2 * 1.2) out.add(s);
        }
        sacs = out;
    }

    // "12s 3/4"
    private static boolean isTimer(String n) {
        int sp = n.indexOf("s ");
        if (sp <= 0 || !digits(n, 0, sp)) return false;
        int slash = n.indexOf('/', sp);
        return slash > sp + 2 && digits(n, sp + 2, slash) && digits(n, slash + 1, n.length());
    }

    private static boolean digits(String s, int from, int to) {
        if (from >= to) return false;
        for (int i = from; i < to; i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!sacs.isEmpty()) {
            int col = TeslaMapsConfig.parseColor(c.colorTarantulaEggSac) | 0xFF000000;
            for (ArmorStand s : sacs) {
                if (s.isAlive()) ESPRenderer.drawBoxOutline(pose, ESPRenderer.lerpBox(s), col, 2.0f, cam, true);
            }
        }
        if (stuck != null && c.tarantulaInvincible) {
            int col = TeslaMapsConfig.parseColor(c.colorTarantulaInvincible) & 0xFFFFFF;
            ESPRenderer.drawFilledBox(pose, ESPRenderer.lerpBox(stuck), 0x55000000 | col, cam, true);
            ESPRenderer.drawBoxOutline(pose, ESPRenderer.lerpBox(stuck), 0xFF000000 | col, 2.0f, cam, true);
        }
    }
}
