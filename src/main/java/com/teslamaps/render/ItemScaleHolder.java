package com.teslamaps.render;

/** Added to ItemEntityRenderState: dropped item scale, 1 = vanilla. */
public interface ItemScaleHolder {
    float teslamaps$itemScale();

    void teslamaps$setItemScale(float scale);
}
