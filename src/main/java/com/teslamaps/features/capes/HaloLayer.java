package com.teslamaps.features.capes;

// Ported from NoammAddons' cosmetics/halo/HaloLayer.kt + HaloModel.kt
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HaloLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final int[][] PIXELS = {
            {-2, -6}, {1, 5}, {0, 5}, {-1, 5}, {-2, 5}, {-2, 4}, {-3, 4}, {-4, 4}, {-4, 3}, {-5, 3}, {-5, 2}, {-5, 1},
            {-6, 1}, {-6, -2}, {-6, -1}, {1, 4}, {2, 4}, {3, 4}, {3, 3}, {4, 3}, {4, 2}, {4, 1}, {5, 1}, {5, 0}, {5, -1},
            {5, -2}, {-6, 0}, {-5, -2}, {-5, -3}, {-5, -4}, {-4, -4}, {-4, -5}, {-3, -5}, {-2, -5}, {4, -2},
            {4, -3}, {4, -4}, {3, -4}, {3, -5}, {2, -5}, {1, -5}, {1, -6}, {0, -6}, {-1, -6},
    };
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("teslamaps", "halo_white");
    private static boolean textureReady;

    private final HaloModel model = HaloModel.create();

    public HaloLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Cosmetics.Look look = Cosmetics.forId(state.id);
        if (look == null || !look.hasHalo() || state.isInvisible) return;
        if (!textureReady) {
            NativeImage img = new NativeImage(1, 1, false);
            img.setPixel(0, 0, -1);
            Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "TeslaMaps halo", img));
            textureReady = true;
        }
        float bob = (float) (Math.sin(System.currentTimeMillis() % 2000L / 2000.0 * Math.PI * 2) + 1) / 2f * 0.08f;
        float offset = state.isCrouching ? 0.5f : 0.7f;
        pose.pushPose();
        pose.translate(0f, -offset - bob * 2f, 0f);
        collector.submitModel(model, state, pose, RenderTypes.entitySolid(WHITE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | look.halo(), null, 0, null);
        pose.popPose();
    }

    static class HaloModel extends EntityModel<AvatarRenderState> {
        HaloModel(ModelPart root) {
            super(root);
        }

        static HaloModel create() {
            CubeListBuilder builder = CubeListBuilder.create();
            for (int[] p : PIXELS) builder.addBox(p[0], 0f, p[1], 1f, 1f, 1f);
            List<ModelPart.Cube> cubes = new ArrayList<>();
            for (CubeDefinition d : builder.getCubes()) cubes.add(d.bake(16, 16));
            ModelPart halo = new ModelPart(cubes, Map.of());
            return new HaloModel(new ModelPart(List.of(), Map.of("halo", halo)));
        }
    }
}
