/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * Bundled fonts are OFL-licensed (see NOTICE.md). See the LICENSE file for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets the user swap the default UI font. The StyleFontMixin redirects the vanilla default font
 * (used by virtually all text) to the chosen bundled font when {@code customFont} is on.
 */
public class CustomFont {
    // display name -> font definition (assets/teslamaps/font/<value>.json); "Default" = no override
    public static final Map<String, String> FONTS = new LinkedHashMap<>();
    static {
        FONTS.put("Default", null);
        FONTS.put("Press Start 2P", "pressstart");
        FONTS.put("VT323", "vt323");
        FONTS.put("Poppins", "poppins");
        FONTS.put("Lato", "lato");
        FONTS.put("Pacifico", "pacifico");
        FONTS.put("Bangers", "bangers");
    }

    public static String[] names() { return FONTS.keySet().toArray(new String[0]); }

    private static String cachedKey = null;
    private static FontDescription cachedDesc = null;

    /** Returns the replacement font for the vanilla default, or null to leave it unchanged. */
    public static FontDescription override(FontDescription current) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.customFont) return null;
        if (!(current instanceof FontDescription.Resource r)) return null;
        Identifier id = r.id();
        if (!"minecraft".equals(id.getNamespace()) || !"default".equals(id.getPath())) return null; // only the default font

        String key = c.guiFont;
        String file = key == null ? null : FONTS.get(key);
        if (file == null) return null; // "Default" or unknown -> no override

        if (!key.equals(cachedKey)) {
            cachedKey = key;
            cachedDesc = new FontDescription.Resource(Identifier.fromNamespaceAndPath("teslamaps", file));
        }
        return cachedDesc;
    }
}
