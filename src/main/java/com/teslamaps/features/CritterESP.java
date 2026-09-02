/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CritterESP {

    public static final String AREA = "Torrhus Canyon";

    private static final List<Box> boxes = new ArrayList<>();
    private static final List<Cluster> spots = new ArrayList<>();

    private record Box(AABB box, int color) {}

    public static boolean inArea() {
        String a = SkyblockUtils.getArea().toLowerCase();
        return a.contains("torrhus") || a.contains("safari");
    }

    public static boolean active() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.critterESP && !LegitMode.blocksCheats() && inArea();
    }

    public static void tick() {
        boxes.clear();
        spots.clear();
        if (!inArea() || LegitMode.blocksCheats()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.critterESP) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof ArmorStand) continue;
                String col = colorFor(e, c);
                if (col == null) continue;
                boxes.add(new Box(e.getBoundingBox(), TeslaMapsConfig.parseColor(col) | 0xFF000000));
            }
        }
        if (c.critterSpotESP) scanSpots(mc);
    }

    // the floor spots are clusters of item_display entities holding a re-modelled string item
    private static void scanSpots(Minecraft mc) {
        List<Vec3> found = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Display.ItemDisplay id)) continue;
            SlotAccess slot = id.getSlot(0);
            if (slot == null || slot.get().getItem() != Items.STRING) continue;
            found.add(e.position());
        }
        // one box per cluster, not per display (a spot is 3-4 displays within ~2 blocks)
        for (Vec3 p : found) {
            boolean merged = false;
            for (int i = 0; i < spots.size(); i++) {
                Cluster cl = spots.get(i);
                if (cl.center().distanceToSqr(p) < 9) {
                    spots.set(i, cl.add(p));
                    merged = true;
                    break;
                }
            }
            if (!merged) spots.add(new Cluster(p, 1));
        }
    }

    private record Cluster(Vec3 sum, int n) {
        Vec3 center() { return sum.scale(1.0 / n); }
        Cluster add(Vec3 p) { return new Cluster(sum.add(p), n + 1); }
    }

    public static List<Vec3> spotCenters() {
        List<Vec3> out = new ArrayList<>();
        for (Cluster c : spots) out.add(c.center());
        return out;
    }

    private static String colorFor(Entity e, TeslaMapsConfig c) {
        if (c.critterBee && e instanceof Bee) return c.colorCritterBee;
        if (c.critterBlueJay && e instanceof Parrot) return c.colorCritterBlueJay;
        if (c.critterBunbun && e instanceof Rabbit) return c.colorCritterBunbun;
        if (c.critterDrybark && e instanceof Creaking) return c.colorCritterDrybark;
        if (c.critterFirefox && e instanceof Fox) return c.colorCritterFirefox;
        if (c.critterGroundhog && e instanceof Hoglin) return c.colorCritterGroundhog;
        if (c.critterMountainGoat && e instanceof Goat) return c.colorCritterMountainGoat;
        if (c.critterSepialot && e instanceof Axolotl) return c.colorCritterSepialot;
        if (c.critterDustybit && e instanceof Frog) return c.colorCritterDustybit;
        if (c.critterPuck && e instanceof Vex) return c.colorCritterPuck;
        if (c.critterGrizzly && named(e, "Grizzly")) return c.colorCritterGrizzly;
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
        }

        if (spots.isEmpty()) return;
        int col = TeslaMapsConfig.parseColor(c.colorCritterSpot) | 0xFF000000;
        for (Cluster cl : spots) {
            Vec3 p = cl.center();
            AABB box = new AABB(p.x - 0.5, p.y - 0.05, p.z - 0.5, p.x + 0.5, p.y + 0.5, p.z + 0.5);
            if (filled) ESPRenderer.drawFilledBox(matrices, box, col & 0x00FFFFFF, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, box, col, 2.0f, cameraPos, true);
        }
    }
}
