package com.teslamaps.features.choco;

// After SkyHanni's CFTimeTowerManager, Skyblocker's TimeTowerReminder, CFBarnManager, CFShopPrice, hitman/HitmanSlots
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChocoExtras {

    static final long TT_CHARGE_MS = 8 * 3_600_000L;

    private static final Pattern TOWER = Pattern.compile("^TIME TOWER! Your Chocolate Factory production has increased by \\+[\\d.]+x for (\\d+)h!$");
    private static final Pattern CRUSHED = Pattern.compile("^BARN FULL! .+ got crushed! \\+([\\d,]+) Chocolate");
    private static final Pattern COST = Pattern.compile("Cost ([\\d,]+) Chocolate");
    private static final Pattern COIN_COST = Pattern.compile("Cost ([\\d,]+) Coins");
    private static final Pattern SPENT = Pattern.compile("^Chocolate Spent: ([\\d,]+)");
    private static final List<String> SHOP_SAMPLE = List.of("§e§lCoins per million chocolate", "§eChocolate spent: §61,234,567",
            "§aSupreme Chocolate Bar §6§l120k", "§5Rabbit Egg §6§l45k");

    private static HudEditElement shopHud;
    private static List<String> shopLines = List.of();
    private static int shopWidth, ticks;
    private static boolean inShop, inHitman;
    private static String hitmanLine = "";
    private static boolean ttNewCharge, barnWarned;
    private static long lastTtFullWarn;

    private ChocoExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        shopHud = HudEditElement.of("cfShopPrice", "Chocolate Shop Price").section(ChocoData.CATEGORY)
                .enabled(() -> cfg().cfShopPrice)
                .size(() -> shopLines.isEmpty() ? TextHud.width(SHOP_SAMPLE) : shopWidth,
                        () -> TextHud.height(shopLines.isEmpty() ? SHOP_SAMPLE.size() : shopLines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, shopLines.isEmpty() ? SHOP_SAMPLE : shopLines,
                        shopLines.isEmpty() ? TextHud.width(SHOP_SAMPLE) : shopWidth));
        Feature.of("ChocoExtras").section(ChocoData.CATEGORY)
                .when(() -> {
                    TeslaMapsConfig c = cfg();
                    return c.cfTimeTowerWarning || c.cfTimeTowerExpired || c.cfBarnWarning || c.cfShopPrice || c.cfHitman;
                })
                .tick(ChocoExtras::tick)
                .serverChat(ChocoExtras::onChat)
                .hudForeground("cfshopprice", ChocoExtras::renderHud)
                .config(ChocoData.CATEGORY, b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Time Tower Warnings", () -> c.cfTimeTowerWarning, v -> c.cfTimeTowerWarning = v)
                            .desc("Chat line and beep when the Time Tower gets a new charge, and every 5 minutes while it is full.");
                    b.toggle("Time Tower Expired Reminder", () -> c.cfTimeTowerExpired, v -> c.cfTimeTowerExpired = v)
                            .desc("Title, chat line and beep when your Time Tower runs out, so you can use the next charge. Survives restarts.");
                    b.toggle("Barn Full Warning", () -> c.cfBarnWarning, v -> c.cfBarnWarning = v)
                            .desc("Warns when the barn is (almost) full so new rabbits don't get crushed. Barn slot red in the factory.");
                    b.slider("  Warn At Free Slots", 0, 20, () -> (float) c.cfBarnThreshold, v -> c.cfBarnThreshold = Math.round(v));
                    b.toggle("Chocolate Shop Price", () -> c.cfShopPrice, v -> c.cfShopPrice = v)
                            .desc("Panel in the Chocolate Shop with coins per million chocolate for each item. Position in /tmap gui");
                    b.toggle("Rabbit Hitman Info", () -> c.cfHitman, v -> c.cfHitman = v)
                            .desc("Eggs / slots on the Hitman slot in the factory, and bought / remaining slots with their coin cost in the Hitman menu.");
                });
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        String title = mc.gui.screen() instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        inShop = title.equals("Chocolate Shop");
        inHitman = title.equals("Rabbit Hitman");
        if (ticks % 10 == 0 && mc.gui.screen() instanceof AbstractContainerScreen<?> cs) {
            if (inShop && cfg().cfShopPrice) buildShop(cs);
            if (inHitman && cfg().cfHitman) buildHitman(cs);
        }
        if (ticks % 20 == 0 && SkyblockUtils.isInSkyblock()) timeTower();
    }

    // time tower

    private static void timeTower() {
        ChocoData.State s = ChocoData.state();
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = cfg();
        boolean active = s.ttActiveUntil > now;
        if (s.ttActiveUntil > 0 && !active) {
            s.ttActiveUntil = 0;
            ChocoData.changed();
            if (c.cfTimeTowerExpired) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.hud.setTimes(0, 50, 10);
                mc.gui.hud.setTitle(Component.literal("§dTime Tower ran out"));
                String left = s.ttCharges >= 0 ? " §7(" + s.ttCharges + " charge" + (s.ttCharges == 1 ? "" : "s") + " left)" : "";
                ChocoData.chatOpen("§dYour Time Tower has run out!" + left + " §eClick to open the factory.");
                ChocoData.beep();
            }
        }
        if (s.ttMax <= 0 || ChocoData.inFactory()) return;
        while (s.ttNextCharge > 0 && now >= s.ttNextCharge && s.ttCharges < s.ttMax) {
            s.ttCharges++;
            s.ttNextCharge += TT_CHARGE_MS;
            ttNewCharge = true;
            ChocoData.changed();
        }
        if (!c.cfTimeTowerWarning || active) return;
        if (s.ttCharges >= s.ttMax) {
            ttNewCharge = false;
            if (now - lastTtFullWarn < 5 * 60_000L) return;
            lastTtFullWarn = now;
            ChocoData.chatOpen("§cYour Time Tower is full §7(" + s.ttCharges + "/" + s.ttMax + " Charges)§c, use one so no charge is wasted! §eClick to open the factory.");
            ChocoData.beep();
        } else if (ttNewCharge && s.ttCharges > 0) {
            ttNewCharge = false;
            ChocoData.chatOpen("§eYour Time Tower has an available charge §7(" + s.ttCharges + "/" + s.ttMax + " Charges)§e. Click to open the factory.");
            ChocoData.beep();
        }
    }

    // barn / chat

    static void onFactoryOpened() {
        barnWarned = false;
        if (cfg().cfBarnWarning && cfg().section(ChocoData.CATEGORY)) checkBarn();
    }

    private static void checkBarn() {
        ChocoData.State s = ChocoData.state();
        if (barnWarned || s.barnMax <= 0 || s.barnMax - s.barnCur > cfg().cfBarnThreshold) return;
        barnWarned = true;
        String level = s.barnCur >= s.barnMax ? "full" : "almost full";
        ChocoData.chatOpen("§cYour barn is " + level + " §7(" + s.barnCur + "/" + s.barnMax + " Rabbits)§c. Upgrade it so rabbits don't get crushed!");
        ChocoData.beep();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        TeslaMapsConfig c = cfg();
        ChocoData.State s = ChocoData.state();
        if (t.startsWith("TIME TOWER!")) {
            Matcher tm = TOWER.matcher(t);
            if (tm.matches()) {
                s.ttActiveUntil = System.currentTimeMillis() + Long.parseLong(tm.group(1)) * 3_600_000L;
                ChocoData.changed();
            }
            return;
        }
        if (t.startsWith("NEW RABBIT!")) {
            if (s.barnCur >= 0) { s.barnCur++; ChocoData.changed(); }
            if (c.cfBarnWarning) { barnWarned = false; checkBarn(); }
            return;
        }
        if (c.cfBarnWarning && CRUSHED.matcher(t).find()) {
            barnWarned = false;
            checkBarn();
        }
    }

    // shop

    private static void buildShop(AbstractContainerScreen<?> cs) {
        record Row(String name, double perMil, boolean buyable) {}
        List<Row> rows = new ArrayList<>();
        long spent = -1;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            for (String l : lore) {
                Matcher sm = SPENT.matcher(l.trim());
                if (sm.find()) spent = (long) ChocoData.num(sm.group(1));
            }
            Matcher m = COST.matcher(String.join(" ", lore));
            if (!m.find()) continue;
            double choc = ChocoData.num(m.group(1));
            String id = ItemUtil.skyblockId(st);
            double price = id == null ? 0 : PriceManager.getPrice(id);
            if (choc <= 0 || price <= 0) continue;
            boolean buyable = true;
            for (String l : lore) if (l.contains("Maxed out") || l.contains("Can't afford") || l.contains("Requires")) buyable = false;
            rows.add(new Row(st.getHoverName().getString(), price / choc * 1_000_000, buyable));
        }
        rows.sort((a, b) -> Double.compare(b.perMil, a.perMil));
        List<String> out = new ArrayList<>();
        out.add("§e§lCoins per million chocolate");
        ChocoData.State s = ChocoData.state();
        if (s.chocolate >= 0) out.add("§eChocolate available: §6" + ChocoData.commas(s.chocolate));
        if (spent >= 0) out.add("§eChocolate spent: §6" + ChocoData.commas(spent));
        for (Row r : rows) out.add((r.buyable ? "§f" : "§7§m") + r.name + "§r §6§l" + com.teslamaps.utils.ItemPrice.format(r.perMil));
        if (rows.isEmpty()) out.add("§7No priced items");
        shopLines = out;
        shopWidth = TextHud.width(out);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!inShop || !cfg().cfShopPrice || shopLines.isEmpty() || shopHud == null) return;
        TextHud.draw(g, Minecraft.getInstance(), shopHud.x(), shopHud.y(), shopHud.scale(), shopLines, shopWidth);
    }

    // hitman

    private static void buildHitman(AbstractContainerScreen<?> cs) {
        int left = 0;
        long coins = 0, next = -1;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Matcher m = COIN_COST.matcher(String.join(" ", ItemUtil.loreLines(st)));
            if (!m.find()) continue;
            long c = (long) ChocoData.num(m.group(1));
            left++;
            coins += c;
            if (next < 0 || c < next) next = c;
        }
        ChocoData.State s = ChocoData.state();
        String bought = s.hitmanSlots >= 0 ? "§aBought: §f" + s.hitmanSlots + "  " : "";
        hitmanLine = left == 0 ? bought + "§7No slots left to buy"
                : bought + "§cRemaining: §f" + left + " §7(§6" + com.teslamaps.utils.ItemPrice.format(coins) + " coins§7, next §6"
                + com.teslamaps.utils.ItemPrice.format(next) + "§7)";
    }

    static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = cfg();
        if (!c.cfHitman || !c.section(ChocoData.CATEGORY)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (inHitman && !hitmanLine.isEmpty()) {
            ctx.centeredText(mc.font, hitmanLine, mc.getWindow().getGuiScaledWidth() / 2, Math.max(2, acc.getY() - 12), 0xFFFFFFFF);
        } else if (ChocoData.inFactory() && ChocoData.hitmanSlot >= 0) {
            ChocoData.State s = ChocoData.state();
            if (s.hitmanEggs < 0 || s.hitmanSlots < 0) return;
            String col = s.hitmanEggs == 0 ? "§7" : s.hitmanEggs >= s.hitmanSlots ? "§6§l" : "§e";
            String t = s.hitmanSlots == 0 ? "§70/0" : col + s.hitmanEggs + (s.hitmanSlots < s.hitmanMax ? "§7/§e" + s.hitmanSlots : "");
            ChocoFactory.text(ctx, mc, acc, cs.getMenu().slots, ChocoData.hitmanSlot, t);
        }
    }

    /** From HandledScreenClickMixin.slotClicked; true cancels. */
    public static boolean onSlotClick(AbstractContainerScreen<?> cs, Slot slot, int button, ContainerInput type) {
        if (slot == null || slot.container instanceof Inventory || !cfg().section(ChocoData.CATEGORY)) return false;
        if (!ChocoData.anyEnabled()) return false;
        if (ChocoStrays.blockClick(slot)) return true;
        ChocoReminder.onSlotClick(slot, button);
        return false;
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }
}
