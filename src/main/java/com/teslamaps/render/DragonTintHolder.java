package com.teslamaps.render;

/** Added to EnderDragonRenderState: overlay packed by DragonRecolor, -1 = vanilla. */
public interface DragonTintHolder {
    // WitherDragons.Dragon order: red, orange, green, blue, purple. Alpha = share of the original texture.
    int[] TINTS = {0x80FF5555, 0x80FFAA00, 0x8055FF55, 0x8055FFFF, 0x80AA00AA};
    int TINT_U = 16 - 1 - 5;
    int TINT_V = 12;

    int teslamaps$overlay();

    void teslamaps$setOverlay(int overlay);
}
