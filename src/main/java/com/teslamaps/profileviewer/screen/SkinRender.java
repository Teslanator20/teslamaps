package com.teslamaps.profileviewer.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;

/**
 * The rotating player NEU shows on its basic page. GuiGraphicsExtractor#skin takes a baked
 * player model plus the skin texture, so no fake entity is needed.
 */
public class SkinRender {
    private static PlayerModel wide;
    private static PlayerModel slim;

    private static PlayerModel model(boolean thin) {
        Minecraft mc = Minecraft.getInstance();
        if (thin) {
            if (slim == null) slim = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }

    /** x1/y1..x2/y2 is the box the player is drawn into; yaw and pitch are in degrees. */
    public static void draw(GuiGraphicsExtractor ctx, Identifier texture, boolean thin,
                            float yaw, float pitch, int x1, int y1, int x2, int y2) {
        ctx.skin(model(thin), texture, yaw, pitch, 0f, 0f, x1, y1, x2, y2);
    }
}
