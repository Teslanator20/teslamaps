package com.teslamaps.profileviewer.screen;

import com.mojang.authlib.GameProfile;
import com.teslamaps.mixin.InventoryScreenInvoker;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * The player NEU shows on its first page: a real entity render, so every skin layer, the
 * armor and the cape show up. Same approach the customize screen already uses.
 */
public class PvPlayerRender {
    private static final float FLIP = (float) Math.PI;

    private static String cachedUuid;
    private static RemotePlayer player;

    public static void reset() {
        cachedUuid = null;
        player = null;
    }

    private static RemotePlayer player(String uuid, String name) {
        if (player != null && uuid.equals(cachedUuid)) return player;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        GameProfile profile = new GameProfile(UUID.fromString(com.teslamaps.api.PvApi.dashed(uuid)), name);
        player = new RemotePlayer(mc.level, profile) {
            @Override
            public boolean isInvisibleTo(Player other) {
                return true;
            }

            @Override
            public boolean isModelPartShown(PlayerModelPart part) {
                return true;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        player.yHeadRot = player.yHeadRotO = 0;
        cachedUuid = uuid;

        // put their teslamaps cape on the render, same as in the world
        com.teslamaps.features.capes.CapeSync.setPreview(player.getId(),
                com.teslamaps.features.capes.CapeSync.capeFor(uuid));
        return player;
    }

    /** Puts the profile's armor on the render. Boots first, like hypixel stores it. */
    public static void setArmor(String uuid, String name, List<InventoryDecoder.Slot> armor) {
        RemotePlayer target = player(uuid, name);
        if (target == null || armor.isEmpty()) return;

        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < armor.size() ? ItemGrid.stackFor(armor.get(i)) : ItemStack.EMPTY;
            target.setItemSlot(slots[i], stack);
        }
    }

    public static void draw(GuiGraphicsExtractor ctx, String uuid, String name,
                            int x1, int y1, int x2, int y2, float yaw, float pitch) {
        RemotePlayer target = player(uuid, name);
        if (target == null) return;

        float size = (y2 - y1) / 2.4f;
        Vector3f translation = new Vector3f(0, target.getBbHeight() / 2f + 0.0625f, 0);
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                -Mth.clamp(pitch, -50f, 50f) * Mth.DEG_TO_RAD, -yaw * Mth.DEG_TO_RAD, FLIP);

        EntityRenderState state = InventoryScreenInvoker.invokeExtractRenderState(target);
        ctx.entity(state, size, translation, rotation, null, x1, y1, x2, y2);
    }
}
