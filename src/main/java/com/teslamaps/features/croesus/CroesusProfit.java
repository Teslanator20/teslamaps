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

    static final Set<String> CHEST_NAMES =
            Set.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock", "Free", "Paid");

    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence(?: x([0-9,]+))?$");
    private static final Pattern KUUDRA_ARMOR = Pattern.compile(
            "^(?:Crimson|Terror|Aurora|Hollow|Fervor) (?:Helmet|Chestplate|Leggings|Boots)$");
    private static final double[] SALVAGE_BY_STARS = {120, 138, 158, 182, 210, 240, 272, 308};
    private static final Set<String> WORTHLESS = Set.of(
            "DUNGEON_DISC_1", "DUNGEON_DISC_2", "DUNGEON_DISC_3", "DUNGEON_DISC_4", "DUNGEON_DISC_5",
            "MAXOR_THE_FISH", "STORM_THE_FISH", "GOLDOR_THE_FISH");
    private static final Pattern WORTHLESS_BOOK = Pattern.compile(
            "^ENCHANTMENT_(?:(?:ULTIMATE_)?(?:NO_PAIN_NO_GAIN|COMBO|BANK|JERRY)_\\d+|(?:FEATHER_FALLING|INFINITE_QUIVER)_(?:[6-9]|10))$");
    private static final Pattern COINS = Pattern.compile("^([0-9,]+) Coins$");
    private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$");
    private static final Pattern ITEM_QTY = Pattern.compile("^(.+) x([0-9,]+)$"); // "Thorn Shard x1"
    static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9 ]");

    private static final int GRAY_COVER = 0xCC2A2A2E; // dims a slot so it looks empty ("as if nothing there")

    private static int lastDumpId = 0;

    static final class Parsed {
        boolean opened;
        boolean kismet;   // a Kismet Feather was used (a struck-through line in the lore)
        boolean incomplete;
        boolean noEssence;
        boolean chestKey;
        int keyIndex = -1;
        double value, cost, essence;
        final List<String> breakdown = new ArrayList<>();
        double profit() { return value - (noEssence ? essence : 0) - cost; }
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
    static boolean isRunView(String t) { return t.contains("Catacombs - Flo") || t.startsWith("Kuudra - "); }
    static boolean isChestGui(String t) { return CHEST_NAMES.contains(t.replace(" Chest", "").trim()); }

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
        if (!TeslaMapsConfig.get().croesusHighlightUnopened) return;
        Minecraft mc = Minecraft.getInstance();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            int state = runState(slot.getItem());
            if (state == 0) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (state == 1) ctx.fill(sx, sy, sx + 16, sy + 16, 0xFF44DD55);
            else if (state == 3) ctx.fill(sx, sy, sx + 16, sy + 16, 0xFFDD4444);
            if (kismetUsed(slot.getItem())) {
                com.teslamaps.render.gui.TeslaGui.checkIcon(ctx, sx + 8, sy + 1, 8, 0xFF55FF55);
            }
        }
    }

    private static int runState(ItemStack item) {
        if (item.isEmpty()) return 0;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) return 0;
        boolean isRun = false, done = false, fresh = false;
        for (Component cmp : lore.lines()) {
            String l = strip(cmp.getString());
            if (l.contains("Click to view chests!")) isRun = true;
            if (l.contains("No more chests to open!")) done = true;
            if (l.contains("No chests opened yet!")) fresh = true;
        }
        if (!isRun) return 0;
        if (fresh) return 1;
        if (done) return 2;
        return 3;
    }

    private static boolean kismetUsed(ItemStack item) {
        if (item.isEmpty()) return false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component cmp : lore.lines())
            if (strip(cmp.getString()).contains("Kismet Feather") && isStruck(cmp)) return true;
        return false;
    }

    public static boolean styleCroesusSlot(GuiGraphicsExtractor ctx, Slot slot) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.croesusHighlightUnopened) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (!isCroesusMenu(strip(cs.getTitle().getString()))) return false;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return false;
        return runState(slot.getItem()) != 0;
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
        int secondIdx = -1;
        if (cfg.croesusHighlightSecond) {
            for (int i = 0; i < results.size(); i++) {
                if (i == bestIdx || results.get(i).opened || results.get(i).profit() <= 0) continue;
                if (secondIdx < 0 || results.get(i).profit() > results.get(secondIdx).profit()) secondIdx = i;
            }
        }

        for (int i = 0; i < chestSlots.size(); i++) {
            Slot s = chestSlots.get(i);
            int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
            if (i == bestIdx) {
                if (cfg.croesusHighlightBest) { // the most profitable chest -> green
                    ctx.fill(sx, sy, sx + 16, sy + 16, 0x6655FF55);
                    drawBorder(ctx, sx, sy, 0xFF55FF55);
                }
            } else if (i == secondIdx) {
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x66FFFF55);
                drawBorder(ctx, sx, sy, 0xFFFFFF55);
            } else if (cfg.croesusDimOthers) {   // every other chest -> grayed out
                ctx.fill(sx, sy, sx + 16, sy + 16, GRAY_COVER);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6§lCroesus Profit");
        if (!PriceManager.isLoaded()) lines.add("§7loading prices…");
        if (derpyWarn(results)) lines.add("§cDerpy is mayor: essence counts may be off");
        // panel sorted by profit (best first), opened chests at the bottom
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) order.add(i);
        order.sort((a, b) -> {
            if (results.get(a).opened != results.get(b).opened) return results.get(a).opened ? 1 : -1;
            return Double.compare(results.get(b).profit(), results.get(a).profit());
        });
        for (int i : order) {
            Parsed r = results.get(i);
            if (r.opened) { if (!cfg.croesusHideOpened) lines.add("§8" + names.get(i) + ": opened"); continue; }
            String col = r.profit() >= 0 ? "§a+" : "§c";
            String star = (i == bestIdx) ? " §e" : (i == secondIdx ? " §62nd" : "");
            String kismet = r.kismet ? " §a" : "";    // Kismet Feather already used on this chest
            lines.add("§f" + names.get(i) + ": " + col + fmt(r.profit()) + (r.incomplete ? " §8?" : "") + star + kismet);
            if (noKey(r)) lines.add(" §c§lNO KUUDRA KEY");
        }

        if (!cfg.croesusCompact) {
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
        }
        drawPanel(ctx, mc, acc, lines);
    }

    private static void renderChestGui(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs, HandledScreenAccessor acc, Minecraft mc, String title) {
        if (TeslaMapsConfig.get().croesusKismetCount) drawKismetCount(ctx, cs, acc, mc);
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
            if (!TeslaMapsConfig.get().croesusCompact) {
                lines.addAll(r.breakdown);
                lines.add("§7Cost: §c-" + fmt(r.cost));
            }
            String col = r.profit() >= 0 ? "§a+" : "§c";
            lines.add("§fProfit: " + col + fmt(r.profit()) + (r.incomplete ? " §8(incomplete)" : ""));
            if (noKey(r)) lines.add("§c§lNO KUUDRA KEY");
            if (derpyWarn(List.of(r))) lines.add("§cDerpy is mayor: essence counts may be off");
            if (TeslaMapsConfig.get().croesusRerollProtect && r.profit() >= rerollMin() && hasRerollButton(cs))
                lines.add("§eReroll blocked §7(hold CTRL to reroll anyway)");
            drawPanel(ctx, mc, acc, lines);
            return;
        }
    }

    private static int kismetScreen;
    private static long kismetAt;
    private static String kismetLabel = "0";
    private static Slot kismetSlot;

    // SkyHanni kismetDungeonChestSetup: owned feathers (sack + inventory) on "Reroll Chest" until it was used
    private static void drawKismetCount(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs, HandledScreenAccessor acc, Minecraft mc) {
        long now = System.currentTimeMillis();
        if (System.identityHashCode(cs) != kismetScreen || now - kismetAt > 1000) {
            kismetScreen = System.identityHashCode(cs);
            kismetAt = now;
            kismetSlot = findReroll(cs);
            if (kismetSlot != null) kismetLabel = String.valueOf(kismetFeathers(mc));
        }
        Slot slot = kismetSlot;
        if (slot == null) return;
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.text(mc.font, kismetLabel, sx + 17 - mc.font.width(kismetLabel), sy + 9, 0xFF55FF55, true);
    }

    private static Slot findReroll(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty() || !strip(item.getHoverName().getString()).equals("Reroll Chest")) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()
                    && strip(lore.lines().get(lore.lines().size() - 1).getString()).equals("You already rerolled a chest!")) return null;
            return slot;
        }
        return null;
    }

    private static int kismetFeathers(Minecraft mc) {
        int n = 0;
        if (mc.player != null) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && "KISMET_FEATHER".equals(com.teslamaps.utils.ItemUtil.skyblockId(st))) n += st.getCount();
            }
        }
        int sack = 0;
        for (var hit : com.teslamaps.features.ItemIndex.search("Kismet Feather", com.teslamaps.features.ItemIndex.SACKS)) {
            sack = Math.max(sack, hit.count());
        }
        return n + sack;
    }

    private static final java.util.IdentityHashMap<ItemLore, Parsed> parsedCache = new java.util.IdentityHashMap<>();
    private static int parsedStamp;

    static Parsed parseLore(ItemLore lore) {
        if (lore == null) return new Parsed();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        int stamp = java.util.Objects.hash(System.identityHashCode(cfg), TeslaMapsConfig.version(), PriceManager.version(),
                cfg.croesusPriceSource, cfg.croesusKismetCost, cfg.croesusKuudraPetBonus, cfg.croesusKuudraSalvage, cfg.kuudraFaction,
                cfg.croesusKeyCost, cfg.croesusNoEssence, cfg.croesusAvgPrices);
        if (stamp != parsedStamp || parsedCache.size() > 256) {
            parsedStamp = stamp;
            parsedCache.clear();
        }
        Parsed cached = parsedCache.get(lore);
        if (cached != null) return cached;
        Parsed r = parse(lore, cfg);
        parsedCache.put(lore, r);
        return r;
    }

    private static Parsed parse(ItemLore lore, TeslaMapsConfig cfg) {
        Parsed r = new Parsed();
        r.noEssence = cfg.croesusNoEssence;
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
                if (l.equals("Dungeon Chest Key")) {
                    r.chestKey = true;
                    if (cfg.croesusKeyCost) {
                        double kc = price("DUNGEON_CHEST_KEY");
                        if (kc > 0) r.cost += kc;
                        else r.incomplete = true;
                    }
                }
                int key = KuudraKeys.keyIndex(l);
                if (key >= 0) {
                    r.keyIndex = key;
                    double kc = KuudraKeys.cost(key, cfg.croesusPriceSource);
                    if (kc < 0) r.incomplete = true;
                    else r.cost += kc;
                }
                continue;
            }

            Matcher ess = ESSENCE.matcher(l);
            if (ess.matches()) {
                int cnt = ess.group(2) != null ? (int) parseNum(ess.group(2)) : 1;
                double mult = ess.group(1).equalsIgnoreCase("Crimson") ? essenceBonus() : 1;
                double p = cnt * mult * price("ESSENCE_" + ess.group(1).toUpperCase());
                r.value += p;
                r.essence += p;
                r.breakdown.add(" §7" + l + ": " + (r.noEssence ? "§8" : "§6") + fmt(p));
                continue;
            }
            Matcher book = BOOK.matcher(l);
            if (book.matches()) {
                String base = bookBase(book.group(1));
                int level = roman(book.group(2));
                double p = WORTHLESS_BOOK.matcher("ENCHANTMENT_" + base + "_" + level).matches() ? 0 : bookValue(base, level);
                r.value += p;
                r.breakdown.add(" §7" + l + ": §6" + fmt(p));
                continue;
            }
            String itemName = l.replace("✪", "").trim();
            int qty = 1;
            Matcher q = ITEM_QTY.matcher(itemName);
            if (q.matches()) { itemName = q.group(1); qty = (int) parseNum(q.group(2)); }

            if (cfg.croesusKuudraSalvage && (KUUDRA_ARMOR.matcher(itemName).matches() || itemName.startsWith("Molten "))) {
                double ess2 = itemName.startsWith("Molten ") ? 600 : SALVAGE_BY_STARS[Math.min(7, stars(cmp))];
                double p = ess2 * essenceBonus() * price("ESSENCE_CRIMSON") * qty;
                r.value += p;
                r.breakdown.add(" §7" + l + " §8(salvage)§7: §6" + fmt(p));
                continue;
            }

            String id = PriceManager.idForName(itemName);
            if (id != null && WORTHLESS.contains(id)) {
                r.breakdown.add(" §7" + l + ": §80");
                continue;
            }
            double p = (id != null ? price(id) : 0) * qty;
            if (p <= 0) r.incomplete = true;
            r.value += p;
            r.breakdown.add(" §7" + l + ": §6" + (p > 0 ? fmt(p) : "?"));
        }
        if (r.kismet && cfg.croesusKismetCost) {
            double k = price("KISMET_FEATHER");
            if (k > 0) r.cost += k;
            else r.incomplete = true;
        }
        return r;
    }

    public record ChestValue(double value, double essence, double cost, boolean kismet, boolean incomplete) {}

    public static ChestValue evaluate(ItemLore lore) {
        Parsed p = parseLore(lore);
        return new ChestValue(p.value, p.essence, p.cost, p.kismet, p.incomplete);
    }

    private static boolean derpyWarn(List<Parsed> rs) {
        if (!TeslaMapsConfig.get().croesusDerpyWarn || !"Derpy".equalsIgnoreCase(com.teslamaps.api.ElectionApi.mayor())) return false;
        for (Parsed r : rs) if (r.essence > 0) return true;
        return false;
    }

    private static double rerollMin() {
        return TeslaMapsConfig.get().croesusRerollProtectMin * 1_000_000.0;
    }

    private static long rerollMsgAt;

    private static boolean hasRerollButton(AbstractContainerScreen<?> cs) {
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = s.getItem();
            if (!item.isEmpty() && strip(item.getHoverName().getString()).equals("Reroll Chest")) return true;
        }
        return false;
    }

    /** True = swallow this click on the Reroll Chest button. */
    public static boolean blockReroll(AbstractContainerScreen<?> cs, Slot slot) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.croesusRerollProtect || slot == null || slot.container instanceof net.minecraft.world.entity.player.Inventory) return false;
        ItemStack item = slot.getItem();
        if (item.isEmpty() || !strip(item.getHoverName().getString()).equals("Reroll Chest")) return false;
        if (!isChestGui(strip(cs.getTitle().getString()))) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasControlDown()) return false;
        Parsed best = null;
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemLore lore = s.getItem().get(DataComponents.LORE);
            if (lore == null) continue;
            boolean hasContents = false;
            for (Component cmp : lore.lines()) if (strip(cmp.getString()).equals("Contents")) { hasContents = true; break; }
            if (!hasContents) continue;
            best = parseLore(lore);
            break;
        }
        if (best == null || best.profit() < rerollMin()) return false;
        long now = System.currentTimeMillis();
        if (mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
            if (now - rerollMsgAt > 1500) {
                rerollMsgAt = now;
                mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] Blocked rerolling a " + fmt(best.profit())
                        + " chest. §7Hold CTRL and click to reroll anyway."));
            }
        }
        return true;
    }

    private static boolean noKey(Parsed r) {
        return r.keyIndex >= 0 && !r.opened && TeslaMapsConfig.get().croesusKuudraNoKeyWarn && !KuudraKeys.hasKey(r.keyIndex);
    }

    private static double price(String id) {
        if (TeslaMapsConfig.get().croesusAvgPrices) {
            Double avg = PriceManager.averageOrNull(id);
            if (avg != null && avg > 0) return avg;
        }
        int src = TeslaMapsConfig.get().croesusPriceSource;
        if (src == PriceManager.SOURCE_BAZAAR_BUY) return PriceManager.getPrice(id);
        Double p = PriceManager.getPriceOrNull(id, src);
        return p != null ? p : 0;
    }

    private static double essenceBonus() {
        return 1 + TeslaMapsConfig.get().croesusKuudraPetBonus / 100.0;
    }

    /** Odin's Vesuvius star count: gold ✪ = 1, pink (master) ✪ = 2. */
    private static int stars(Component c) {
        int[] n = {0};
        net.minecraft.network.chat.TextColor gold = net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD);
        net.minecraft.network.chat.TextColor pink = net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        c.visit((style, str) -> {
            int count = 0;
            for (int i = 0; i < str.length(); i++) if (str.charAt(i) == '✪') count++;
            if (count > 0) {
                if (pink.equals(style.getColor())) n[0] += count * 2;
                else if (gold.equals(style.getColor())) n[0] += count;
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return n[0];
    }

    private static String bookBase(String name) {
        return NON_ALNUM.matcher(name.toUpperCase().replace("'", "")).replaceAll("").trim().replace(" ", "_");
    }

    private static double bookValue(String base, int level) {
        double p = price("ENCHANTMENT_" + base + "_" + level);
        if (p > 0) return p;
        return price("ENCHANTMENT_ULTIMATE_" + base + "_" + level); // ultimate books drop the "Ultimate" word
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
        TeslaMaps.debug("[CroesusDump] TITLE: '{}'", title);
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) continue;
            TeslaMaps.debug("[CroesusDump] === {} (nameStruck={}) ===",
                    strip(item.getHoverName().getString()), isStruck(item.getHoverName()));
            for (Component cmp : lore.lines())
                TeslaMaps.debug("[CroesusDump]  |{} {}", isStruck(cmp) ? " [STRUCK]" : "", strip(cmp.getString()));
        }
    }

    private static double parseNum(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (Exception e) { return 0; }
    }

    static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (a >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}
