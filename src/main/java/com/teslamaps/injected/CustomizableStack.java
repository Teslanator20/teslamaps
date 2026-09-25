package com.teslamaps.injected;

/** Implemented on ItemStack by ItemStackUuidMixin. */
public interface CustomizableStack {
    /** Hypixel's per-item uuid, cached on the stack. Empty string when the item has none. */
    String teslamaps$uuid();

    /** Customization key: the uuid, or id(+timestamp) for uuid-less items when enabled. */
    String teslamaps$customKey();

    /** SkyBlock item id, cached until CUSTOM_DATA changes. Empty when none. */
    String teslamaps$skyblockId();

    /** Lore lines with § codes stripped, cached until LORE changes. Immutable. */
    java.util.List<String> teslamaps$loreLines();
}
