package com.teslamaps.screen.nia;

// Ported from NiaMod's gui/theme/GuiTheme.java, GuiStyle.configuredTheme
record NiaTheme(int background, int secondary, int textColor, int secondaryText, int trinaryText,
                int overlay, int accentColor, int shadowColor, int sliderTrack, int scrollbarColor) {

    static final NiaTheme DEFAULT = new NiaTheme(0xFF171A21, 0xFF11141B, 0xFFFFFFFF, 0xDCFFFFFF, 0x82FFFFFF,
            0x26000000, 0xFF4794FD, 0x18000000, 0xFF171A21, 0x30FFFFFF);

    private static final float GUI_OPACITY = 0.9f;

    NiaTheme configured(double opacityMultiplier) {
        int alpha = Math.clamp((int) (GUI_OPACITY * 255 * opacityMultiplier), 0, 255);
        return new NiaTheme(
                (background & 0x00FFFFFF) | (alpha << 24),
                (secondary & 0x00FFFFFF) | (alpha << 24),
                textColor, secondaryText, trinaryText, overlay,
                (accentColor & 0x00FFFFFF) | 0xFF000000,
                shadowColor,
                (sliderTrack & 0x00FFFFFF) | (alpha << 24),
                scrollbarColor);
    }
}
