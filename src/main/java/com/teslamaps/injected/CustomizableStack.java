package com.teslamaps.injected;

/** Implemented on ItemStack by ItemStackUuidMixin. */
public interface CustomizableStack {
    /** Hypixel's per-item uuid, cached on the stack. Empty string when the item has none. */
    String teslamaps$uuid();
}
