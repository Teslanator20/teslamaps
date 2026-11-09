package com.teslamaps.features;

// Ported from SkyHanni's TeleportPadCompactName.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Optional;

public class TeleportPads {

    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TeleportPads").section("Render")
                .when(() -> cfg().teleportPadCompactName)
                .tick(TeleportPads::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Private Island Teleport Pads");
                    b.toggle("Compact Pad Names", () -> c.teleportPadCompactName, v -> c.teleportPadCompactName = v)
                            .desc("\"Warp To X\" becomes \"X\", \"No Destination\" tags are hidden.")
                            .tags("teleport", "pad", "island");
                });
    }

    private static void tick() {
        if (++ticks % 10 == 0 && SkyblockUtils.isInArea("Private Island")) compactNames();
    }

    private static void compactNames() {
        for (ArmorStand stand : EntityCache.namedArmorStands()) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            String plain = StringUtil.stripTrim(name.getString());
            if (plain.equals("✦ No Destination")) {
                if (stand.isCustomNameVisible()) stand.setCustomNameVisible(false);
            } else if (plain.startsWith("✦ Warp To ")) {
                Component cut = after(name, "Warp To ");
                if (cut != null) stand.setCustomName(cut);
            }
        }
    }

    // rebuild the styled tail of a component starting after the marker text
    private static Component after(Component name, String marker) {
        StringBuilder raw = new StringBuilder();
        name.visit((style, text) -> {
            raw.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        int idx = raw.indexOf(marker);
        if (idx < 0) return null;
        int cut = idx + marker.length();
        MutableComponent out = Component.empty();
        int[] pos = {0};
        name.visit((style, text) -> {
            int start = pos[0], end = start + text.length();
            pos[0] = end;
            if (end > cut) out.append(Component.literal(text.substring(Math.max(0, cut - start))).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
