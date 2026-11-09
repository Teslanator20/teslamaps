package com.teslamaps.features.mining;

// Tip table ported from NotEnoughUpdates' MiningOverlay.getTip
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/** One hint line per commission in the Commissions menu tooltip. Sub-option of Commission Highlight. */
public final class CommissionTips {

    public static final String[] MODES = {"Off", "While Holding Shift", "Always"};

    private static final String JUNGLE = " §a(Jungle)", GOBLIN = " §6(Goblin Holdout)", MITHRIL = " §b(Mithril Deposits)",
            PRECURSOR = " §8(Precursor Remnants)", MAGMA = " §c(Magma Fields)";

    private static final Map<String, String> SHAFT = Map.ofEntries(
            Map.entry("Corpse Looter", "Find Corpses in a Glacite Mineshaft"),
            Map.entry("Mineshaft Explorer", "Discover a Glacite Mineshaft"),
            Map.entry("Scrap Collector", "Break non-vanilla Ores and not Hard Stone in a Glacite Mineshaft"),
            Map.entry("Umber Collector", "Break red sand/hardened clay"),
            Map.entry("Tungsten Collector", "Break cobblestone/clay"),
            Map.entry("Glacite Collector", "Break ice"),
            Map.entry("Onyx Gemstone Collector", "Break black glass"),
            Map.entry("Aquamarine Gemstone Collector", "Break aqua glass"),
            Map.entry("Peridot Gemstone Collector", "Break dark green glass"),
            Map.entry("Citrine Gemstone Collector", "Break brown glass"),
            Map.entry("Maniac Slayer", "Kill mobs in a Glacite Mineshaft"));

    private static final Map<String, String> DWARVEN = Map.ofEntries(
            Map.entry("First Event", "Participate in any §6Mining Event"),
            Map.entry("Lucky Raffle", "Collect 20 Raffle Tickets during §6Raffle Event"),
            Map.entry("Goblin Raid Slayer", "Kill 20 Goblins during §6Goblin Raid Event"),
            Map.entry("Raffle", "Participate in §6Raffle Event"),
            Map.entry("Goblin Raid", "Participate in §6Goblin Raid event"),
            Map.entry("2x Mithril Powder Collector", "Collect 500 Mithril Powder during §62x Powder event"),
            Map.entry("Glacite Walker Slayer", "Kill 50 Glacite Walkers §b(Great Ice Wall)"),
            Map.entry("Goblin Slayer", "Kill 100 Goblins §b(Goblin Burrows)"),
            Map.entry("Golden Goblin Slayer", "Kill 1 Golden Goblin (anywhere)"),
            Map.entry("Star Sentry Puncher", "Damage Star Sentries 10 times (anywhere)"),
            Map.entry("Treasure Hoarder Puncher", "Damage Treasure Hoarders 10 times §b(Upper Mines)"),
            Map.entry("Mines Slayer", "Kill 50 mobs (anywhere)"),
            Map.entry("Mithril Miner", "Break 350 Mithril (anywhere)"),
            Map.entry("Titanium Miner", "Break 15 Titanium (anywhere)"),
            Map.entry("Cliffside Veins Mithril", "Break 250 Mithril §b(Cliffside Veins)"),
            Map.entry("Royal Mines Mithril", "Break 250 Mithril §b(Royal Mines)"),
            Map.entry("Lava Springs Mithril", "Break 250 Mithril §b(Lava Springs)"),
            Map.entry("Rampart's Quarry Mithril", "Break 250 Mithril §b(Rampart's Quarry)"),
            Map.entry("Upper Mines Mithril", "Break 250 Mithril §b(Upper Mines)"),
            Map.entry("Cliffside Veins Titanium", "Break 10 Titanium §b(Cliffside Veins)"),
            Map.entry("Lava Springs Titanium", "Break 10 Titanium §b(Lava Springs)"),
            Map.entry("Royal Mines Titanium", "Break 10 Titanium §b(Royal Mines)"),
            Map.entry("Rampart's Quarry Titanium", "Break 10 Titanium §b(Rampart's Quarry)"),
            Map.entry("Upper Mines Titanium", "Break 10 Titanium §b(Upper Mines)"));

    private static final Map<String, String> HOLLOWS = Map.ofEntries(
            Map.entry("Chest Looter", "Open 3 chests"),
            Map.entry("Hard Stone Miner", "Break 1,000 Hard Stone"),
            Map.entry("Goblin Slayer", "Kill 13 Goblins" + GOBLIN),
            Map.entry("Sludge Slayer", "Kill 25 Sludges" + JUNGLE),
            Map.entry("Thyst Slayer", "Kill 5 Thysts, when breaking Amethysts" + JUNGLE),
            Map.entry("Boss Corleone Slayer", "Find and kill Corleone" + MITHRIL),
            Map.entry("Yog Slayer", "Kill 13 Yogs" + MAGMA),
            Map.entry("Automaton Slayer", "Kill 13 Automatons" + PRECURSOR),
            Map.entry("Team Treasurite Member Slayer", "Kill 13 Team Treasurite Members" + MITHRIL),
            Map.entry("Amethyst Crystal Hunter", "Temple Jump & Run" + JUNGLE),
            Map.entry("Jade Crystal Hunter", "4 weapons from Mines of Divan" + MITHRIL),
            Map.entry("Amber Crystal Hunter", "King and Queen" + GOBLIN),
            Map.entry("Sapphire Crystal Hunter", "6 Robot Parts in Precursor City" + PRECURSOR),
            Map.entry("Topaz Crystal Hunter", "Kill Bal" + MAGMA),
            Map.entry("Amber Gemstone Collector", "Break orange glass" + GOBLIN),
            Map.entry("Sapphire Gemstone Collector", "Break blue glass" + PRECURSOR),
            Map.entry("Jade Gemstone Collector", "Break green glass" + MITHRIL),
            Map.entry("Amethyst Gemstone Collector", "Break purple glass" + JUNGLE),
            Map.entry("Ruby Gemstone Collector", "Break red glass (anywhere)"),
            Map.entry("Topaz Gemstone Collector", "Break yellow glass" + MAGMA));

    private CommissionTips() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (c.commissionTips == null || c.commissionTips.equals("Off") || !c.commissionHighlight || !c.section("Inventory")) return;
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !cs.getTitle().getString().contains("Commissions")) return;
            if (c.commissionTips.equals("While Holding Shift") && !mc.hasShiftDown()) return;
            onTooltip(lines);
        });
    }

    private static void onTooltip(List<Component> lines) {
        String area = SkyblockUtils.getArea().toLowerCase();
        Map<String, String> table = area.contains("hollows") ? HOLLOWS : area.contains("mineshaft") ? SHAFT : DWARVEN;
        for (int i = 0; i < lines.size(); i++) {
            String l = StringUtil.stripTrim(lines.get(i).getString());
            String tip = table.get(l);
            if (tip == null && table == DWARVEN) tip = SHAFT.get(l);
            if (tip == null) continue;
            lines.add(i + 1, Component.literal("§8§l> §7" + tip));
            return;
        }
    }
}
