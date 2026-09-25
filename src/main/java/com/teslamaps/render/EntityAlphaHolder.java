package com.teslamaps.render;

/** Added to LivingEntityRenderState: body alpha 0-255, 255 = vanilla. */
public interface EntityAlphaHolder {
    int teslamaps$alpha();

    void teslamaps$setAlpha(int alpha);
}
