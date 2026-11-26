package com.teslamaps.features;

// SkyblockExtras-style hiders: item durability, displayed armor, Witherborn withers, forced glint
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.CustomDataAccessor;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class RenderHiders {

    private RenderHiders() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean hideDurability() {
        return cfg().hideItemDurability;
    }

    /** From NoGlintMixin. Reads the tag without copying. */
    public static boolean forceGlint(ItemStack stack) {
        if (!cfg().forceEnchantGlint) return false;
        if (stack.isEnchanted()) return true;
        CustomData d = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        if (d == null) return false;
        Tag t = ((CustomDataAccessor) (Object) d).teslamaps$tag().get("enchantments");
        return t instanceof CompoundTag ct && !ct.isEmpty();
    }

    public static void stripArmor(ArmorStandRenderState s) {
        if (!cfg().hideArmorStandArmor) return;
        s.chestEquipment = ItemStack.EMPTY;
        s.legsEquipment = ItemStack.EMPTY;
        s.feetEquipment = ItemStack.EMPTY;
    }

    /** Witherborn minions (dungeon boss withers stay) and Wither Cloak creepers. */
    public static boolean shouldHide(Entity e) {
        if (e instanceof Creeper c && "Hide".equals(cfg().witherCloakCreepers) && c.isInvisible() && c.isPowered() && c.getHealth() == 20f) return true;
        return e instanceof WitherBoss && cfg().hideWitherbornWithers && SkyblockUtils.isInSkyblock()
                && !(DungeonManager.isInDungeon() && DungeonManager.isInBossArea());
    }
}
