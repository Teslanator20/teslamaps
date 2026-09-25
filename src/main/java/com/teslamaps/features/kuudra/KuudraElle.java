package com.teslamaps.features.kuudra;

// Ported from IQ's Elle Highlight (a/d.java)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class KuudraElle {

    private KuudraElle() {}

    private static Entity elle;
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraElle").section("Kuudra").when(() -> cfg().kuuElleHighlight)
                .tick(KuudraElle::tick)
                .world(KuudraElle::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Elle Highlight", () -> c.kuuElleHighlight, v -> c.kuuElleHighlight = v)
                            .desc("Box around Elle during the build phase");
                    b.espStyle("  Elle", () -> c.kuuElleEsp);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (KuudraBuild.phase != 2 || mc.level == null || !KuudraBuild.inKuudra()) {
            elle = null;
            return;
        }
        if (elle == null || elle.isRemoved() || ++ticks % 20 == 0) elle = find(mc);
        if (elle != null) EspStyle.of(cfg().kuuElleEsp).tickEntity(elle);
    }

    private static Entity find(Minecraft mc) {
        ArmorStand tag = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand s) || !s.hasCustomName()) continue;
            String n = StringUtil.stripTrim(s.getCustomName().getString()).toLowerCase(Locale.ROOT);
            if (n.contains("elle") && !n.contains("'")) { tag = s; break; }
        }
        if (tag == null) return null;
        Player best = null;
        double bestD = 9;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.getUUID().version() == 4) continue;
            double dx = p.getX() - tag.getX(), dz = p.getZ() - tag.getZ(), dy = tag.getY() - p.getY();
            if (dy < -0.5 || dy > 3.5) continue;
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = p; }
        }
        return best != null ? best : tag;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Entity e = elle;
        if (e == null || e.isRemoved()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        EspStyle.of(cfg().kuuElleEsp).drawEntity(pose, e, cam, pt);
    }
}
