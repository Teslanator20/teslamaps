package com.teslamaps.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One ESP look as a config field: {@code public EspStyle fooEsp = new EspStyle("FFFF5555");}
 * plus {@code b.espStyle("Foo", () -> config.fooEsp)} in the config screen.
 * Gson leaves the field null in an old json that lacks it, so read it through {@link #of}.
 */
public class EspStyle {

    public static final String OUTLINE = "Outline";
    public static final String FILLED = "Filled";
    public static final String BOTH = "Filled + Outline";
    public static final String GLOW = "Glow";
    public static final String[] MODES = {OUTLINE, FILLED, BOTH, GLOW};

    public String color = "FFFFFFFF";   // RRGGBB or AARRGGBB
    public String mode = OUTLINE;
    public float lineWidth = 2.0f;
    public boolean tracer = false;
    public boolean throughWalls = true;

    public EspStyle() {}

    public EspStyle(String color) {
        this.color = color;
    }

    public EspStyle(String color, String mode) {
        this.color = color;
        this.mode = mode;
    }

    private static final EspStyle FALLBACK = new EspStyle();

    public static EspStyle of(EspStyle s) {
        return s != null ? s : FALLBACK;
    }

    public int argb() {
        return TeslaMapsConfig.parseColor(color);
    }

    public boolean glow() {
        return GLOW.equals(mode);
    }

    /** Box around a block or area. Glow has no meaning there and falls back to the outline. */
    public void drawBox(PoseStack pose, AABB box, Vec3 cam) {
        int c = argb();
        if (FILLED.equals(mode) || BOTH.equals(mode)) ESPRenderer.drawFilledBox(pose, box, c, cam, throughWalls);
        if (!FILLED.equals(mode)) ESPRenderer.drawBoxOutline(pose, box, c | 0xFF000000, lineWidth, cam, throughWalls);
        if (tracer) ESPRenderer.drawTracerFromCamera(pose, box.getCenter(), c | 0xFF000000, cam, throughWalls);
    }

    /** Tick side of an entity highlight: registers the outline glow when that mode is picked. */
    public void tickEntity(Entity e) {
        if (glow()) EntityGlow.mark(e, argb() & 0xFFFFFF);
    }

    /** Render side of an entity highlight: box and tracer (the glow itself is drawn by vanilla). */
    public void drawEntity(PoseStack pose, Entity e, Vec3 cam, float partialTick) {
        Vec3 p = e.getPosition(partialTick);
        AABB box = e.getBoundingBox().move(p.subtract(e.position()));
        int c = argb() | 0xFF000000;
        if (!glow()) {
            if (FILLED.equals(mode) || BOTH.equals(mode)) ESPRenderer.drawFilledBox(pose, box, argb(), cam, throughWalls);
            if (!FILLED.equals(mode)) ESPRenderer.drawBoxOutline(pose, box, c, lineWidth, cam, throughWalls);
        }
        if (tracer) ESPRenderer.drawTracerFromCamera(pose, box.getCenter(), c, cam, throughWalls);
    }
}
