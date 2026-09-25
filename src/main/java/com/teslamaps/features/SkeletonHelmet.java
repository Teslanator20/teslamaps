package com.teslamaps.features;

// Ported from SkyblockAddons (HIDE_BONES, checkIfWearingSkeletonHelmet, drawSkeletonBar)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class SkeletonHelmet {

    private static HudEditElement hud;
    private static ItemStack bone;
    private static boolean wearing;
    private static int bones, ticks;
    private static Set<Entity> hidden = Set.of();

    private SkeletonHelmet() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("skeletonBar", "Skeleton's Helmet Bones")
                .enabled(() -> cfg().skeletonHelmetBar)
                .size(() -> 3 * 16, () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, x, y, s, 3));
        Feature.of("SkeletonHelmet").when(() -> cfg().skeletonHelmetHideBones || cfg().skeletonHelmetBar)
                .tick(SkeletonHelmet::tick)
                .hudHiddenInInventory("skeletonbar", SkeletonHelmet::render)
                .config("Interface", b -> {
                    b.label("Skeleton's Helmet");
                    b.toggle("Hide Orbiting Bones", () -> cfg().skeletonHelmetHideBones, v -> cfg().skeletonHelmetHideBones = v)
                            .desc("Hides the bones circling you while you wear a Skeleton's Helmet").tags("bone", "skeleton");
                    b.toggle("Skeleton's Helmet Bone Bar", () -> cfg().skeletonHelmetBar, v -> cfg().skeletonHelmetBar = v)
                            .desc("Bone icons for the bones left around you (/tmap gui)").tags("bone", "skeleton");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 2 != 0) return;
        if (ticks % 10 == 0) {
            wearing = SkyblockUtils.isInSkyblock()
                    && "SKELETON_HELMET".equals(ItemUtil.skyblockId(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        }
        if (!wearing) {
            bones = 0;
            hidden = Set.of();
            return;
        }
        Set<Entity> found = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(8))) {
            if (isBone(e)) found.add(e);
        }
        bones = found.size();
        hidden = found;
    }

    private static boolean isBone(Entity e) {
        if (e instanceof ItemEntity item) {
            return item.getItem().getItem() == Items.BONE && item.getVehicle() instanceof ArmorStand s && s.isInvisible();
        }
        if (e instanceof ArmorStand s && s.isInvisible()) {
            return s.getMainHandItem().getItem() == Items.BONE || s.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.BONE;
        }
        return false;
    }

    public static boolean shouldHide(Entity e) {
        return wearing && !hidden.isEmpty() && cfg().skeletonHelmetHideBones && hidden.contains(e);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().skeletonHelmetBar || !wearing || hud == null || mc.options.hideGui) return;
        draw(g, hud.x(), hud.y(), hud.scale(), bones);
    }

    private static void draw(GuiGraphicsExtractor g, int x, int y, float scale, int n) {
        if (bone == null) bone = new ItemStack(Items.BONE);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        for (int i = 0; i < Math.min(n, 10); i++) g.item(bone, i * 16, 0);
        g.pose().popMatrix();
    }
}
