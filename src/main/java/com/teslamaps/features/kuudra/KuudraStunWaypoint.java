package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/phasethree/StunWaypoints.kt and IQ's stun waypoints
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Box on the belly block the stunner has to break, after "You purchased Human Cannonball!". */
public final class KuudraStunWaypoint {

    public static final String[] MARKERS = {"A", "B", "C"};
    private static final Vec3[] BLOCKS = {new Vec3(-168, 28, -168), new Vec3(-153, 27, -173), new Vec3(-156, 28, -157)};
    private static final Vec3 ENTER = new Vec3(-161, 49, -186);

    private static boolean armed, inBelly;
    private static Object lastLevel;

    private KuudraStunWaypoint() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraStunWaypoint").section("Kuudra").when(() -> cfg().kuuBuildHelper && cfg().kuudraStunWaypoint)
                .serverChat(KuudraStunWaypoint::onChat)
                .tick(KuudraStunWaypoint::tick)
                .world(KuudraStunWaypoint::render);
    }

    /** Rows under the Build Helper. */
    public static void config(com.teslamaps.screen.config.ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("  Stun Waypoint", () -> c.kuudraStunWaypoint, v -> c.kuudraStunWaypoint = v)
                .desc("After buying the Human Cannonball: box on the belly block to break, offset to you before the launch, fixed inside Kuudra");
        b.dropdown("    Stun Marker", MARKERS, () -> c.kuudraStunMarker, v -> c.kuudraStunMarker = v);
        b.espStyle("  Stun Waypoint", () -> c.kuudraStunStyle);
    }

    private static void onChat(String raw) {
        String s = KuudraRunInfo.strip(raw);
        if (s.contains("You purchased Human Cannonball!")) {
            armed = true;
            inBelly = false;
        } else if (s.contains("destroyed one of Kuudra's pods!")) {
            armed = false;
            inBelly = false;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            armed = inBelly = false;
        }
        if (armed && mc.player.getY() < 50) {
            armed = false;
            inBelly = true;
        }
    }

    private static Vec3 block() {
        String m = cfg().kuudraStunMarker;
        for (int i = 0; i < MARKERS.length; i++) if (MARKERS[i].equals(m)) return BLOCKS[i];
        return BLOCKS[1];
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!armed && !inBelly) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 b = block();
        if (armed) {
            Vec3 p = mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            b = b.add(p.subtract(ENTER));
        }
        AABB box = new AABB(b.x, b.y, b.z, b.x + 1, b.y + 1, b.z + 1);
        EspStyle.of(cfg().kuudraStunStyle).drawBox(pose, box, cam);
    }
}
