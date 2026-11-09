package com.teslamaps.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

public interface MultiBufferSource {

    VertexConsumer getBuffer(RenderType type);

    interface BufferSource extends MultiBufferSource {
        void endBatch(RenderType type);

        void endBatch();
    }
}
