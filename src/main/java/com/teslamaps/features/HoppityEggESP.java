/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Egg detection (armor stand + skull texture matching) is adapted from
 * Skyblocker (https://github.com/SkyblockerMod/Skyblocker, LGPL-3.0).
 * See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Hoppity's Hunt chocolate egg ESP: eggs are invisible armor stands wearing a
// known egg skull. Texture hashes from Skyblocker's EggFinder.
public class HoppityEggESP {

    private record EggType(String name, String hash, int color) {}

    private static final EggType[] TYPES = {
        new EggType("Breakfast", "a49333d85b8a315d0336eb2df37d8a714ca24c51b8c6074f1b5b927deb516c24", 0xFFFFAA00),
        new EggType("Lunch", "7ae6d2d31d8167bcaf95293b68a4acd872d66e751db5a34f2cbc6766a0356d0a", 0xFF5555FF),
        new EggType("Dinner", "e5e36165819fd2850f98552edcd763ff986313119283c126ace0c4cc495e76a8", 0xFF55FF55),
    };

    private record Found(ArmorStand entity, EggType type) {}

    private static final List<Found> found = new ArrayList<>();
    private static final java.util.Set<net.minecraft.core.BlockPos> collected = new java.util.HashSet<>();
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> lastLevel = new java.lang.ref.WeakReference<>(null);
    private static int tickCounter = 0;

    public static void tick() {
        if (!TeslaMapsConfig.get().hoppityEggESP) return;
        if (++tickCounter % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.level != lastLevel.get()) {
            collected.clear();
            lastLevel = new java.lang.ref.WeakReference<>(mc.level);
        }
        if (com.teslamaps.dungeon.DungeonManager.isInDungeon()) {
            found.clear();
            return;
        }

        found.clear();
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class,
                mc.player.getBoundingBox().inflate(128, 128, 128))) {
            if (stand.hasCustomName() || !stand.isInvisible() || stand.showBasePlate()) continue;
            EggType type = matchEgg(stand.getItemBySlot(EquipmentSlot.HEAD));
            if (type != null) found.add(new Found(stand, type));
        }
    }

    private static EggType matchEgg(ItemStack head) {
        if (head == null || head.isEmpty()) return null;
        ResolvableProfile rp = head.get(DataComponents.PROFILE);
        if (rp == null) return null;
        Collection<Property> props = rp.partialProfile().properties().get("textures");
        if (props.isEmpty()) return null;
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(props.iterator().next().value()));
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (EggType type : TYPES) {
            if (decoded.contains(type.hash)) return type;
        }
        return null;
    }

    // hide the waypoint once the egg was clicked ("found" / "already collected" chat)
    public static void onChatMessage(String raw) {
        if (!TeslaMapsConfig.get().hoppityEggESP) return;
        String text = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (!text.startsWith("HOPPITY'S HUNT You found a Chocolate")
                && !text.startsWith("You have already collected this Chocolate")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 p = mc.player.position();
        for (Found f : found) {
            if (f.entity.position().distanceTo(p) < 8) collected.add(f.entity.blockPosition());
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.hoppityEggESP || found.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 playerPos = mc.player != null ? mc.player.position() : cameraPos;
        for (Found f : found) {
            if (!f.entity.isAlive()) continue;
            if (collected.contains(f.entity.blockPosition())) continue;
            Vec3 pos = f.entity.position();
            if (pos.distanceTo(playerPos) < 5) continue; // close enough, stop cluttering the screen
            AABB box = new AABB(pos.x - 0.4, pos.y + 1.1, pos.z - 0.4,
                    pos.x + 0.4, pos.y + 1.9, pos.z + 0.4);
            ESPRenderer.drawESPBox(matrices, box, f.type.color, cameraPos);
            ESPRenderer.drawText(matrices, colorCode(f.type) + f.type.name + " Egg",
                    pos.add(0, 2.4, 0), 1.5f, cameraPos);
            if (cfg.hoppityEggTracers) {
                ESPRenderer.drawTracerFromCamera(matrices, box.getCenter(), f.type.color, cameraPos);
            }
        }
    }

    private static String colorCode(EggType type) {
        return switch (type.name) {
            case "Breakfast" -> "§6";
            case "Lunch" -> "§9";
            default -> "§a";
        };
    }
}
