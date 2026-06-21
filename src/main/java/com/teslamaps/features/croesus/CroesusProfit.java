/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features.croesus;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CroesusProfit {

    private static final Set<String> CHEST_NAMES =
            Set.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock", "Free", "Paid");

    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence x([0-9,]+)$");
    private static final Pattern COINS = Pattern.compile("^([0-9,]+) Coins$");
    private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$");
    private static final Pattern ITEM_QTY = Pattern.compile("^(.+) x([0-9,]+)$"); // "Thorn Shard x1"

    private static final int GRAY_COVER = 0xCC2A2A2E; // dims a slot so it looks empty ("as if nothing there")

    private static int lastDumpId = 0;

    private static final class Parsed {
        boolean opened;
        boolean kismet;   // a Kismet Feather was used (a struck-through line in the lore)
        double value, cost;
        final List<String> breakdown = new ArrayList<>();
        double profit() { return value - cost; }
    }

    private static boolean isStruck(Component c) {
        if (c == null) return false;
        if (c.getString().contains("§m")) return true;            // literal §m strikethrough code
        if (c.getStyle() != null && c.getStyle().isStrikethrough()) return true; // style-based
        for (Component sib : c.getSiblings()) if (isStruck(sib)) return true;
        return false;
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.croesusProfitOverlay) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        String t = strip(cs.getTitle().getString());
        return isCroesusMenu(t) || isRunView(t) || isChestGui(t);
    }

    private static boolean isCroesusMenu(String t) { return t.contains("Croesus"); }
    private static boolean isRunView(String t) { return t.contains("Catacombs - Flo") || t.startsWith("Kuudra - "); }
    private static boolean isChestGui(String t) { return CHEST_NAMES.contains(t.replace(" Chest", "").trim()); }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        PriceManager.ensureFresh();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String title = strip(cs.getTitle().getString());

        if (TeslaMapsConfig.get().croesusDebug && System.identityHashCode(cs) != lastDumpId) {
            lastDumpId = System.identityHashCode(cs);
            dumpAll(cs, title);
        }

        if (isCroesusMenu(title)) renderCroesusMenu(ctx, cs, acc);
        else if (isRunView(title)) renderRunView(ctx, cs, acc, mc);
        else if (isChestGui(title)) renderChestGui(ctx, cs, acc, mc, title);
    }

    private static void renderCroesusMenu(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs, HandledScreenAccessor acc) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null) continue;
            boolean isRun = false, done = false, kismetUsed = false;
            for (Component cmp : lore.lines()) {
                String l = strip(cmp.getString());
                if (l.contains("Kismet Feather") && isStruck(cmp)) kismetUsed = true;
                if (l.contains("Click to view chests!")) isRun = true;
                if (l.contains("No more chests to open!")) done = true;
            }
            if (!isRun) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (done) {
                if (cfg.croesusHideOpened) ctx.fill(sx, sy, sx + 16, sy + 16, GRAY_COVER);
            } else if (kismetUsed) {
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FFA500);                 // orange = kismet used
            } else if (cfg.croesusHighlightUnopened) {
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x8055FF55);                 // green = openable
            }
        }
    }

    private static void renderRunView(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs, HandledScreenAccessor acc, Minecraft mc) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        List<Slot> chestSlots = new ArrayList<>();
        List<Parsed> results = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            String name = strip(item.getHoverName().getString()).replace(" Chest", "").trim();
            if (!CHEST_NAMES.contains(name)) continue;
            chestSlots.add(slot);
            names.add(name);
            Parsed p = parseLore(item.get(DataComponents.LORE));
            results.add(p);
        }
        if (results.isEmpty()) return;

        int bestIdx = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).opened) continue;
            if (bestIdx < 0 || results.get(i).profit() > results.get(bestIdx).profit()) bestIdx = i;
        }

        for (int i = 0; i < chestSlots.size(); i++) {
            Slot s = chestSlots.get(i);
            int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
            if (i == bestIdx) {
                if (cfg.croesusHighlightBest) { // the most profitable chest -> green
                    ctx.fill(sx, sy, sx + 16, sy + 16, 0x6655FF55);
                    drawBorder(ctx, sx, sy, 0xFF55FF55);
                }
            } else if (cfg.croesusDimOthers) {   // every other chest -> grayed out
                ctx.fill(sx, sy, sx + 16, sy + 16, GRAY_COVER);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6§lCroesus Profit");
        if (!PriceManager.isLoaded()) lines.add("§7loading prices…");
        for (int i = 0; i < results.size(); i++) {
            Parsed r = results.get(i);
            if (r.opened) { if (!cfg.croesusHideOpened) lines.add("§8" + names.get(i) + ": opened"); continue; }
            String col = r.profit() >= 0 ? "§a+" : "§c";
            String star = (i == bestIdx) ? " §e" : "";
            String kismet = r.kismet ? " §a" : "";    // Kismet Feather already used on this chest
            lines.add("§f" + names.get(i) + ": " + col + fmt(r.profit()) + star + kismet);
        }

        Slot hovered = acc.getFocusedSlot();
        if (hovered != null) {
            int hi = chestSlots.indexOf(hovered);
            if (hi >= 0 && !results.get(hi).opened) {
                lines.add("");
                lines.add("§e" + names.get(hi) + " breakdown:");
                lines.addAll(results.get(hi).breakdown);
                lines.add("§7Cost: §c-" + fmt(results.get(hi).cost));
            }
        }
        drawPanel(ctx, mc, acc, lines);
    }

    private static void renderChestGui(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs, HandledScreenAccessor acc, Minecraft mc, String title) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null) continue;
            boolean hasContents = false;
            for (Component cmp : lore.lines()) if (strip(cmp.getString()).equals("Contents")) { hasContents = true; break; }
            if (!hasContents) continue;

            Parsed r = parseLore(lore);
            List<String> lines = new ArrayList<>();
            lines.add("§6§l" + title.replace(" Chest", "") + " Chest");
            lines.addAll(r.breakdown);
            lines.add("§7Cost: §c-" + fmt(r.cost));
            String col = r.profit() >= 0 ? "§a+" : "§c";
            lines.add("§fProfit: " + col + fmt(r.profit()));
            drawPanel(ctx, mc, acc, lines);
            return;
        }
    }

    private static Parsed parseLore(ItemLore lore) {
        Parsed r = new Parsed();
        if (lore == null) return r;
        boolean inCost = false;
        for (Component cmp : lore.lines()) {
            String l = strip(cmp.getString());
            if (l.contains("Kismet Feather") && isStruck(cmp)) r.kismet = true;
            if (l.isEmpty()) continue;
            if (l.equals("Contents")) { inCost = false; continue; }
            if (l.equals("Cost")) { inCost = true; continue; }
            if (l.equals("Click to open!") || l.startsWith("NOTE") || l.startsWith("bank if") || l.startsWith("your purse")) continue;
            if (l.equals("Already opened!")) { r.opened = true; continue; }

            if (inCost) {
                Matcher coins = COINS.matcher(l);
                if (coins.matches()) r.cost += parseNum(coins.group(1));
                continue;
            }

            Matcher ess = ESSENCE.matcher(l);
            if (ess.matches()) {
                int cnt = (int) parseNum(ess.group(2));
                double p = cnt * PriceManager.getPrice("ESSENCE_" + ess.group(1).toUpperCase());
                r.value += p;
                r.breakdown.add(" §7" + l + ": §6" + fmt(p));
                continue;
            }
            Matcher book = BOOK.matcher(l);
            if (book.matches()) {
                double p = bookValue(book.group(1), roman(book.group(2)));
                r.value += p;
                r.breakdown.add(" §7" + l + ": §6" + fmt(p));
                continue;
            }
            String itemName = l;
            int qty = 1;
            Matcher q = ITEM_QTY.matcher(l);
            if (q.matches()) { itemName = q.group(1); qty = (int) parseNum(q.group(2)); }
            String id = PriceManager.idForName(itemName);
            double p = (id != null ? PriceManager.getPrice(id) : 0) * qty;
            r.value += p;
            r.breakdown.add(" §7" + l + ": §6" + (p > 0 ? fmt(p) : "?"));
        }
        return r;
    }

    private static double bookValue(String name, int level) {
        String base = name.toUpperCase().replace("'", "").replaceAll("[^A-Z0-9 ]", "").trim().replace(" ", "_");
        double p = PriceManager.getPrice("ENCHANTMENT_" + base + "_" + level);
        if (p > 0) return p;
        return PriceManager.getPrice("ENCHANTMENT_ULTIMATE_" + base + "_" + level); // ultimate books drop the "Ultimate" word
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
            default -> 1;
        };
    }

    private static void drawPanel(GuiGraphicsExtractor ctx, Minecraft mc, HandledScreenAccessor acc, List<String> lines) {
        if (lines.size() <= 1) return;
        int panelX = acc.getX() + 176 + 6;
        int panelY = acc.getY();
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        int pad = 4;
        ctx.fill(panelX - pad, panelY - pad, panelX + w + pad, panelY + lines.size() * 10 + pad, 0xCC101012);
        int y = panelY;
        for (String l : lines) { ctx.text(mc.font, l, panelX, y, 0xFFFFFFFF); y += 10; }
    }

    private static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int color) {
        ctx.fill(x, y, x + 16, y + 1, color);
        ctx.fill(x, y + 15, x + 16, y + 16, color);
        ctx.fill(x, y, x + 1, y + 16, color);
        ctx.fill(x + 15, y, x + 16, y + 16, color);
    }

    private static void dumpAll(AbstractContainerScreen<?> cs, String title) {
        TeslaMaps.LOGGER.info("[CroesusDump] TITLE: '{}'", title);
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) continue;
            TeslaMaps.LOGGER.info("[CroesusDump] === {} (nameStruck={}) ===",
                    strip(item.getHoverName().getString()), isStruck(item.getHoverName()));
            for (Component cmp : lore.lines())
                TeslaMaps.LOGGER.info("[CroesusDump]  |{} {}", isStruck(cmp) ? " [STRUCK]" : "", strip(cmp.getString()));
        }
    }

    private static double parseNum(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (Exception e) { return 0; }
    }

    private static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (a >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    private static String strip(String s) {
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
