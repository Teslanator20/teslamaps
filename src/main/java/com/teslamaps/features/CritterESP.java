package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CritterESP {

    public static final String AREA = "Torrhus Canyon";

    private static final List<Box> boxes = new ArrayList<>();

    private record Box(AABB box, int color, boolean tracer) {}

    /** The canyon only - the Safari itself is handled by com.teslamaps.safari. */
    public static boolean inArea() {
        String a = SkyblockUtils.getArea().toLowerCase();
        return a.contains("torrhus") && !a.contains("safari");
    }

    public static boolean active() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.critterESP && !LegitMode.blocksCheats() && inArea();
    }

    public static void tick() {
        boxes.clear();
        if (!inArea() || LegitMode.blocksCheats()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.critterESP) {
            for (Entity e : com.teslamaps.utils.EntityCache.all()) {
                if (e instanceof ArmorStand || e == mc.player) continue;
                String col = colorFor(e, c);
                if (col == null) continue;
                boxes.add(new Box(e.getBoundingBox(), TeslaMapsConfig.parseColor(col) | 0xFF000000,
                        c.critterTracers));
            }
        }
    }

    private static String colorFor(Entity e, TeslaMapsConfig c) {
        if (c.critterBee && e instanceof Bee) return c.colorCritterBee;
        if (c.critterBlueJay && e instanceof Parrot) return c.colorCritterBlueJay;
        if (c.critterBunbun && e instanceof Rabbit) return c.colorCritterBunbun;
        if (c.critterDrybark && e instanceof Creaking) return c.colorCritterDrybark;
        if (c.critterFirefox && e instanceof Fox) return c.colorCritterFirefox;
        if (c.critterGroundhog && e instanceof Hoglin) return c.colorCritterGroundhog;
        if (c.critterHideonsun && e instanceof Shulker) return c.colorCritterHideonsun;
        if (c.critterMountainGoat && e instanceof Goat) return c.colorCritterMountainGoat;
        if (c.critterSepialot && e instanceof Axolotl) return c.colorCritterSepialot;
        if (c.critterDustybit && e instanceof Frog) return c.colorCritterDustybit;
        if (c.critterPuck && e instanceof Vex) return c.colorCritterPuck;
        if (c.critterGrizzly && e instanceof net.minecraft.world.entity.Mob && named(e, "Grizzly")) return c.colorCritterGrizzly;
        return null;
    }

    // skyblock mobs often carry the display name on an armor stand riding/above them
    private static boolean named(Entity e, String needle) {
        if (e.getName().getString().contains(needle)) return true;
        if (e.getCustomName() != null && e.getCustomName().getString().contains(needle)) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(1, 3, 1), a -> true)) {
            if (as.getName().getString().contains(needle)) return true;
            if (as.getCustomName() != null && as.getCustomName().getString().contains(needle)) return true;
        }
        return false;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean filled = c.critterFilled;
        for (Box b : boxes) {
            if (filled) ESPRenderer.drawFilledBox(matrices, b.box(), b.color() & 0x00FFFFFF, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, b.box(), b.color(), 2.0f, cameraPos, true);
            if (b.tracer()) {
                ESPRenderer.drawTracerFromCamera(matrices, b.box().getCenter(), b.color(), cameraPos);
            }
        }
    }
}
