package com.teslamaps.features.croesus;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.utils.ItemUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** /tmap price [item|reload], after SkyHanni's /shdebugprice and /shreloadlbins. */
public final class PriceDebug {

    private PriceDebug() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("price")
                .executes(c -> debug(c.getSource(), null))
                .then(ClientCommands.literal("reload").executes(c -> reload(c.getSource())))
                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                        .executes(c -> debug(c.getSource(), StringArgumentType.getString(c, "item"))));
    }

    /** /tmap refreshprices, same as /tmap price reload. */
    public static LiteralArgumentBuilder<FabricClientCommandSource> refreshCommand() {
        return ClientCommands.literal("refreshprices").executes(c -> reload(c.getSource()));
    }

    private static int reload(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Reloading bazaar, lowest BIN and average prices…"));
        long start = System.currentTimeMillis();
        PriceManager.forceRefreshAll(() -> {
            var p = Minecraft.getInstance().player;
            if (p == null) return;
            int bz = PriceManager.bazaarCount(), bin = PriceManager.lowestBinCount();
            boolean ok = bz > 0 && bin > 0;
            p.sendSystemMessage(Component.literal("§b[TeslaMaps] " + (ok ? "§aPrices reloaded" : "§cSome prices failed to load")
                    + " §7in " + (System.currentTimeMillis() - start) / 100 / 10.0 + "s: §f" + bz + " §7bazaar, §f" + bin
                    + " §7BIN"));
        });
        return 1;
    }

    private static String resolve(String arg) {
        if (arg == null || arg.isBlank()) {
            var player = Minecraft.getInstance().player;
            if (player == null) return null;
            String id = ItemUtil.skyblockId(player.getMainHandItem());
            if (id.isEmpty()) id = PriceManager.idForName(player.getMainHandItem().getHoverName().getString());
            return id == null || id.isEmpty() ? null : id;
        }
        String id = PriceManager.idForName(arg);
        if (id == null) id = com.teslamaps.utils.SkyblockItems.idForName(arg);
        return id != null ? id : arg.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static int debug(FabricClientCommandSource src, String arg) {
        PriceManager.ensureFresh();
        String id = resolve(arg);
        if (id == null) {
            src.sendFeedback(Component.literal("§cHold an item or use /tmap price <name or id>"));
            return 0;
        }
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Prices for §6" + id));
        String[] names = {"BZ Instant Buy", "BZ Instant Sell", "NPC Sell", "Lowest BIN"};
        int[] ids = {PriceManager.SOURCE_BAZAAR_BUY, PriceManager.SOURCE_BAZAAR_SELL,
                PriceManager.SOURCE_NPC, PriceManager.SOURCE_LOWEST_BIN};
        for (int i = 0; i < ids.length; i++) {
            line(src, "Source " + names[i], PriceManager.getPriceOrNull(id, ids[i]));
        }
        line(src, "Instant Buy", PriceManager.bazaarBuyOrNull(id));
        line(src, "Instant Sell", PriceManager.bazaarSellOrNull(id));
        line(src, "Best Buy Order", PriceManager.bazaarBuyOrderOrNull(id));
        line(src, "Best Sell Offer", PriceManager.bazaarSellOfferOrNull(id));
        line(src, "Lowest BIN", PriceManager.lowestBinOrNull(id));
        line(src, "NPC Sell", com.teslamaps.utils.SkyblockItems.hasNpcPrice(id)
                ? com.teslamaps.utils.SkyblockItems.npcPrice(id) : null);
        Double craft = CraftCost.costOrNull(id, PriceManager.SOURCE_BAZAAR_BUY);
        src.sendFeedback(Component.literal(" §7Raw Craft Cost: " + (craft != null ? "§6" + EstimatedValue.exact(craft)
                : CraftCost.known(id) ? "§8none" : "§8loading, run again")));
        src.sendFeedback(Component.literal(" §8" + PriceManager.bazaarCount() + " bazaar, "
                + PriceManager.lowestBinCount() + " BIN" + (PriceManager.isLoaded() ? "" : ", not loaded yet")));
        return 1;
    }

    private static void line(FabricClientCommandSource src, String label, Double v) {
        src.sendFeedback(Component.literal(" §7" + label + ": " + (v != null ? "§6" + EstimatedValue.exact(v) : "§8-")));
    }
}
