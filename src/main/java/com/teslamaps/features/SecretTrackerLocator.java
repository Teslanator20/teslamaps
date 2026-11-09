package com.teslamaps.features;

// Ported from SkyHanni's DungeonSecretTrackerLocator.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.BezierFitter;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretTrackerLocator {

    private static final String TRACKER_ID = "SECRET_TRACKER";
    private static final Pattern DISTANCE = Pattern.compile("There's a secret (\\d+) blocks(?:.+and (\\d+) blocks)?");
    private static final String NO_MISSING = "There are no missing secrets near you!";

    private static final BezierFitter fitter = new BezierFitter(1);
    private static long lastParticle = 0;
    private static long lastUse = 0;
    private static int secretDistance = -1;
    private static Vec3 location = null;
    private static Object lastLevel = null;
    private static int shownDistance = -1;
    private static String distanceText = "";

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SecretTrackerLocator").section("Secrets & Waypoints").when(() -> cfg().secretTrackerLocator)
                .tick(SecretTrackerLocator::tick)
                .serverChat(SecretTrackerLocator::onChat)
                .world(SecretTrackerLocator::render)
                .config("Secrets & Waypoints", b -> b.toggle("Secret Tracker Locator",
                        () -> cfg().secretTrackerLocator, v -> cfg().secretTrackerLocator = v)
                        .desc("Marks where the Secret Tracker points, from its particles and the distance in chat"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
            lastUse = 0;
        }
    }

    /** Right click hook. True = block the click, the previous trail is still flying. */
    public static boolean onUseItem(ItemStack stack) {
        TeslaMapsConfig c = cfg();
        if (!c.secretTrackerLocator || !c.section("Secrets & Waypoints") || !DungeonManager.isInDungeon()) return false;
        if (!TRACKER_ID.equals(ItemUtil.skyblockId(stack))) return false;
        long now = System.currentTimeMillis();
        if (now - lastParticle < 200) return true;
        fitter.reset();
        lastUse = now;
        return false;
    }

    /** Particle hook, main thread only. */
    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        TeslaMapsConfig c = cfg();
        if (!c.secretTrackerLocator) return;
        if (!Minecraft.getInstance().isSameThread()) return;
        if (packet.particle().getType() != ParticleTypes.HAPPY_VILLAGER) return;
        if (packet.count() != 1 || packet.xMaxSpeed() != 0f) return;
        if (!DungeonManager.isInDungeon()) return;
        long now = System.currentTimeMillis();
        lastParticle = now;
        if (now - lastUse > 1000) return;
        if (!fitter.tryAdd(new Vec3(packet.x(), packet.y(), packet.z()), 1.0)) return;
        repredict();
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String msg = StringUtil.stripTrim(raw);
        if (msg.equals(NO_MISSING)) {
            reset();
            return;
        }
        if (!msg.startsWith("There's a secret")) return;
        Matcher m = DISTANCE.matcher(msg);
        if (!m.find()) return;
        int d1 = Integer.parseInt(m.group(1));
        int d2 = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        secretDistance = (int) Math.sqrt((double) d1 * d1 + (double) d2 * d2);
        repredict();
    }

    private static void repredict() {
        if (secretDistance < 0) return;
        Vec3 p = fitter.pointAt(secretDistance * 2.0);
        if (p != null) location = p;
    }

    private static void reset() {
        location = null;
        fitter.reset();
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        Vec3 at = location;
        if (at == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double distance = at.distanceTo(mc.player.getEyePosition());
        if (distance <= 3) {
            reset();
            return;
        }
        int d = (int) distance;
        if (d != shownDistance) {
            shownDistance = d;
            distanceText = "§e" + d + "m";
        }
        ESPRenderer.drawText(matrices, "§d§lSECRET", at, 1.7f, cam);
        ESPRenderer.drawText(matrices, distanceText, at.add(0, -0.1 - distance / (12 * 1.7), 0), 1.0f, cam);
    }
}
