package com.teslamaps.features;

// After Skyblocker's ItemPrice.itemPriceLookup and NEU's AhBzKeybind
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Key over an item: /bz for bazaar items, /ahs for the rest. Second key: /viewrecipe. */
public final class PriceLookup {

    private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl \\d+(?: ➡ \\d+)?] (?:\\[\\d+✦] )?");
    private static boolean wasDown, wasRecipeDown;

    private PriceLookup() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PriceLookup").section("Inventory").when(() -> cfg().priceLookup).tick(PriceLookup::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean screen = mc.gui.screen() instanceof AbstractContainerScreen<?>;
        boolean down = screen && KeyNames.isDown(cfg().priceLookupKey);
        boolean recipe = screen && KeyNames.isDown(cfg().priceLookupRecipeKey);
        if (down && !wasDown) lookup(mc, false);
        if (recipe && !wasRecipeDown) lookup(mc, true);
        wasDown = down;
        wasRecipeDown = recipe;
    }

    private static void lookup(Minecraft mc, boolean recipe) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || mc.player == null || mc.getConnection() == null) return;
        Slot slot = ((com.teslamaps.mixin.HandledScreenAccessor) cs).getFocusedSlot();
        if (slot == null || slot.getItem().isEmpty()) return;
        ItemStack stack = slot.getItem();
        String id = ItemUtil.skyblockId(stack);
        if (recipe) {
            if (id.isEmpty()) id = PriceManager.idForName(stack.getHoverName().getString());
            if (id == null || id.isEmpty()) {
                fail(mc, "No SkyBlock id on this item");
                return;
            }
            mc.getConnection().sendCommand("viewrecipe " + id);
            return;
        }
        PriceManager.ensureFresh();
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        String priceId = id;

        if (id.equals("PET")) {
            name = PET_LEVEL.matcher(name).replaceFirst("");
            send(mc, "ahs " + name);
            return;
        }
        if (id.equals("ENCHANTED_BOOK")) {
            String ench = singleEnchant(stack);
            List<String> lore = ItemUtil.loreLines(stack);
            if (ench != null) priceId = ench;
            if (!lore.isEmpty()) name = StringUtil.stripTrim(lore.get(0));
        } else if (!id.isEmpty()) {
            String known = SkyblockItems.name(id);
            if (known != null) name = StringUtil.stripTrim(known);
        } else {
            priceId = PriceManager.idForName(name);
        }
        if (name.isEmpty()) return;

        if (priceId != null && PriceManager.hasBazaar(priceId)) send(mc, "bz " + name);
        else if (priceId != null && PriceManager.lowestBinOrNull(priceId) != null) send(mc, "ahs " + name);
        else if (!PriceManager.isLoaded()) fail(mc, "Prices are still loading");
        else fail(mc, "No bazaar or auction price for " + name);
    }

    private static String singleEnchant(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag ench = data.copyTag().getCompoundOrEmpty("enchantments");
        if (ench.keySet().size() != 1) return null;
        String k = ench.keySet().iterator().next();
        return "ENCHANTMENT_" + k.toUpperCase(Locale.ROOT) + "_" + ench.getIntOr(k, 0);
    }

    private static void send(Minecraft mc, String cmd) {
        // packet, not sendCommand: the client /ahs would catch it
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(StringUtil.strip(cmd).replace("§", "")));
    }

    private static void fail(Minecraft mc, String msg) {
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §c" + msg));
    }
}
