package com.teslamaps.safari;

// Ported from Sparkling Mutuals' TeammateEsp.kt + TeammateEspRules.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Party members glow and get a see-through name in the Safari and Torrhus Canyon. */
public final class TeammateEsp {

    private static final Set<String> AREAS = Set.of("Torrhus Canyon", "Torrhus Springs", "Critter Safari Entrance", "Critter Safari");

    private static final List<Player> mates = new ArrayList<>();

    private TeammateEsp() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TeammateEsp").section("Critter Safari").legit().when(() -> cfg().safariTeammateEsp)
                .tick(TeammateEsp::tick)
                .world(TeammateEsp::render)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Teammate ESP");
                    b.toggle("Teammate ESP", () -> c.safariTeammateEsp, v -> c.safariTeammateEsp = v).cheat()
                            .desc("Party members glow and get a name you can read through walls, in the Critter Safari and Torrhus Canyon. Party comes from chat, /p list fills it in.")
                            .tags("teammate", "party", "glow", "name");
                    b.toggle("  Glow", () -> c.safariTeammateGlow, v -> c.safariTeammateGlow = v);
                    b.toggle("  Names Through Walls", () -> c.safariTeammateNames, v -> c.safariTeammateNames = v);
                    b.color("  Color", () -> c.safariTeammateColor, v -> c.safariTeammateColor = v);
                    b.slider("  Name Size", 0.5f, 3.0f, () -> c.safariTeammateNameScale, v -> c.safariTeammateNameScale = v, 1);
                });
    }

    private static boolean inArea() {
        if (SafariData.inSafari()) return true;
        String a = SkyblockUtils.getArea();
        for (String s : AREAS) if (s.equalsIgnoreCase(a)) return true;
        return false;
    }

    private static void tick() {
        mates.clear();
        Minecraft mc = Minecraft.getInstance();
        if (!PartyTracker.inParty() || !inArea()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isRemoved()) continue;
            if (!PartyTracker.isMember(p.getGameProfile().name())) continue;
            mates.add(p);
        }
        if (!cfg().safariTeammateGlow) return;
        int rgb = TeslaMapsConfig.parseColor(cfg().safariTeammateColor) & 0xFFFFFF;
        for (Player p : mates) EntityGlow.mark(p, rgb);
    }

    /** The custom label replaces the vanilla name tag. */
    public static boolean hideNameTag(Entity e) {
        TeslaMapsConfig c = cfg();
        return c.safariTeammateEsp && c.safariTeammateNames && !mates.isEmpty() && e instanceof Player
                && mates.contains(e) && c.section("Critter Safari") && !LegitMode.blocksCheats();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (mates.isEmpty() || !cfg().safariTeammateNames) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int color = TeslaMapsConfig.parseColor(cfg().safariTeammateColor) | 0xFF000000;
        float base = cfg().safariTeammateNameScale;
        for (Player p : mates) {
            if (p.isRemoved()) continue;
            Vec3 pos = p.getPosition(pt);
            double dist = pos.distanceTo(cam);
            float scale = (float) (base * Math.max(dist * 0.12, 1.0));
            Vec3 at = new Vec3(pos.x, pos.y + p.getBbHeight() + 0.7 + dist * 0.015, pos.z);
            ESPRenderer.drawText(pose, p.getGameProfile().name(), at, scale, cam, false, true, color);
        }
    }
}
