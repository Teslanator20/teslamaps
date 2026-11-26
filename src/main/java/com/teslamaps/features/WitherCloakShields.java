package com.teslamaps.features;

// Ported from NotEnoughUpdates' miscfeatures/WitherCloakChanger.java + MixinEntityChargedCreeper
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

public final class WitherCloakShields {

    private static final String[] MODES = {"Vanilla", "Hide", "Shields"};
    private static final float[] QUAD = new float[12];
    private static boolean active;
    private static long lastSeen;

    private WitherCloakShields() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("WitherCloakShields").section("Render").when(() -> "Shields".equals(cfg().witherCloakCreepers))
                .serverChat(WitherCloakShields::onChat)
                .tick(WitherCloakShields::tick)
                .world(WitherCloakShields::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Wither Cloak");
                    b.dropdown("Wither Cloak Creepers", MODES, () -> c.witherCloakCreepers, v -> c.witherCloakCreepers = v)
                            .desc("Hide = the Creeper Veil creepers are not drawn (they still block clicks). Shields = rotating shields you can see through instead.");
                    b.slider("  Shield Count", 1f, 20f, () -> c.witherCloakCount, v -> c.witherCloakCount = v, 0);
                    b.slider("  Rotation Speed", -360f, 360f, () -> c.witherCloakSpeed, v -> c.witherCloakSpeed = v, 0);
                    b.slider("  Distance", 0.5f, 3f, () -> c.witherCloakDistance, v -> c.witherCloakDistance = v, 1);
                    b.color("  Shield Color", () -> c.witherCloakColor, v -> c.witherCloakColor = v)
                            .desc("Alpha sets the transparency.");
                });
    }

    private static void onChat(String raw) {
        String s = StringUtil.strip(raw).trim();
        if (s.startsWith("Creeper Veil")) {
            active = s.equals("Creeper Veil Activated!");
            lastSeen = System.currentTimeMillis();
        } else if (s.startsWith("Not enough mana! Creeper Veil De-activated!")) {
            active = false;
        }
    }

    private static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (!mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(4), WitherCloakShields::isCloak).isEmpty()) lastSeen = now;
        else if (now - lastSeen > 2000) active = false;
    }

    private static boolean isCloak(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e instanceof Creeper c && c.isPowered() && mc.player != null && c.distanceToSqr(mc.player) < 16;
    }

    /** From EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        return active && e instanceof Creeper && "Shields".equals(cfg().witherCloakCreepers) && isCloak(e);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        int color = TeslaMapsConfig.parseColor(c.witherCloakColor);
        Vec3 p = mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        int n = Math.max(1, Math.round(c.witherCloakCount));
        double base = (System.currentTimeMillis() % 360000L) / 1000.0 * c.witherCloakSpeed;
        double d = c.witherCloakDistance;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(base + i * 360.0 / n);
            double cos = Math.cos(a), sin = Math.sin(a);
            corner(0, p, -0.4, 0.2, d, cos, sin);
            corner(1, p, 0.4, 0.2, d, cos, sin);
            corner(2, p, 0.4, 2.0, d, cos, sin);
            corner(3, p, -0.4, 2.0, d, cos, sin);
            ESPRenderer.drawQuad(pose, QUAD, color, cam);
        }
    }

    private static void corner(int i, Vec3 p, double x, double y, double z, double cos, double sin) {
        QUAD[i * 3] = (float) (p.x + x * cos - z * sin);
        QUAD[i * 3 + 1] = (float) (p.y + y);
        QUAD[i * 3 + 2] = (float) (p.z + x * sin + z * cos);
    }
}
