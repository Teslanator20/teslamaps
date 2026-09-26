package com.teslamaps.features.rift;

// Ported from SkyHanni's VoltHighlighter.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoltCrux {

    private static final String LIGHTNING = "e1b1bfb536b6416b26a283d2d8ad4b1771bbe5b76896e2727d5e833bc894982d";
    private static final String FRIENDLY = "7aea6251e58e3d20552a035d424561eaee083feacd1e6cb3c2a5cf945ce6d76e";
    private static final String HOSTILE = "8f9ede970d40c5b245cbd651349ee166f695d250345f8f0e63fcb10cfb5a2b78";
    private static final int NONE = 0, FRIEND = 1, HOSTILE_STATE = 2, DISCHARGING = 3;
    private static final double RADIUS = 7, HEIGHT = 20;
    private static final long CHARGE_MS = 12_000;

    private static final Map<Integer, Integer> STATE = new HashMap<>();
    private static final Map<Integer, Long> CHARGING_SINCE = new HashMap<>();
    private static List<ArmorStand> discharging = List.of();
    private static boolean rift;
    private static int ticks;

    private VoltCrux() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("VoltCrux").section("Rift").when(() -> cfg().voltCrux)
                .tick(VoltCrux::tick)
                .world(VoltCrux::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Dreadfarm");
                    b.toggle("Volt Crux Helper", () -> c.voltCrux, v -> c.voltCrux = v)
                            .desc("Volts in the Dreadfarm: mood colours, lightning range and a countdown to the strike.");
                    b.toggle("  Mood Meter", () -> c.voltMoodMeter, v -> c.voltMoodMeter = v)
                            .desc("Outline: green = friendly, red = hostile, blue = discharging.");
                    b.toggle("  Lightning Range", () -> c.voltRange, v -> c.voltRange = v)
                            .desc("7 block cylinder around a discharging Volt plus a 'Lightning: x.xs' countdown.");
                    b.color("  Range Color", () -> c.voltRangeColor, v -> c.voltRangeColor = v);
                });
    }

    private static int state(ArmorStand s) {
        String url = SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD));
        if (url == null) return NONE;
        return switch (url) {
            case LIGHTNING -> DISCHARGING;
            case FRIENDLY -> FRIEND;
            case HOSTILE -> HOSTILE_STATE;
            default -> NONE;
        };
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 0) {
            rift = CraftRoomHolograms.inRift();
            if (!rift) {
                STATE.clear();
                CHARGING_SINCE.clear();
            }
        }
        if (!rift) {
            discharging = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        List<ArmorStand> out = new ArrayList<>();
        for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(64))) {
            int st = state(s);
            Integer old = STATE.put(s.getId(), st);
            if (st == NONE) continue;
            if (st == DISCHARGING) {
                if (old != null && old != DISCHARGING) CHARGING_SINCE.put(s.getId(), now);
                out.add(s);
            } else {
                CHARGING_SINCE.remove(s.getId());
            }
            if (c.voltMoodMeter) EntityGlow.mark(s, st == FRIEND ? 0x00FF00 : st == HOSTILE_STATE ? 0xFF0000 : 0x0000FF);
        }
        if (STATE.size() > 256) STATE.clear();
        discharging = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!c.voltRange || discharging.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int color = TeslaMapsConfig.parseColor(c.voltRangeColor);
        long now = System.currentTimeMillis();
        for (ArmorStand s : discharging) {
            Vec3 p = s.getPosition(pt);
            double base = p.y - 4;
            for (double y = 0; y <= HEIGHT; y += 2) ESPRenderer.drawCircle(pose, p.x, base + y, p.z, RADIUS, color, 2f, cam);
            Long since = CHARGING_SINCE.get(s.getId());
            if (since == null) continue;
            long left = CHARGE_MS - (now - since);
            if (left <= 0) continue;
            ESPRenderer.drawText(pose, "§eLightning: " + (left / 1000) + "." + (left % 1000 / 100) + "s",
                    new Vec3(p.x, p.y + 2.5, p.z), 2.5f, cam);
        }
    }
}
