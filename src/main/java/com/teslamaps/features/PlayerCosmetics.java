package com.teslamaps.features;

// Player size after Odin's PlayerSize.kt / Devonian's PlayerScale, trail after Shizo's Trail
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class PlayerCosmetics {

    public static final String[] TARGETS = {"Self", "Real Players", "Everyone"};
    private static final int MAX_TRAIL = 200;

    private static final Vec3[] trail = new Vec3[MAX_TRAIL];
    private static final java.util.List<Vec3> PTS = new java.util.ArrayList<>(MAX_TRAIL);
    private static final int[] COLORS = new int[MAX_TRAIL + 1];
    private static int head, count;
    private static Object lastLevel;

    private PlayerCosmetics() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PlayerSize").section("Render").when(() -> cfg().playerSize)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Player Size", () -> c.playerSize, v -> c.playerSize = v)
                            .desc("Scales player models client-side. Negative height turns them upside down.");
                    b.dropdown("  Players", TARGETS, () -> c.playerSizeTarget, v -> c.playerSizeTarget = v)
                            .desc("Real Players skips Hypixel NPCs.");
                    b.slider("  Width", -1f, 3f, () -> c.playerSizeX, v -> c.playerSizeX = v, 2);
                    b.slider("  Height", -1f, 3f, () -> c.playerSizeY, v -> c.playerSizeY = v, 2);
                    b.slider("  Depth", -1f, 3f, () -> c.playerSizeZ, v -> c.playerSizeZ = v, 2);
                });
        Feature.of("PlayerTrail").section("Render").when(() -> cfg().playerTrail)
                .tick(PlayerCosmetics::tick)
                .world(PlayerCosmetics::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Player Trail", () -> c.playerTrail, v -> c.playerTrail = v)
                            .desc("Draws a fading line behind you while you move.");
                    b.color("  Trail Color", () -> c.playerTrailColor, v -> c.playerTrailColor = v);
                    b.slider("  Trail Length (ticks)", 5f, 200f, () -> c.playerTrailLength, v -> c.playerTrailLength = v, 0);
                    b.slider("  Trail Width", 1f, 8f, () -> c.playerTrailWidth, v -> c.playerTrailWidth = v, 1);
                });
    }

    /** From PlayerSizeMixin, before the model is drawn. */
    public static void scale(int entityId, PoseStack pose) {
        if (!scales(entityId)) return;
        TeslaMapsConfig c = cfg();
        if (c.playerSizeY < 0) pose.translate(0f, c.playerSizeY * 2, 0f);
        pose.scale(c.playerSizeX, c.playerSizeY, c.playerSizeZ);
    }

    public static boolean scales(int entityId) {
        TeslaMapsConfig c = cfg();
        return c.playerSize && c.section("Render") && matches(entityId, c.playerSizeTarget);
    }

    public static boolean matches(int entityId, String target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (entityId == mc.player.getId()) return true;
        if ("Self".equals(target) || mc.level == null) return false;
        if ("Everyone".equals(target)) return true;
        Entity e = mc.level.getEntity(entityId);
        return e instanceof Player p && p.getUUID().version() == 4;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            count = 0;
        }
        Vec3 p = mc.player.position().add(0, 0.1, 0);
        Vec3 last = count > 0 ? trail[(head - 1 + MAX_TRAIL) % MAX_TRAIL] : null;
        if (last != null && last.distanceToSqr(p) < 1.0E-4) {
            if (count > 0) count--;
            return;
        }
        trail[head] = p;
        head = (head + 1) % MAX_TRAIL;
        if (count < MAX_TRAIL) count++;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        int n = Math.min(count, Math.max(2, (int) c.playerTrailLength));
        if (n < 2) return;
        int color = TeslaMapsConfig.parseColor(c.playerTrailColor);
        int alpha = (color >>> 24) == 0 ? 255 : color >>> 24;
        int rgb = color & 0xFFFFFF;
        PTS.clear();
        for (int i = 1; i <= n; i++) {
            Vec3 a = trail[(head - i + MAX_TRAIL) % MAX_TRAIL];
            if (a == null) break;
            COLORS[PTS.size()] = (Math.max(1, alpha * (n - i + 1) / n) << 24) | rgb;
            PTS.add(a);
        }
        ESPRenderer.drawPolyline(pose, PTS, COLORS, c.playerTrailWidth, cam, false);
    }
}
