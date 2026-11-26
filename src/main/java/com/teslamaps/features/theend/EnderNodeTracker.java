package com.teslamaps.features.theend;

// Ported from SkyHanni's EnderNodeTracker.kt + EnderNode.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SackApi;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ender Nodes, Endermite Nests and their loot in The End. */
public final class EnderNodeTracker {

    private static final Pattern NODE = Pattern.compile("^ENDER NODE! You found (?:([\\d,]+)x )?(.+?)!$");
    private static final Pattern DROP = Pattern.compile("(?:RARE|PET) DROP! (?:§.)*(.+?) (?:§.)*\\(");
    private static final Pattern PET = Pattern.compile("(?:RARE|PET) DROP! (?:§r)*(?:§l)?§([0-9a-f])(?:§.)*Enderman\\b");
    private static final String NODES = "ENDER_NODES", NESTS = "ENDERMITE_NEST", MITE_GEL = "MITE_GEL";
    private static final String[] PET_COLORS = {"f", "a", "9", "5", "6"};
    private static final double[] GEORGE = {100, 500, 2_000, 10_000, 1_000_000};
    private static final Map<String, String> LOOT = new LinkedHashMap<>();
    private static final Map<String, String> ARMOR = new LinkedHashMap<>();

    static {
        LOOT.put("Enchanted End Stone", "ENCHANTED_ENDSTONE");
        LOOT.put("Enchanted Obsidian", "ENCHANTED_OBSIDIAN");
        LOOT.put("Enchanted Ender Pearl", "ENCHANTED_ENDER_PEARL");
        LOOT.put("Grand Experience Bottle", "GRAND_EXP_BOTTLE");
        LOOT.put("Titanic Experience Bottle", "TITANIC_EXP_BOTTLE");
        LOOT.put("End Stone Shulker", "END_STONE_SHULKER");
        LOOT.put("End Stone Geode", "ENDSTONE_GEODE");
        LOOT.put("◆ Magical Rune I", "MAGIC_RUNE;1");
        LOOT.put("Mite Gel", MITE_GEL);
        LOOT.put("Shrimp the Fish", "SHRIMP_THE_FISH");
        ARMOR.put("Ender Helmet", "END_HELMET");
        ARMOR.put("Ender Chestplate", "END_CHESTPLATE");
        ARMOR.put("Ender Leggings", "END_LEGGINGS");
        ARMOR.put("Ender Boots", "END_BOOTS");
        ARMOR.put("Ender Necklace", "ENDER_NECKLACE");
        ARMOR.put("Ender Gauntlet", "ENDER_GAUNTLET");
    }

    private static Tracker tracker;
    private static HudEditElement hud;
    private static boolean show;
    private static int miteGelInv = -1, ticks;
    private static long miteGelSack = -1;

