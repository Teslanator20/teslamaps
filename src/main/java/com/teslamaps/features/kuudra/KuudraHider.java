package com.teslamaps.features.kuudra;

// Tags: legitcatmod's kuudra/HideTags.java, nameless stands: Skytils' kuudraHideNonNametags,
// bossbar: IQ's BossBarHudMixin, tiny mobs: legitcatmod's tinyKuudraMobs, tentacles: Shizo's KuudraInfo.kt p4Kuudra
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class KuudraHider {

    private static final Set<Entity> HIDDEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Entity> NAMELESS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<MagmaCube> TENTACLES = new ArrayList<>();
    private static boolean active;
    private static boolean altDown;

    private KuudraHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraHider").when(() -> cfg().kuudraHider)
                .tick(KuudraHider::tick)
                .world(KuudraHider::render)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Kuudra");
                    b.toggle("Kuudra Hider", () -> c.kuudraHider, v -> c.kuudraHider = v)
                            .desc("Render hiders that only work on the Kuudra island");
                    b.toggle("  Hide Mob Tags", () -> c.kuudraHideTags, v -> c.kuudraHideTags = v)
                            .desc("Hides \"[Lv...\" mob tags, \"SNEAK + PUNCH\" and \"SUPPLY PILE\"; the pile progress stays");
                    b.toggle("  Hide Nameless Armor Stands", () -> c.kuudraHideArmorStands, v -> c.kuudraHideArmorStands = v)
                            .desc("Invisible armor stands without a name. Hold Alt to show them again");
                    b.toggle("  Hide Kuudra Bossbar", () -> c.kuudraHideBossBar, v -> c.kuudraHideBossBar = v);
                    b.toggle("  Tiny Kuudra Mobs", () -> c.kuudraTinyMobs, v -> c.kuudraTinyMobs = v)
                            .desc("Renders the zombies on the Kuudra island smaller (hitboxes stay the same)");
                    b.slider("    Size", 0.1f, 1.0f, () -> c.kuudraTinyMobsScale, v -> c.kuudraTinyMobsScale = v, 2);
                    b.toggle("  Tentacle Wireframes (P4)", () -> c.kuudraHideTentacles, v -> c.kuudraHideTentacles = v)
                            .desc("In Kuudra's lair: hides the big magma cubes and draws a box around them instead");
                    b.color("    Box Color", () -> c.kuudraTentacleColor, v -> c.kuudraTentacleColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        HIDDEN.clear();
        NAMELESS.clear();
        TENTACLES.clear();
        active = Kuudra.inKuudra() && mc.level != null;
        if (!active) return;
        altDown = mc.hasAltDown();
        TeslaMapsConfig c = cfg();
        boolean boss = c.kuudraHideTentacles && Kuudra.phase() == Kuudra.Phase.BOSS;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand as) {
                if (as.hasCustomName()) {
                    if (!c.kuudraHideTags) continue;
                    String n = StringUtil.stripTrim(as.getCustomName().getString());
                    if (n.startsWith("[Lv") || n.equals("SNEAK + PUNCH") || n.equals("SUPPLY PILE")) HIDDEN.add(as);
                } else if (c.kuudraHideArmorStands && as.isInvisible()) {
                    NAMELESS.add(as);
                }
            } else if (boss && e instanceof MagmaCube cube && cube.getBbWidth() > 5f) {
                HIDDEN.add(cube);
                TENTACLES.add(cube);
            }
        }
    }

    /** EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        if (!active || !cfg().kuudraHider) return false;
        return HIDDEN.contains(e) || !altDown && NAMELESS.contains(e);
    }

    /** BossBarHealthMixin. */
    public static boolean hideBossBar() {
        TeslaMapsConfig c = cfg();
        return c.kuudraHider && c.kuudraHideBossBar && Kuudra.inKuudra();
    }

    /** MobScaleMixin. */
    public static void scale(LivingEntityRenderState state, PoseStack pose) {
        if (state.entityType != EntityType.ZOMBIE || !active) return;
        TeslaMapsConfig c = cfg();
        if (!c.kuudraHider || !c.kuudraTinyMobs) return;
        float s = c.kuudraTinyMobsScale;
        pose.scale(s, s, s);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (TENTACLES.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().kuudraTentacleColor);
        for (MagmaCube cube : TENTACLES) {
            if (cube.isRemoved()) continue;
            ESPRenderer.drawBoxOutline(pose, ESPRenderer.lerpBox(cube), color, 2.0f, cam, true);
        }
    }
}
