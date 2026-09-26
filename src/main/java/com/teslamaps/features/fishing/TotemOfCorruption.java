package com.teslamaps.features.fishing;

// Ported from SkyHanni's TotemOfCorruption.kt; shown as a Deployables overlay line
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TotemOfCorruption {

    private record Totem(int id, double x, double y, double z, int seconds, long readAt, String owner, boolean own) {
        long left() { return Math.max(0, seconds - (System.currentTimeMillis() - readAt) / 1000); }
    }

    private static final Pattern REMAINING = Pattern.compile("^Remaining: (?:(\\d+)m )?(\\d+)s$");
    private static final double RADIUS = 16;
    private static final int[] LATITUDES = {-14, -10, -5, 0, 5, 10, 14};

    private static List<Totem> totems = List.of();
    private static Totem best;
    private static final Set<Integer> warned = new HashSet<>();
    private static int ticks;

    private TotemOfCorruption() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.deployableTotem && c.deployableOverlay;
    }

    public static void register() {
        Feature.of("TotemOfCorruption").section("Skills").when(TotemOfCorruption::on)
                .tick(TotemOfCorruption::tick)
                .world(TotemOfCorruption::render);
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player.getName().getString();
        List<ArmorStand> named = EntityCache.namedArmorStands();
        List<Totem> out = new ArrayList<>();
        for (ArmorStand s : named) {
            if (s.isRemoved() || s.getCustomName() == null || s.distanceToSqr(mc.player) > 10000) continue;
            if (!StringUtil.stripTrim(s.getCustomName().getString()).equals("Totem of Corruption")) continue;
            int seconds = -1;
            String owner = null;
            for (ArmorStand o : named) {
                if (o == s || o.getCustomName() == null || o.distanceToSqr(s) > 4) continue;
                String n = StringUtil.stripTrim(o.getCustomName().getString());
                Matcher m = REMAINING.matcher(n);
                if (m.matches()) seconds = Integer.parseInt(m.group(2)) + (m.group(1) == null ? 0 : Integer.parseInt(m.group(1)) * 60);
                else if (n.startsWith("Owner: ")) owner = n.substring(7).trim();
            }
            if (seconds < 0 || owner == null) continue;
            boolean own = owner.equals(me);
            if (cfg().deployableTotemOwnOnly && !own) continue;
            out.add(new Totem(s.getId(), s.getX(), s.getY(), s.getZ(), seconds, System.currentTimeMillis(), owner, own));
        }
        totems = out;
        Totem pick = null;
        double bestD = Double.MAX_VALUE;
        for (Totem t : out) {
            double d = mc.player.distanceToSqr(t.x, t.y, t.z);
            if (d > RADIUS * RADIUS * 4 && !t.own) continue;
            if (t.own) { pick = t; break; }
            if (d < bestD) { bestD = d; pick = t; }
        }
        best = pick;
        int warn = cfg().deployableTotemWarn;
        if (pick != null && warn > 0 && pick.seconds <= warn && warned.add(pick.id)) {
            mc.gui.setTimes(0, 40, 10);
            mc.gui.setTitle(Component.literal("§c§lTotem of Corruption §eabout to expire!"));
            mc.gui.setSubtitle(Component.literal(""));
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
        }
        if (warned.size() > 64) warned.clear();
    }

    /** Overlay line for Deployables, null when no totem is near. */
    public static String line() {
        Totem t = best;
        if (!on() || t == null) return null;
        long s = t.left();
        String time = s >= 60 ? (s / 60) + "m " + (s % 60 < 10 ? "0" : "") + (s % 60) + "s" : s + "s";
        return "§5Totem of Corruption §e" + time + " §7(" + (t.own ? "yours" : t.owner) + ")";
    }

    private static void render(PoseStack pose, Vec3 cam) {
        String mode = cfg().deployableTotemRange;
        if ("Off".equals(mode) || totems.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int color = TeslaMapsConfig.parseColor(cfg().colorTotemRange) | 0xFF000000;
        for (Totem t : totems) {
            double cy = t.y + 1;
            if ("Wireframe".equals(mode)) {
                for (int lat : LATITUDES) {
                    double r = Math.sqrt(RADIUS * RADIUS - lat * lat);
                    ESPRenderer.drawCircle(pose, t.x, cy + lat, t.z, r, color, 1.5f, cam);
                }
            } else {
                double dy = mc.player.getY() - cy;
                if (Math.abs(dy) >= RADIUS) continue;
                ESPRenderer.drawCircle(pose, t.x, mc.player.getY() + 0.05, t.z, Math.sqrt(RADIUS * RADIUS - dy * dy), color, 2f, cam);
            }
        }
    }

    /** Main thread, from Deployables.hideParticle. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        if (!cfg().deployableTotemHideParticles || !on() || totems.isEmpty()) return false;
        if (p.getParticle().getType() != ParticleTypes.WITCH || p.getMaxSpeed() != 0f) return false;
        for (Totem t : totems) {
            double dx = t.x - p.getX(), dy = t.y - p.getY(), dz = t.z - p.getZ();
            if (dx * dx + dy * dy + dz * dz < 16) return true;
        }
        return false;
    }
}
