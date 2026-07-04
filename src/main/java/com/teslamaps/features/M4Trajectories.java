/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file ports the Trajectories feature from BirdAddon
 * (https://github.com/BarefootBird/BirdAddon, BSD 3-Clause). See NOTICE.md.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.M4Mobs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Predicts where the launched cows/sheep land (and the chickens' ground spot) in M4. Ported from BirdAddon.
public class M4Trajectories {
    private static final List<Vec3> cowSpots = new ArrayList<>();
    private static final List<Vec3> sheepSpots = new ArrayList<>();
    private static final List<Vec3> chickenSpots = new ArrayList<>();

    public static void tick() {
        cowSpots.clear(); sheepSpots.clear(); chickenSpots.clear();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.m4Trajectories || !M4Mobs.inM4Boss()) return;

        if (c.m4TrajCow) for (Entity e : M4Mobs.cows) if (e.getY() > 71.5) addLanding(cowSpots, e);
        if (c.m4TrajSheep) for (Entity e : M4Mobs.sheep) if (e.getY() > 71.5) addLanding(sheepSpots, e);
        if (c.m4TrajChicken) for (Entity e : M4Mobs.chickens) chickenSpots.add(new Vec3(e.getX(), groundY(e.getX(), e.getZ()), e.getZ()));
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.m4Trajectories || !M4Mobs.inM4Boss()) return;
        int style = M4Highlight.styleOf(c.m4HighlightStyle);
        for (Vec3 s : cowSpots) marker(matrices, s, TeslaMapsConfig.parseColor(c.m4ColorCow), style, cameraPos);
        for (Vec3 s : sheepSpots) marker(matrices, s, TeslaMapsConfig.parseColor(c.m4ColorSheep), style, cameraPos);
        for (Vec3 s : chickenSpots) marker(matrices, s, TeslaMapsConfig.parseColor(c.m4ColorChicken), style, cameraPos);
    }

    private static void marker(PoseStack m, Vec3 s, int color, int style, Vec3 cam) {
        M4Highlight.drawStyled(m, new AABB(s.x - 0.3, s.y, s.z - 0.3, s.x + 0.3, s.y + 0.05, s.z + 0.3), color, style, cam);
    }

    private static void addLanding(List<Vec3> out, Entity e) {
        Vec3 l = predict(e.position(), e.getDeltaMovement());
        if (l != null) out.add(l);
    }

    // Minecraft projectile-ish physics; assumes the floor is y=69 (true except beef-on-leaf).
    private static Vec3 predict(Vec3 pos, Vec3 vel) {
        double x = pos.x, y = pos.y, z = pos.z;
        double vx = vel.x, vy = vel.y, vz = vel.z;
        for (int i = 0; i < 100; i++) {
            x += vx; y += vy; z += vz;
            vx *= 0.98; vz *= 0.98; vy = (vy - 0.08) * 0.98;
            if (y <= 69.0) return new Vec3(x, 69.0, z);
        }
        return null;
    }

    private static double groundY(double x, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 69.0;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int y = 100; y >= 69; y--) {
            var state = mc.level.getBlockState(new BlockPos(bx, y, bz));
            if (!state.isAir() && state.isSolid()) return y + 1.0;
        }
        return 69.0;
    }

    public static void reset() {
        cowSpots.clear(); sheepSpots.clear(); chickenSpots.clear();
    }
}