    private EnderNodeTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("ender_node", "§5§lEnder Node Tracker")
                .priceSource(() -> cfg().enderNodePriceSource)
                .fixedValue(NODES, 0).name(NODES, "§dEnder Nodes mined")
                .fixedValue(NESTS, 0).name(NESTS, "§cEndermite Nest");
        String[] rar = {"§fCommon", "§aUncommon", "§9Rare", "§5Epic", "§6Legendary"};
        for (int i = 0; i < 5; i++) tracker.name("ENDERMAN;" + i, rar[i] + " Enderman Pet");
        for (String id : ARMOR.values()) tracker.fixedValue(id, 10_000);
        hud = HudEditElement.of("enderNodeTracker", "Ender Node Tracker").ctx(HudEditElement.HudCtx.THE_END).section("The End")
                .enabled(() -> cfg().enderNodeTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("EnderNodeTracker").section("The End").when(() -> cfg().enderNodeTracker)
                .tick(EnderNodeTracker::tick)
                .serverChat(EnderNodeTracker::onChat)
                .hud("endernodetracker", EnderNodeTracker::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Ender Node Tracker", () -> c.enderNodeTracker, v -> c.enderNodeTracker = v)
                            .desc("Counts Ender Nodes, Endermite Nests, node loot (Mite Gel from sacks too), Enderman pets by rarity and Ender armor in The End. Value = max of market, NPC and George price (/tmap gui).")
                            .tags("ender node", "endermite", "nest", "mite gel", "end", "tracker", "mining");
                    b.toggle("  Only While Holding A Mining Tool", () -> c.enderNodeOnlyTool, v -> c.enderNodeOnlyTool = v);
                    b.priceSource("  Price Source", () -> c.enderNodePriceSource, v -> c.enderNodePriceSource = v);
                    b.trackerButtons(tracker);
                });
    }

    private static boolean holdingTool() {
        Minecraft mc = Minecraft.getInstance();
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        return id != null && (id.contains("PICKAXE") || id.contains("DRILL") || id.contains("GAUNTLET"));
    }

    private static void tick() {
        show = EndProtector.inArea();
        if (!show) {
            miteGelInv = -1;
            miteGelSack = -1;
            return;
        }
        boolean counting = !cfg().enderNodeOnlyTool || holdingTool();
        int inv = countMiteGel();
        if (miteGelInv >= 0 && inv > miteGelInv && counting) tracker.add(MITE_GEL, inv - miteGelInv);
        miteGelInv = inv;
        long sack = SackApi.amount("Mite Gel");
        if (miteGelSack >= 0 && sack > miteGelSack && counting) tracker.add(MITE_GEL, sack - miteGelSack);
        miteGelSack = sack;
        if (++ticks % 20 == 0) updatePrices();
        tracker.tick();
    }

    private static int countMiteGel() {
        Minecraft mc = Minecraft.getInstance();
        int n = 0;
        var items = mc.player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && MITE_GEL.equals(ItemUtil.skyblockId(s))) n += s.getCount();
        }
        return n;
    }

    private static double market(String id) {
        Double p = PriceManager.getPriceOrNull(id, cfg().enderNodePriceSource);
        double npc = SkyblockItems.hasNpcPrice(id) ? SkyblockItems.npcPrice(id) : 0;
        return Math.max(p == null ? 0 : p, npc);
    }

    private static void updatePrices() {
        for (String id : LOOT.values()) tracker.fixedValue(id, market(id));
        for (int i = 0; i < 5; i++) tracker.fixedValue("ENDERMAN;" + i, Math.max(market("ENDERMAN;" + i), GEORGE[i]));
    }

    private static void onChat(String text) {
        if (!show || (cfg().enderNodeOnlyTool && !holdingTool())) return;
        String plain = StringUtil.strip(text).trim();
        Matcher n = NODE.matcher(plain);
        if (n.matches()) {
            tracker.add(NODES, 1);
            String name = n.group(2).trim();
            long amount = 1;
            if (n.group(1) != null) {
                try { amount = Long.parseLong(n.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
            }
            if (name.equals("Endermite Nest")) { tracker.add(NESTS, 1); return; }
            String id = idFor(name);
            // mite gel lands in the inventory or sacks, the tick picks it up there
            if (id != null && !id.equals(MITE_GEL)) tracker.add(id, amount);
            return;
        }
        if (!plain.contains(" DROP! ")) return;
        Matcher p = PET.matcher(text);
        if (p.find()) {
            for (int i = 0; i < PET_COLORS.length; i++) {
                if (PET_COLORS[i].equals(p.group(1))) { tracker.add("ENDERMAN;" + i, 1); return; }
            }
        }
        Matcher d = DROP.matcher(text);
        if (d.find()) {
            String id = idFor(StringUtil.strip(d.group(1)).trim());
            if (id != null && !id.equals(MITE_GEL)) tracker.add(id, 1);
        }
    }

    private static String idFor(String name) {
        String id = LOOT.get(name);
        return id != null ? id : ARMOR.get(name);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
