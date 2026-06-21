/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatWaypoint {

    private static final Pattern COORDS =
            Pattern.compile("x:\\s*(-?\\d+)[,.]?\\s*y:\\s*(-?\\d+)[,.]?\\s*z:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final long DURATION_MS = 60_000;

    private static final class WP { final BlockPos pos; final long until; WP(BlockPos p, long u) { pos = p; until = u; } }
    private static final List<WP> waypoints = new ArrayList<>();

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().chatWaypoint) return;
        String t = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        Matcher m = COORDS.matcher(t);
        if (!m.find()) return;
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            waypoints.add(new WP(pos, System.currentTimeMillis() + DURATION_MS));
        } catch (NumberFormatException ignored) {}
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().chatWaypoint || waypoints.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<WP> it = waypoints.iterator(); it.hasNext(); ) {
            WP w = it.next();
            if (now >= w.until) { it.remove(); continue; }
            ESPRenderer.drawBeaconBeam(matrices, w.pos, 0xAA00FFFF, cameraPos);
            Vec3 txt = new Vec3(w.pos.getX() + 0.5, w.pos.getY() + 1.5, w.pos.getZ() + 0.5);
            ESPRenderer.drawText(matrices, "§b" + w.pos.getX() + " " + w.pos.getY() + " " + w.pos.getZ(), txt, 1.0f, cameraPos);
        }
    }

    public static void reset() { waypoints.clear(); }
}
