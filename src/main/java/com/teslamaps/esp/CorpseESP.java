package com.teslamaps.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Box + beacon on unlooted Glacite Mineshaft corpses. A corpse is an armor stand wearing one of the
// known corpse helmets. Looted ones are dropped from the chat message, since the armor stand can
// linger for a while afterwards. Always drawn through walls.
public class CorpseESP {

    private static final Set<String> CORPSE_HELMETS = Set.of(
            "LAPIS_ARMOR_HELMET", "MINERAL_HELMET", "ARMOR_OF_YOG_HELMET", "YOG_HELMET", "VANGUARD_HELMET");

    // helmet item -> corpse type colour (lapis blue, umber orange, tungsten grey)
    private static final Map<net.minecraft.world.item.Item, Integer> TYPE_COLORS = Map.of(
            Items.SEA_LANTERN, 0x0000AA,
            Items.LEATHER_HELMET, 0xFFAA00,
            Items.PLAYER_HEAD, 0xAAAAAA);

    private static final Map<UUID, Vec3> seen = new HashMap<>();
    private static final Set<UUID> looted = new HashSet<>();
    private static ClientLevel lastLevel;

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.corpseESP) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
            looted.clear();
        }
        if (!inMineshaft()) return;

        int fallback = TeslaMapsConfig.parseColor(c.colorCorpseESP);

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand as)) continue;
            ItemStack helmet = as.getItemBySlot(EquipmentSlot.HEAD);
            if (!CORPSE_HELMETS.contains(ItemUtil.skyblockId(helmet))) continue;

            seen.put(as.getUUID(), as.position());
            if (looted.contains(as.getUUID())) continue;

            int color = fallback;
            if (c.corpseESPTypeColors) {
                Integer typed = TYPE_COLORS.get(helmet.getItem());
                if (typed != null) color = typed;
            }

            AABB box = new AABB(as.getX() - 0.4, as.getY(), as.getZ() - 0.4,
                    as.getX() + 0.4, as.getY() + 2.0, as.getZ() + 0.4);
            ESPRenderer.drawFilledBox(matrices, box, color, cameraPos, true);
            ESPRenderer.drawBeaconBeam(matrices, as.blockPosition(), color, cameraPos);
        }
    }

    // "    LAPIS CORPSE LOOT!    " - marks the corpse the player is standing at as done
    public static void onChatMessage(String msg) {
        if (!TeslaMapsConfig.get().corpseESP || seen.isEmpty()) return;
        String clean = msg.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        if (!clean.equals("LAPIS CORPSE LOOT!") && !clean.equals("UMBER CORPSE LOOT!")
                && !clean.equals("TUNGSTEN CORPSE LOOT!")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();

        UUID nearest = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<UUID, Vec3> entry : seen.entrySet()) {
            double d = entry.getValue().distanceToSqr(pos);
            if (d < best) {
                best = d;
                nearest = entry.getKey();
            }
        }
        if (nearest != null) looted.add(nearest);
    }

    private static boolean mineshaft = false;
    private static long mineshaftTime = 0;

    private static boolean inMineshaft() {
        long now = System.currentTimeMillis();
        if (now - mineshaftTime < 500) return mineshaft;
        mineshaftTime = now;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(line).contains("Mineshaft")) return mineshaft = true;
        }
        return mineshaft = false;
    }
}
