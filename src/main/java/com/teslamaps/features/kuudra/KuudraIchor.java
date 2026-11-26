package com.teslamaps.features.kuudra;

// Ported from IQ's Ichor Pool Area (a/t.java)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraIchor {

    private KuudraIchor() {}

    private static final Pattern CAST = Pattern.compile(
            "(?i)(?:casting(?:\\s+spell:)?\\s+ichor\\s+pool!?|ichor\\s+pool\\s+cast(?:ed)?!?)\\s*(?:at|@)?\\s*\\(?\\s*(?:x:\\s*)?(-?\\d+)\\s*[, ]+\\s*(?:y:\\s*)?(-?\\d+)\\s*[, ]+\\s*(?:z:\\s*)?(-?\\d+)");
    private static final long DURATION = 20_000;

    record Pool(Vec3 center, long expires) {}

    private static final List<Pool> POOLS = new ArrayList<>();
    private static Object lastLevel;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraIchor").section("Kuudra").when(() -> cfg().kuuIchorPool)
                .chat(KuudraIchor::onChat)
                .tick(KuudraIchor::tick)
                .world(KuudraIchor::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Ichor Pool Circle", () -> c.kuuIchorPool, v -> c.kuuIchorPool = v)
                            .desc("Draws the 8 block Ichor Pool circle for 20 s when you or a party member casts it");
                    b.color("  Circle Color", () -> c.kuuIchorColor, v -> c.kuuIchorColor = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.toLowerCase(Locale.ROOT).contains("ichor pool")) return;
        if (!KuudraBuild.inKuudra()) return;
        Minecraft mc = Minecraft.getInstance();
        if (t.equals("Casting Spell: Ichor Pool!") && StringUtil.isServerMessage(raw)) {
            if (mc.player != null) add(Vec3.atBottomCenterOf(mc.player.blockPosition()).add(0, 0.05, 0));
            return;
        }
        if (!t.startsWith("Party > ")) return;
        Matcher m = CAST.matcher(t);
        if (m.find()) {
            add(new Vec3(Integer.parseInt(m.group(1)) + 0.5, Integer.parseInt(m.group(2)) + 0.05, Integer.parseInt(m.group(3)) + 0.5));
        }
    }

    private static void add(Vec3 c) {
        long exp = System.currentTimeMillis() + DURATION;
        for (Pool p : POOLS) if (p.center.distanceToSqr(c) < 4) return;
        POOLS.add(new Pool(c, exp));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            POOLS.clear();
        }
        if (POOLS.isEmpty()) return;
        long now = System.currentTimeMillis();
        POOLS.removeIf(p -> p.expires <= now);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (POOLS.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().kuuIchorColor);
        for (Pool p : POOLS) ESPRenderer.drawCircle(pose, p.center.x, p.center.y, p.center.z, 8.0, color, 2f, cam, true);
    }
}
