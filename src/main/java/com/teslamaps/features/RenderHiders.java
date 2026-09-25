package com.teslamaps.features;

// SkyblockExtras-style hiders: item durability, displayed armor, Witherborn withers, forced glint,
// middle click on the SkyBlock Menu
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.CustomDataAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class RenderHiders {

    private RenderHiders() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MiddleClickSbMenu").section("Inventory")
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("SkyBlock Menu");
                    b.toggle("Middle Click SkyBlock Menu", () -> c.middleClickSbMenu, v -> c.middleClickSbMenu = v)
                            .desc("Middle-clicking the SkyBlock Menu item in your inventory opens the menu.");
                });
    }

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

    /** Witherborn minions; dungeon boss withers stay. */
    public static boolean shouldHide(Entity e) {
        return e instanceof WitherBoss && cfg().hideWitherbornWithers && SkyblockUtils.isInSkyblock()
                && !(DungeonManager.isInDungeon() && DungeonManager.isInBossArea());
    }

    /** From HandledScreenClickMixin. */
    public static boolean middleClickMenu(Slot slot, int button, ContainerInput type) {
        TeslaMapsConfig c = cfg();
        if (!c.middleClickSbMenu || button != 2 || (type != ContainerInput.CLONE && type != ContainerInput.PICKUP) || slot == null || !(slot.container instanceof Inventory)
                || !c.section("Inventory") || !SkyblockUtils.isInSkyblock()) return false;
        if (!ItemUtil.skyblockId(slot.getItem()).equals("SKYBLOCK_MENU")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        mc.getConnection().sendCommand("sbmenu");
        return true;
    }
}
