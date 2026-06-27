/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Corpse-detection approach (armor stand holding a corpse helmet by SkyBlock id,
 * gated to Glacite Mineshafts) references MiningQOL (github.com/Rinity9801/MiningQOL).
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Box + beacon on unlooted Glacite Mineshaft corpses. A corpse is an armor stand wearing one of the
// known corpse helmets; once looted the armor stand despawns, so present = unlooted. Depth Check off = through walls.
public class CorpseESP {

    private static final Set<String> CORPSE_HELMETS = Set.of(
            "LAPIS_ARMOR_HELMET", "MINERAL_HELMET", "ARMOR_OF_YOG_HELMET", "YOG_HELMET", "VANGUARD_HELMET");

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.corpseESP) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!inMineshaft()) return;

        int color = TeslaMapsConfig.parseColor(c.colorCorpseESP);
        boolean throughWalls = !c.corpseESPDepthCheck;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand as)) continue;
            ItemStack helmet = as.getItemBySlot(EquipmentSlot.HEAD);
            if (!CORPSE_HELMETS.contains(ItemUtil.skyblockId(helmet))) continue;

            AABB box = new AABB(as.getX() - 0.4, as.getY(), as.getZ() - 0.4,
                    as.getX() + 0.4, as.getY() + 2.0, as.getZ() + 0.4);
            ESPRenderer.drawFilledBox(matrices, box, color, cameraPos, throughWalls);
            ESPRenderer.drawBeaconBeam(matrices, as.blockPosition(), color, cameraPos);
        }
    }

    private static boolean inMineshaft() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(line).contains("Mineshaft")) return true;
        }
        return false;
    }
}
