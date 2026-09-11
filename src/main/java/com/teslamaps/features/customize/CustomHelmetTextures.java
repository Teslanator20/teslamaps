package com.teslamaps.features.customize;

import com.teslamaps.utils.SkyblockItems;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.regex.Pattern;

/** The skyblock head skins the helmet picker offers, taken from the item table. */
public class CustomHelmetTextures {
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\[Lvl[^\\]]*\\]");

    private static List<NamedTexture> textures = List.of();

    public static void init() {
        SkyblockItems.load();
    }

    /** Built lazily; the item table loads async, so the first screen open may see an empty list. */
    public static List<NamedTexture> getTextures() {
        if (textures.isEmpty() && SkyblockItems.loaded()) {
            textures = SkyblockItems.headTextures().stream()
                    .map(head -> new NamedTexture(clean(head.name()), head.texture(), head.id()))
                    .toList();
        }
        return textures;
    }

    public static ResolvableProfile getProfile(String texture) {
        return SkyblockItems.profileFor(texture);
    }

    private static String clean(String name) {
        return LEVEL_PATTERN.matcher(name).replaceAll("").trim();
    }

    public record NamedTexture(String name, String texture, String internalName) {}
}
