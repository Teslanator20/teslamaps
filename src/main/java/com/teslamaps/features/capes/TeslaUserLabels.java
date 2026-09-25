package com.teslamaps.features.capes;

// Idea from Bax's SchrumboLabel.java: a label floating above other mod users
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class TeslaUserLabels {

    private static final String TEXT = "TeslaMaps";

    private static List<AbstractClientPlayer> users = List.of();
    private static int color = 0xFF55FFFF;
    private static int ticks;

    private TeslaUserLabels() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TeslaUserLabels").section("Render").when(() -> cfg().teslaUserLabels)
                .tick(TeslaUserLabels::tick)
                .world(TeslaUserLabels::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("TeslaMaps User Labels", () -> c.teslaUserLabels, v -> c.teslaUserLabels = v)
                            .desc("A small \"TeslaMaps\" label above other players who use TeslaMaps (known through the cape server)");
                    b.color("  Label Color", () -> c.teslaUserLabelColor, v -> c.teslaUserLabelColor = v);
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!CapeSync.enabled()) {
            users = List.of();
            return;
        }
        color = 0xFF000000 | (TeslaMapsConfig.parseColor(cfg().teslaUserLabelColor) & 0xFFFFFF);
        List<AbstractClientPlayer> out = new ArrayList<>();
        int self = mc.player.getId();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.getId() == self || p.isInvisible()) continue;
            if (CapeSync.isUser(p.getUUID().toString())) out.add(p);
        }
        users = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AbstractClientPlayer> list = users;
        if (list.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (AbstractClientPlayer p : list) {
            if (p.isRemoved()) continue;
            Vec3 pos = p.getPosition(pt).add(0, p.getBbHeight() + 0.8, 0);
            if (pos.distanceToSqr(cam) > 64 * 64) continue;
            ESPRenderer.drawText(pose, TEXT, pos, 0.8f, cam, false, false, color);
        }
    }
}
