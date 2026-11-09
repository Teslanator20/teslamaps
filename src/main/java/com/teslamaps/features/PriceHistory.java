package com.teslamaps.features;

// NEU's price graph key (GuiPriceGraph.kt) + SkyHanni's price website links
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

public final class PriceHistory {

    private static Feature feature;

    private PriceHistory() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PriceHistory").section("Inventory")
                .when(() -> cfg().priceHistory)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Price History");
                    b.toggle("Price History Website", () -> c.priceHistory, v -> c.priceHistory = v)
                            .desc("Key over an item opens its price history on skyblock.bz (bazaar) or sky.coflnet.com (auction) in the browser. /tmap pricegraph <item>")
                            .tags("price graph", "history", "coflnet", "skyblock.bz", "trend");
                    b.keybind("  Website Key", () -> c.priceHistoryKey, v -> c.priceHistoryKey = v);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("pricegraph")
                        .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String text = StringArgumentType.getString(c, "item").trim();
                                    String id = PriceManager.idForName(text);
                                    if (id == null) id = SkyblockItems.idForName(text);
                                    if (id == null) id = text.toUpperCase(Locale.ROOT).replace(' ', '_');
                                    String fid = id;
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> open(mc, fid, display(fid)));
                                    return 1;
                                })))));
    }

    private static String display(String id) {
        String n = SkyblockItems.coloredName(id);
        return n != null ? n : id;
    }

    /** Container key hook. */
    public static boolean onKey(int key, Slot hovered) {
        if (feature == null || !feature.active() || key <= 0 || key != cfg().priceHistoryKey) return false;
        if (hovered == null || hovered.getItem().isEmpty()) return false;
        String id = priceId(hovered.getItem());
        if (id == null || id.isEmpty()) return false;
        open(Minecraft.getInstance(), id, hovered.getItem().getHoverName().getString());
        return true;
    }

    private static void open(Minecraft mc, String id, String name) {
        try {
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(websiteUrl(id)));
        } catch (Exception e) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cCould not open the browser"));
        }
    }

    private static String websiteUrl(String id) {
        if (PriceManager.hasBazaar(id)) return "https://www.skyblock.bz/product/" + id;
        return "https://sky.coflnet.com/item/" + id;
    }

    static String priceId(ItemStack st) {
        String id = ItemUtil.skyblockId(st);
        if (id.equals("ENCHANTED_BOOK")) {
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            if (d != null) {
                CompoundTag ench = d.copyTag().getCompoundOrEmpty("enchantments");
                if (ench.size() == 1) {
                    String k = ench.keySet().iterator().next();
                    return "ENCHANTMENT_" + k.toUpperCase(Locale.ROOT) + "_" + ench.getIntOr(k, 1);
                }
            }
        }
        if (!id.isEmpty()) return id;
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        String p = PriceManager.idForName(name);
        return p != null ? p : SkyblockItems.idForName(name);
    }
}
