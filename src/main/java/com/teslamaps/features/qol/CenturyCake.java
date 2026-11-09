package com.teslamaps.features.qol;

// Ported from Devonian's CenturyCakeTimer.kt (HUD) and WpcMod's CenturyCakeHelper.kt (cake glow)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CenturyCake {

    private static final Pattern EAT = Pattern.compile("^(?:Big )?Yum! You (?:gain|refresh) \\+\\d+\\S* (.+) for 48 hours!$");
    private static final long DURATION = 48 * 3_600_000L;
    private static final String SAMPLE = "§dCakes: §a1d 12h";
    // cake head texture hash -> stat, from WpcMod
    private static final Map<String, String> TEXTURES = Map.ofEntries(
            Map.entry("21185676416653bf70603c723ff7a48e080db649a8ce5d65b461bf654874935a", "Health"),
            Map.entry("ba7bfa80a85e707eedf945b88905942f5f7795ea5264d692a22e707fc577a88b", "Defense"),
            Map.entry("50f144fd4d7c89e03f35d7efe41d39783968235916a009e4d0f882ec2c8e6eb7", "Strength"),
            Map.entry("e25588999b674b399a1980a25d9ca323639d033e3f85040c1de7ffb428bca095", "Speed"),
            Map.entry("5eff0904d5ec691dd8e38d1f63d38bef0492013ece93d32af90dc0320116c597", "Intelligence"),
            Map.entry("e12a28e69da5e43ce8fdecc6a823c92fff84719e0147caac71c5c8d99da5781c", "Sea Creature Chance"),
            Map.entry("64592746cc35672ee29bb3a153d74f7a5d0e5fe8612729a34fad252fe7904283", "Magic Find"),
            Map.entry("f58cd2750884471c0836343343ec6e188229da054ef3671013ae8d43d6b02548", "Ferocity"),
            Map.entry("25d9243c61e2c3e1e0f9931843cadbd0dff4a646225ef54c557979cf39bb2057", "Pet Luck"),
            Map.entry("b237cdfd7d3f20e0f5b697a20c85324fbb7bcd409e653337ccd5a9ce8ccafc1d", "Farming Fortune"),
            Map.entry("ba0a5b510a8d824ff4910ca5b2698aa3d030f837810e8d7f0bbca8cf06ce2023", "Foraging Fortune"),
            Map.entry("fb7cfbb6af700e49bfdb60236920c522084bb808ab612474fb1f83ea410d5484", "Mining Fortune"),
            Map.entry("c3830d235c9de703946864c281f42f9d45647691ee353fbbb0722717706b8bda", "Vitality"),
            Map.entry("d40f9299508387618cfad6db35c9f6d807c49337d333d6ec3b13d9e87d0d488f", "True Defense"),
            Map.entry("d37b1fb4d0981901a292c42c653946ca0e2dea18be9cafa2f2f9933fdabaa984", "Rift Time"),
            Map.entry("498c88dafe133e797f2994acc053b3926e9291d0b7093598b39d272c7c94a366", "Cold Resistance"),
            Map.entry("63d220f235f284fcc5fdc45ddd9f97d66604da2a6ad41ab894a63fde8b7e4995", "Treasure Chance"),
            Map.entry("f0939cc98cee20d84889ca0dcbc83e35df70c3c98245bf2e6e6f6a3ad284cc7e", "Hunting Fortune"),
            Map.entry("4545c6bfd773024511e235e12f6a2a2ede060cb8dbba95e1fc245c16010676ab", "Sweep"),
            Map.entry("3b8a3e22d97c031a3f2f87c85f831b4abfa47358d3249cac043abe999f549ab6", "Tracking"));

    private static int ticks;
    private static String line;
    private static boolean anyReady = true;
    private static List<Entity> readyStands = List.of();
    private static HudEditElement hud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("centuryCakeTimer", "Century Cake Timer").ctx(HudEditElement.HudCtx.ANY).section("Timers")
                .enabled(() -> cfg().centuryCakeTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("CenturyCake").section("Timers")
                .when(() -> cfg().centuryCakeTimer || cfg().centuryCakeGlow)
                .tick(CenturyCake::tick)
                .serverChat(CenturyCake::onChat)
                .hudHiddenInInventory("centurycaketimer", CenturyCake::render)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Century Cake Timer", () -> c.centuryCakeTimer, v -> c.centuryCakeTimer = v)
                            .desc("HUD countdown until your first Century Cake buff runs out, move it in /tmap gui.");
                    b.toggle("  Only When Expired", () -> c.centuryCakeOnlyExpired, v -> c.centuryCakeOnlyExpired = v);
                    b.toggle("Century Cake Glow", () -> c.centuryCakeGlow, v -> c.centuryCakeGlow = v)
                            .desc("On your Private Island, cakes whose buff ran out (or was never eaten) glow.");
                    b.color("  Ready Color", () -> c.colorCenturyCakeReady, v -> c.colorCenturyCakeReady = v);
                });
    }

    private static void onChat(String raw) {
        if (raw.indexOf("Yum!") < 0) return;
        Matcher m = EAT.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        QolStore.get().cakes.put(m.group(1).trim(), System.currentTimeMillis() + DURATION);
        QolStore.save();
        ticks = 19;
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        if (c.centuryCakeGlow && !readyStands.isEmpty()) {
            int rgb = TeslaMapsConfig.parseColor(c.colorCenturyCakeReady) & 0xFFFFFF;
            for (Entity e : readyStands) if (e.isAlive()) EntityGlow.mark(e, rgb);
        }
        if (++ticks % 20 != 0) return;
        long now = System.currentTimeMillis();
        Map<String, Long> cakes = QolStore.get().cakes;
        long soonest = Long.MAX_VALUE;
        for (Long t : cakes.values()) if (t > now) soonest = Math.min(soonest, t);
        boolean expired = cakes.isEmpty() || cakes.values().stream().anyMatch(t -> t <= now);
        anyReady = expired || soonest == Long.MAX_VALUE;
        if (soonest == Long.MAX_VALUE) line = cakes.isEmpty() ? "§dCakes: §cNONE" : "§dCakes: §cREADY";
        else {
            long left = soonest - now;
            line = "§dCakes: " + (left < 3_600_000L ? "§c" : left < 12 * 3_600_000L ? "§e" : "§a") + QolStore.formatDuration(left)
                    + (expired ? " §c(some ready)" : "");
        }
        if (c.centuryCakeGlow && SkyblockUtils.isInArea("Private Island")) scanStands(cakes, now);
        else if (!readyStands.isEmpty()) readyStands = List.of();
    }

    private static void scanStands(Map<String, Long> cakes, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<Entity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand stand)) continue;
            String tex = SoulweaverHider.headTextureUrl(stand.getMainHandItem());
            if (tex == null) continue;
            String stat = TEXTURES.get(tex);
            if (stat == null) continue;
            Long until = cakes.get(stat);
            if (until == null || until <= now) out.add(e);
        }
        readyStands = out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null || !cfg().centuryCakeTimer || !SkyblockUtils.isInSkyblock()) return;
        if (cfg().centuryCakeOnlyExpired && !anyReady) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
