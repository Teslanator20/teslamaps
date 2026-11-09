package com.teslamaps.features;

// After SkyHanni's FlareDisplay.kt, on top of the flares Deployables already tracks
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Flare range, world timer, expiry warning with optional screen flash, flame particle hide. */
public final class FlareExtras {

    public static final String[] RANGE_MODES = {"Off", "Wireframe", "Ring At Feet"};
    private static final double RADIUS = 40;
    private static final int[] LATITUDES = {-36, -28, -18, -8, 0, 8, 18, 28, 36};

    private record Shown(Vec3 pos, int color, String name, String time) {}

    private static List<Deployables.FlarePos> flares = List.of();
    private static List<Shown> shown = List.of();
    private static final Set<String> warned = new HashSet<>();
    private static boolean flashing;
    private static int ticks;

    private FlareExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.deployableFlareExtras && c.deployableOverlay && c.deployableFlare;
    }

    public static void register() {
        Feature.of("FlareExtras").section("Skills").when(FlareExtras::on)
                .tick(FlareExtras::tick)
                .world(FlareExtras::render)
                .hud("flareflash", FlareExtras::renderFlash);
    }

    private static void tick() {
        if (ticks++ % 5 != 0) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        List<Deployables.FlarePos> list = Deployables.flares();
        List<Shown> out = new ArrayList<>(list.size());
        boolean flash = false;
        for (Deployables.FlarePos f : list) {
            int color = TeslaMapsConfig.parseColor(color(c, f.type())) | 0xFF000000;
            out.add(new Shown(new Vec3(f.x(), f.y(), f.z()), color, f.type().displayName, "§b" + time(f.secondsLeft())));
            if (c.deployableFlareWarn <= 0 || f.secondsLeft() > c.deployableFlareWarn) continue;
            flash = true;
            // one warning per flare, keyed by where it stands
            String key = f.type().name() + (int) f.x() + "," + (int) f.z();
            if (warned.add(key)) {
                mc.gui.hud.setTimes(0, 30, 10);
                mc.gui.hud.setTitle(Component.literal(f.type().displayName + " §eexpires in §b" + f.secondsLeft() + "s"));
                mc.gui.hud.setSubtitle(Component.literal(""));
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
            }
        }
        if (list.isEmpty() || warned.size() > 64) warned.clear();
        flares = list;
        shown = out;
        flashing = flash && c.deployableFlareFlash;
    }

    private static String color(TeslaMapsConfig c, Deployables.Deployable d) {
        return switch (d) {
            case SOS_FLARE -> c.colorFlareSos;
            case ALERT_FLARE -> c.colorFlareAlert;
            default -> c.colorFlareWarning;
        };
    }

    private static String time(long s) {
        return s >= 60 ? (s / 60) + "m " + (s % 60 < 10 ? "0" : "") + (s % 60) + "s" : s + "s";
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Shown> l = shown;
        if (l.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        String mode = c.deployableFlareRange;
        for (Shown s : l) {
            if ("Wireframe".equals(mode)) {
                for (int lat : LATITUDES) {
                    ESPRenderer.drawCircle(pose, s.pos.x, s.pos.y + lat, s.pos.z, Math.sqrt(RADIUS * RADIUS - lat * lat), s.color, 1.5f, cam);
                }
            } else if ("Ring At Feet".equals(mode)) {
                double dy = mc.player.getY() - s.pos.y;
                if (Math.abs(dy) < RADIUS) {
                    ESPRenderer.drawCircle(pose, s.pos.x, mc.player.getY() + 0.05, s.pos.z,
                            Math.sqrt(RADIUS * RADIUS - dy * dy), s.color, 2f, cam);
                }
            }
            if (c.deployableFlareWorldText) {
                Vec3 at = s.pos.add(0, 1.2, 0);
                ESPRenderer.drawText(pose, s.name, at, 1.5f, cam, true, false);
                ESPRenderer.drawText(pose, s.time, at.add(0, -0.45, 0), 1.5f, cam, true, false);
            }
        }
    }

    private static void renderFlash(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!flashing) return;
        int alpha = (int) Math.max(0, Math.min(255, (2 + Math.sin(System.currentTimeMillis() / 1000.0)) * 255 / 4));
        int rgb = TeslaMapsConfig.parseColor(cfg().deployableFlareFlashColor) & 0xFFFFFF;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha / 2 << 24) | rgb);
    }

    /** Main thread, from Deployables.hideParticle. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        if (!cfg().deployableFlareHideParticles || !on() || flares.isEmpty()) return false;
        if (p.particle().getType() != ParticleTypes.FLAME) return false;
        for (Deployables.FlarePos f : flares) {
            double dx = f.x() - p.x(), dy = f.y() - p.y(), dz = f.z() - p.z();
            if (dx * dx + dy * dy + dz * dz < 6.25 + 2) return true;
        }
        return false;
    }

    /** Overlay suffix for a flare line, empty when the option is off. */
    public static String manaBuff(Deployables.Deployable d) {
        TeslaMapsConfig c = cfg();
        if (!c.deployableFlareExtras || !c.deployableFlareManaBuff) return "";
        return switch (d) {
            case SOS_FLARE -> " §b+125% §7mana regen";
            case ALERT_FLARE -> " §b+50% §7mana regen";
            default -> "";
        };
    }
}
