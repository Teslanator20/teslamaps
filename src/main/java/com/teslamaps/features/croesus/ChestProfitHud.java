package com.teslamaps.features.croesus;

// NoammAddons ChestProfit / Devonian ChestProfit: movable list of every dungeon chest's profit after the run
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChestProfitHud {

    private static final int ROW = 10;
    private static final double CROESUS_X = -28, CROESUS_Y = 119, CROESUS_Z = 35;

    private static final Map<String, CroesusProfit.Parsed> chests = new LinkedHashMap<>();
    private static List<String> lines = List.of();
    private static int width = 0;
    private static Object lastLevel;
    private static String lastRunTitle = "";
    private static int tickCount = 0;
    private static HudEditElement el;

    private ChestProfitHud() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        el = HudEditElement.of("chestProfitHud", "Chest Profit HUD")
                .enabled(() -> cfg().chestProfitHud)
                .size(() -> Math.max(120, width + 6), () -> Math.max(3, lines.size()) * ROW + 4)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of(
                        "§6§lChest Profit", "§8Bedrock Chest: §a+4.2M", "§bDiamond Chest: §a+120k", "§6Wood Chest: §c-35k")));
        Feature.of("ChestProfitHud").when(() -> cfg().chestProfitHud)
                .tick(ChestProfitHud::tick)
                .hud("chestprofithud", ChestProfitHud::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            chests.clear();
            lastRunTitle = "";
            rebuild(mc);
        }
        if (++tickCount % 5 != 0) return;
        if (tickCount % 40 == 0 && !chests.isEmpty()) rebuild(mc);
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String title = com.teslamaps.utils.StringUtil.stripTrim(cs.getTitle().getString());
        boolean changed = false;
        if (CroesusProfit.isRunView(title) && title.contains("Catacombs")) {
            if (!title.equals(lastRunTitle)) {
                chests.clear();
                lastRunTitle = title;
            }
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                ItemStack item = slot.getItem();
                if (item.isEmpty()) continue;
                String name = com.teslamaps.utils.StringUtil.stripTrim(item.getHoverName().getString()).replace(" Chest", "").trim();
                if (!CroesusProfit.CHEST_NAMES.contains(name)) continue;
                changed |= put(name, CroesusProfit.parseLore(item.get(DataComponents.LORE)));
            }
        } else if (CroesusProfit.isChestGui(title) && DungeonManager.isInDungeon()) {
            String name = title.replace(" Chest", "").trim();
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                ItemLore lore = slot.getItem().get(DataComponents.LORE);
                if (lore == null || !hasContents(lore)) continue;
                changed |= put(name, CroesusProfit.parseLore(lore));
                break;
            }
        }
        if (changed) rebuild(mc);
    }

    private static boolean put(String name, CroesusProfit.Parsed p) {
        if (chests.get(name) == p) return false;
        chests.put(name, p);
        return true;
    }

    private static boolean hasContents(ItemLore lore) {
        for (Component c : lore.lines())
            if (com.teslamaps.utils.StringUtil.stripTrim(c.getString()).equals("Contents")) return true;
        return false;
    }

    private static double profit(CroesusProfit.Parsed p) {
        return p.profit() - (cfg().chestProfitHudEssence ? 0 : p.essence);
    }

    private static void rebuild(Minecraft mc) {
        List<Map.Entry<String, CroesusProfit.Parsed>> list = new ArrayList<>();
        for (var e : chests.entrySet()) if (!e.getValue().opened || e.getValue().value > 0) list.add(e);
        list.sort((a, b) -> Double.compare(profit(b.getValue()), profit(a.getValue())));
        List<String> out = new ArrayList<>();
        if (!list.isEmpty()) out.add("§6§lChest Profit");
        for (var e : list) {
            CroesusProfit.Parsed p = e.getValue();
            double v = profit(p);
            String opened = p.opened ? " §8(opened)" : "";
            out.add(color(e.getKey()) + e.getKey() + " Chest: " + (v >= 0 ? "§a+" : "§c") + CroesusProfit.fmt(v)
                    + (p.incomplete ? " §8?" : "") + opened);
            if (!cfg().chestProfitHudCompact) out.addAll(p.breakdown);
        }
        int w = 0;
        for (String l : out) w = Math.max(w, mc.font.width(l));
        width = w;
        lines = out;
    }

    private static String color(String chest) {
        return switch (chest) {
            case "Wood" -> "§6";
            case "Gold" -> "§e";
            case "Diamond" -> "§b";
            case "Emerald" -> "§2";
            case "Obsidian" -> "§5";
            case "Bedrock" -> "§8";
            default -> "§f";
        };
    }

    private static boolean visible(Minecraft mc) {
        if (DungeonManager.isInDungeon()) return true;
        if (mc.player == null || !com.teslamaps.utils.SkyblockUtils.isInArea("Dungeon Hub")) return false;
        return mc.player.distanceToSqr(CROESUS_X, CROESUS_Y, CROESUS_Z) <= 150;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof AbstractContainerScreen<?>) return;
        if (!visible(mc)) return;
        draw(g, mc, el.x(), el.y(), el.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> text) {
        int w = text == lines ? width : 0;
        if (text != lines) for (String l : text) w = Math.max(w, mc.font.width(l));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        com.teslamaps.render.gui.HudStyle.panel(g, 0, 0, w + 6, text.size() * ROW + 4);
        int ty = 3;
        for (String l : text) {
            g.text(mc.font, l, 3, ty, 0xFFFFFFFF);
            ty += ROW;
        }
        pose.popMatrix();
    }
}
