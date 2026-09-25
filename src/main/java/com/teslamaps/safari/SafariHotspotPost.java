package com.teslamaps.safari;

// Ported from critterMod's client/HotspotWatch.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;

public final class SafariHotspotPost {

    private SafariHotspotPost() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SafariHotspotPost").section("Fishing").when(() -> cfg().safariHotspotPost)
                .serverChat(SafariHotspotPost::onChat);
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("HOTSPOT! Your Hunting Hotspot")) return;
        Matcher m = SafariTracker.HOTSPOT.matcher(t);
        if (!m.matches()) return;
        SafariData.Biome biome = null;
        for (SafariData.Biome b : SafariData.Biome.values()) if (b.label.equalsIgnoreCase(m.group(1))) biome = b;
        if (biome == null) return;
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Your hotspot: " + biome.color + biome.label));
        Announce.send(cfg().safariHotspotPostChannel, "My hotspot: " + biome.label);
    }
}
