// Ported from ADM's p3sim/clear/blood/HeadItems.kt
package com.teslamaps.adm.sim.clear.blood;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Player-head items for the blood room. Server thread only. */
public final class HeadItems {
    private HeadItems() {}

    /** The player head key names; a plain head for PLAIN or an unknown key, EMPTY for NONE. */
    public static ItemStack stack(String key, List<SkullTexture> textures) {
        if (key.equals(HeadTextures.NONE)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        HeadTextures.Resolved tex = HeadTextures.resolve(key, textures);
        if (tex == null) return stack;
        Ec.safely("blood head", () -> {
            GameProfile gp = new GameProfile(tex.profileId, "head", new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tex.value))));
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gp));
        });
        return stack;
    }
}
