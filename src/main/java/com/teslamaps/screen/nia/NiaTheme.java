package com.teslamaps.screen.nia;

import com.teslamaps.config.TeslaMapsConfig;

// Ported from NiaMod's gui/theme/GuiTheme.java, ThemeOption.java, GuiStyle.configuredTheme
record NiaTheme(int background, int secondary, int textColor, int secondaryText, int trinaryText,
                int overlay, int accentColor, int shadowColor, int sliderTrack, int scrollbarColor) {

    static final NiaTheme DEFAULT = new NiaTheme(0xFF171A21, 0xFF11141B, 0xFFFFFFFF, 0xDCFFFFFF, 0x82FFFFFF,
            0x26000000, 0xFF4794FD, 0x18000000, 0xFF171A21, 0x30FFFFFF);
    static final NiaTheme OCEAN = new NiaTheme(0xFF0F172A, 0xFF1E293B, 0xFFF8FAFC, 0xFFCBD5E1, 0xFF94A3B8,
            0x26000000, 0xFF0EA5E9, 0x18000000, 0xFF0F172A, 0x30FFFFFF);
    static final NiaTheme CHERRY = new NiaTheme(0xFF2D1B2E, 0xFF3D263F, 0xFFFDF2F8, 0xFFFBCFE8, 0xFFF472B6,
            0x26000000, 0xFFEC4899, 0x18000000, 0xFF2D1B2E, 0x30FFFFFF);

    static final String[] NAMES = {"Default Dark", "Ocean", "Cherry Blossom", "Custom Colors"};
    static final String[] FONTS = {"Adwaita Sans", "Minecraft Default", "Minecraft Uniform"};

    static NiaTheme base(TeslaMapsConfig c) {
        return switch (c.niaTheme == null ? "" : c.niaTheme) {
            case "Ocean" -> OCEAN;
            case "Cherry Blossom" -> CHERRY;
            case "Custom Colors" -> {
                int bg = TeslaMapsConfig.parseColor(c.niaCustomBg);
                yield new NiaTheme(bg, TeslaMapsConfig.parseColor(c.niaCustomSecondary), 0xFFFFFFFF, 0xDCFFFFFF,
                        0x82FFFFFF, 0x26000000, TeslaMapsConfig.parseColor(c.niaCustomAccent), 0x18000000, bg, 0x30FFFFFF);
            }
            default -> DEFAULT;
        };
    }

    NiaTheme configured(float opacity, double opacityMultiplier) {
        int alpha = Math.clamp((int) (opacity * 255 * opacityMultiplier), 0, 255);
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
