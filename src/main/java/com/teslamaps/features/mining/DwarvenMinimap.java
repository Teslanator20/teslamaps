package com.teslamaps.features.mining;

// After SkyblockExtras' Dwarven Map (map image and world-to-map scale from SBE)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Dwarven Mines overview map with the player arrow and the areas of open commissions. */
public final class DwarvenMinimap {

    private static final Identifier MAP = Identifier.fromNamespaceAndPath("teslamaps", "textures/gui/dwarven_map.png");
    private static final Identifier ICON = Identifier.withDefaultNamespace("textures/map/decorations/player.png");
    private static final int TEX_W = 481, TEX_H = 600;
    private static final float BASE = 0.25f;
    private static final int W = Math.round(TEX_W * BASE), H = Math.round(TEX_H * BASE);

    private record Area(String key, int x, int z) {}

    private static final Area[] AREAS = {
            new Area("Lava Springs", 60, -15), new Area("Cliffside Veins", 40, 40),
            new Area("Rampart's Quarry", -100, -20), new Area("Upper Mines", -130, -50),
            new Area("Royal Mines", 130, 30),
    };

    private static HudEditElement hud;
    private static boolean show;
    private static int ticks;
    private static int count;
    private static final float[] dotX = new float[AREAS.length], dotY = new float[AREAS.length];
    private static final int[] dotColor = new int[AREAS.length];

    private DwarvenMinimap() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("dwarvenMinimap", "Dwarven Mines Map").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().dwarvenMinimap)
                .size(() -> W, () -> H)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, false));
        Feature.of("DwarvenMinimap").section("Mining").when(() -> cfg().dwarvenMinimap)
                .tick(DwarvenMinimap::tick)
                .hudHiddenInInventory("dwarvenminimap", DwarvenMinimap::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Dwarven Mines Map", () -> c.dwarvenMinimap, v -> c.dwarvenMinimap = v)
                            .desc("Overview map of the Dwarven Mines with your position. Move and scale it in /tmap gui.")
                            .tags("minimap", "dwarven mines", "map", "hud");
                    b.toggle("  Show Commission Areas", () -> c.dwarvenMinimapCommissions, v -> c.dwarvenMinimapCommissions = v)
                            .desc("Dot on the areas of your open mithril/titanium commissions");
                });
    }

    private static float mapX(double x) {
        return (float) (Math.max(0, x + 200) * 460 / 400);
    }

    private static float mapY(double z) {
        return (float) (Math.max(0, z + 134) * 480 / 334);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        show = SkyblockUtils.isInArea("Dwarven Mines") && mc.player != null && mc.player.getZ() < 290;
        if (!show || ++ticks % 20 != 0) return;
        count = 0;
        if (!cfg().dwarvenMinimapCommissions) return;
        for (var com : Commissions.active()) {
            if (com.done()) continue;
            for (Area a : AREAS) {
                if (!com.name().contains(a.key()) || count == AREAS.length) continue;
                dotX[count] = mapX(a.x()) * BASE;
                dotY[count] = mapY(a.z()) * BASE;
                dotColor[count] = com.name().contains("Titanium") ? 0xFFD8D6D8 : 0xFF45BDE0;
                count++;
            }
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), true);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, boolean live) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        pose.pushMatrix();
        pose.scale(BASE, BASE);
        g.blit(RenderPipelines.GUI_TEXTURED, MAP, 0, 0, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        pose.popMatrix();
        if (live) {
            for (int i = 0; i < count; i++) {
                int cx = Math.round(dotX[i]), cy = Math.round(dotY[i]);
                g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF000000);
                g.fill(cx - 2, cy - 2, cx + 2, cy + 2, dotColor[i]);
            }
        }
        if (mc.player != null) {
            float px = live ? mapX(mc.player.getX()) * BASE : W / 2f;
            float py = live ? mapY(mc.player.getZ()) * BASE : H / 2f;
            pose.translate(px - 2.5f, py - 3.5f);
            pose.rotateAbout(Mth.DEG_TO_RAD * cardinal(mc.player.getYRot()), 2.5f, 3.5f);
            g.blit(RenderPipelines.GUI_TEXTURED, ICON, 0, 0, 2, 0, 5, 7, 8, 8);
        }
        pose.popMatrix();
    }

    private static float cardinal(float yaw) {
        yaw += 180;
        byte clipped = (byte) ((yaw + (yaw < 0.0 ? -8.0 : 8.0)) * 16.0 / 360.0);
        return clipped * 360f / 16f;
    }
}
