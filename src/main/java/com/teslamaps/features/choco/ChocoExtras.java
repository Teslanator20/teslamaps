package com.teslamaps.features.choco;

// After SkyHanni's CFTimeTowerManager, Skyblocker's TimeTowerReminder, CFBarnManager, CFShopPrice, hitman/HitmanSlots, CFBlockOpen, CFKeybinds, CFTooltipCompact
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hud.EffectTimers;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChocoExtras {

    static final long TT_CHARGE_MS = 8 * 3_600_000L;

    private static final Pattern TOWER = Pattern.compile("^TIME TOWER! Your Chocolate Factory production has increased by \\+[\\d.]+x for (\\d+)h!$");
    private static final Pattern CHOC_NAME = Pattern.compile("^[\\d,]+ Chocolate$");
    private static final Pattern DUPE = Pattern.compile("^DUPLICATE RABBIT! \\+([\\d,]+) Chocolate");
    private static final Pattern CRUSHED = Pattern.compile("^BARN FULL! .+ got crushed! \\+([\\d,]+) Chocolate");
    private static final Pattern COST = Pattern.compile("Cost ([\\d,]+) Chocolate");
    private static final Pattern COIN_COST = Pattern.compile("Cost ([\\d,]+) Coins");
    private static final Pattern SPENT = Pattern.compile("^Chocolate Spent: ([\\d,]+)");
    private static final Pattern OPEN_CMD = Pattern.compile("(?i)^(?:cf|(?:chocolate)?factory)(?: .*)?$");
    private static final List<String> SHOP_SAMPLE = List.of("§e§lCoins per million chocolate", "§eChocolate spent: §61,234,567",
            "§aSupreme Chocolate Bar §6§l120k", "§5Rabbit Egg §6§l45k");

    private static HudEditElement shopHud;
    private static List<String> shopLines = List.of();
    private static int shopWidth, ticks;
    private static boolean inShop, inHitman;
    private static String hitmanLine = "";
    private static boolean ttNewCharge, barnWarned;
    private static long lastTtFullWarn, lastKey, lastChocClick, lastCmdBlock, lastMixinHint;

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
                    return c.cfTimeTowerWarning || c.cfTimeTowerExpired || c.cfBarnWarning || c.cfDupeTime || c.cfShopPrice
                            || c.cfHitman || c.cfBlockOpen || c.cfKeybinds || c.cfCompactTooltip;
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
                    b.toggle("Duplicate Rabbit Production Time", () -> c.cfDupeTime, v -> c.cfDupeTime = v)
                            .desc("After a duplicate rabbit: how much production time its chocolate equals.");
                    b.toggle("Chocolate Shop Price", () -> c.cfShopPrice, v -> c.cfShopPrice = v)
                            .desc("Panel in the Chocolate Shop with coins per million chocolate for each item. Position in /tmap gui");
                    b.toggle("Rabbit Hitman Info", () -> c.cfHitman, v -> c.cfHitman = v)
                            .desc("Eggs / slots on the Hitman slot in the factory, and bought / remaining slots with their coin cost in the Hitman menu.");
                    b.toggle("Block Opening The Factory", () -> c.cfBlockOpen, v -> c.cfBlockOpen = v)
                            .desc("Stops /cf and the factory items while a requirement below is missing.");
                    b.toggle("  Without Mythic Rabbit Pet", () -> c.cfBlockNoRabbit, v -> c.cfBlockNoRabbit = v);
                    b.toggle("  Without Booster Cookie", () -> c.cfBlockNoCookie, v -> c.cfBlockNoCookie = v);
                    b.toggle("  Without Hot Chocolate Mixin", () -> c.cfBlockNoMixin, v -> c.cfBlockNoMixin = v)
                            .desc("Needs Effect Timers; open /effects once so the mixin time is known. Unknown = only a chat hint, no block.");
                    b.toggle("Rabbit Keybinds", () -> c.cfKeybinds, v -> c.cfKeybinds = v)
                            .desc("Keys that click the 7 rabbit employees in the factory.");
                    b.keybind("  Rabbit 1", () -> c.cfKey1, v -> c.cfKey1 = v);
                    b.keybind("  Rabbit 2", () -> c.cfKey2, v -> c.cfKey2 = v);
                    b.keybind("  Rabbit 3", () -> c.cfKey3, v -> c.cfKey3 = v);
                    b.keybind("  Rabbit 4", () -> c.cfKey4, v -> c.cfKey4 = v);
                    b.keybind("  Rabbit 5", () -> c.cfKey5, v -> c.cfKey5 = v);
                    b.keybind("  Rabbit 6", () -> c.cfKey6, v -> c.cfKey6 = v);
                    b.keybind("  Rabbit 7", () -> c.cfKey7, v -> c.cfKey7 = v);
                    b.toggle("Compact Chocolate Tooltip", () -> c.cfCompactTooltip, v -> c.cfCompactTooltip = v)
                            .desc("The big chocolate item shrinks its tooltip to name + amount for a second after you click it.");
                    b.toggle("  Always Compact", () -> c.cfCompactTooltipAlways, v -> c.cfCompactTooltipAlways = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, tip) -> {
            if (cfg().cfCompactTooltip && ChocoData.inFactory() && cfg().section(ChocoData.CATEGORY)) compact(stack, tip);
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(ChocoExtras::allowCommand);
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        String title = mc.screen instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        inShop = title.equals("Chocolate Shop");
        inHitman = title.equals("Rabbit Hitman");
        if (ticks % 10 == 0 && mc.screen instanceof AbstractContainerScreen<?> cs) {
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
                mc.gui.setTimes(0, 50, 10);
                mc.gui.setTitle(Component.literal("§dTime Tower ran out"));
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
        Matcher m;
        if (c.cfBarnWarning && (m = CRUSHED.matcher(t)).find()) {
            barnWarned = false;
            checkBarn();
            return;
        }
        if (c.cfDupeTime && (m = DUPE.matcher(t)).find() && s.cps > 0) {
            double amount = ChocoData.num(m.group(1));
            String time = ChocoData.format((long) (amount / s.cps * 1000));
            TickScheduler.schedule(1, () -> ChocoData.chat("§7(§a+§b" + time + " §aof production§7)"));
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
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
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

    // block open

    private static boolean allowCommand(String command) {
        if (!cfg().cfBlockOpen || !cfg().section(ChocoData.CATEGORY) || !SkyblockUtils.isInSkyblock()) return true;
        if (!OPEN_CMD.matcher(command.trim()).matches()) return true;
        long now = System.currentTimeMillis();
        if (now - lastCmdBlock < 5000) return true;
        String why = missing();
        if (why == null) return true;
        lastCmdBlock = now;
        ChocoData.chat("§cBlocked opening the Chocolate Factory " + why + "§c! §7Send it again within 5 s to open anyway.");
        return false;
    }

    private static String missing() {
        TeslaMapsConfig c = cfg();
        if (c.cfBlockNoRabbit) {
            String pet = PetTracker.displayName();
            if (pet == null || !StringUtil.strip(pet).contains("Rabbit") || !"MYTHIC".equals(PetTracker.rarity())) return "§7without a §dMythic Rabbit Pet";
        }
        if (c.cfBlockNoCookie && !cookieActive()) return "§7without a §dBooster Cookie";
        if (c.cfBlockNoMixin) {
            long exp = EffectTimers.expiresAt("Hot Chocolate Mixin I"), now = System.currentTimeMillis();
            if (c.effectTimers && exp > 0 && exp < now) return "§7without a §6Hot Chocolate Mixin";
            if ((!c.effectTimers || exp <= 0) && now - lastMixinHint > 5 * 60_000L) {
                lastMixinHint = now;
                ChocoData.chat("§eHot Chocolate Mixin unknown §7(turn on Effect Timers, open /effects once)§e, not blocking.");
            }
        }
        return null;
    }

    private static boolean cookieActive() {
        String f = StringUtil.strip(com.teslamaps.dungeon.DungeonInfoHud.footer());
        int i = f.indexOf("Cookie Buff");
        if (i < 0) return true;
        return !f.substring(i + 11).trim().startsWith("Not active");
    }

    /** From HandledScreenClickMixin.slotClicked; true cancels. */
    public static boolean onSlotClick(AbstractContainerScreen<?> cs, Slot slot, int button, ContainerInput type) {
        if (slot == null || slot.container instanceof Inventory || !cfg().section(ChocoData.CATEGORY)) return false;
        if (!cfg().cfBlockOpen && !ChocoData.anyEnabled()) return false;
        if (ChocoStrays.blockClick(slot)) return true;
        ChocoReminder.onSlotClick(slot, button);
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return false;
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        if (ChocoData.inFactory() && name.endsWith(" Chocolate") && CHOC_NAME.matcher(name).matches()) lastChocClick = System.currentTimeMillis();
        if (cfg().cfBlockOpen && SkyblockUtils.isInSkyblock() && (name.equals("Chocolate Factory") || name.equals("Open Chocolate Factory"))
                && !StringUtil.stripTrim(cs.getTitle().getString()).contains("Enchanted Clock")) {
            String why = missing();
            if (why != null) {
                ChocoData.chat("§cBlocked opening the Chocolate Factory " + why + "§c!");
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1f, 1f));
                return true;
            }
        }
        return false;
    }

    // keybinds

    /** From ContainerKeyMixin. */
    public static boolean onKey(AbstractContainerScreen<?> cs, int key) {
        TeslaMapsConfig c = cfg();
        if (!c.cfKeybinds || !ChocoData.inFactory() || !c.section(ChocoData.CATEGORY) || key <= 0) return false;
        int[] keys = {c.cfKey1, c.cfKey2, c.cfKey3, c.cfKey4, c.cfKey5, c.cfKey6, c.cfKey7};
        List<Integer> rabbits = ChocoData.rabbitSlots;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != key) continue;
            if (i >= rabbits.size()) return true;
            long now = System.currentTimeMillis();
            if (now - lastKey < 200) return true;
            lastKey = now;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null && mc.player != null)
                mc.gameMode.handleContainerInput(cs.getMenu().containerId, rabbits.get(i), 2, ContainerInput.CLONE, mc.player);
            return true;
        }
        return false;
    }

    // compact tooltip

    private static void compact(ItemStack stack, List<Component> tip) {
        if (tip.isEmpty()) return;
        ItemLore lore = ItemUtil.lore(stack);
        if (lore == null) return;
        List<Component> lines = lore.lines();
        boolean isChoc = false;
        for (Component l : lines) if (l.getString().contains("Click to uncover the meaning of life!")) { isChoc = true; break; }
        if (!isChoc) return;
        if (!cfg().cfCompactTooltipAlways && System.currentTimeMillis() - lastChocClick > 1000) return;
        Component name = tip.get(0);
        tip.clear();
        tip.add(name);
        if (lines.size() > 5) tip.add(lines.get(5));
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }
}
