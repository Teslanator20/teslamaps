package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Hunting Box and the Attribute Menu into {@link ShardData}, and keeps the
 * stored amounts in step with the syphon / "sent to your Hunting Box" chat lines.
 */
public class HuntingBox {

    private static final Pattern BOX_TITLE = Pattern.compile("(?:\\((?<page>\\d+)/(?<pages>\\d+)\\) )?Hunting Box");
    private static final Pattern MENU_TITLE = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Attribute Menu");
    private static final Pattern BAZAAR_TITLE = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Oddities ➜ Shards");

    private static final Pattern OWNED = Pattern.compile("Owned: (?<amount>[\\d,]+) Shards?");
    private static final Pattern SYPHON = Pattern.compile("Syphon (?<amount>\\d+) shards? to (?:level up|unlock)!");
    private static final Pattern LORE_NAME = Pattern.compile("(?<name>.+?) ?(?<tier>[IVXL]+)? \\(\\w+\\)$");
    private static final Pattern MENU_NAME = Pattern.compile("(?<name>.+?) ?(?<tier>[IVXL]+)?$");
    private static final Pattern QUERY = Pattern.compile("Query: .+");

    private static final Pattern SENT_TO_BOX =
            Pattern.compile("You sent (?:an?|(?<amount>\\d+)) (?<shardName>.+) Shards? to your Hunting Box\\.");
    private static final Pattern SYPHONED = Pattern.compile(
            "\\+(?<amount>\\d+) (?<attributeName>.+) Attribute \\(Level (?<level>\\d+)\\) - (?<untilNext>\\d+) more to upgrade!");
    private static final Pattern SYPHONED_MAXED =
            Pattern.compile("\\+(?<amount>\\d+) (?<attributeName>.+) Attribute \\(Level (?<level>\\d+)\\) MAXED");
    // Fusion and "and N more" from SkyHanni's AttributeShardsData.kt / FusionData.kt
    private static final Pattern FUSION =
            Pattern.compile("FUSION! You obtained(?: an?)? (?<shardName>.+) Shard(?: x(?<amount>\\d+))?!(?: NEW!)?");
    private static final Pattern AND_MORE = Pattern.compile("and (?<amount>\\d+) more\\.\\.\\.");
    private static final Pattern REQUIRED = Pattern.compile("Required to fuse: (?<amount>\\d+)");

    private record Fusion(ShardData.Shard first, int firstAmount, ShardData.Shard second, int secondAmount, ShardData.Shard output) {}
    private static Fusion fusion;
    private static long lastSyphon;

    /** Slots holding shards - the box uses the usual bordered 9x6 layout. */
    private static boolean isShardSlot(int index) {
        if (index < 9 || index > 44) return false;
        int col = index % 9;
        return col != 0 && col != 8;
    }

    private static final Set<String> seenShards = new HashSet<>();
    private static final Set<Integer> seenPages = new HashSet<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean screenChanged = mc.screen != lastScreen;
        refreshScreen(mc);   // before any early return, the render side reads these every frame

        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.huntingBoxValue && !config.shardOverlay
                && !config.shardTierStack && !config.shardHighlightDisabled && !config.shardHighlightProgress) {
            ShardOverlay.update();   // drops whatever is still cached
            return;
        }
        ShardData.load();
        if (!ShardData.loaded()) return;

        ticks++;
        if (ticks % 10 == 0) scanPlayerInventory(mc);
        // menu contents barely move; a fresh read every 5 ticks is plenty, but a new screen
        // (page turn, menu switch) has to be read at once
        if (screenChanged) packetRead = false;
        int version = com.teslamaps.utils.ContainerState.version();
        boolean packetChanged = version != lastVersion && !packetRead;
        lastVersion = version;
        if (!screenChanged && !packetChanged && ticks % 5 != 0) return;
        if (packetChanged && !screenChanged) packetRead = true;   // first fill only, then every 5 ticks

        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            if (config.shardFusionTracking && screenTitle.equals("Confirm Fusion")) readFusion(cs);
            if (boxScreen) readBox(cs, screenTitle);
            else if (menuScreen) readAttributeMenu(cs);
            else BazaarOrders.tick();
        }
        // also runs with no menu open, which is what clears the cached panel
        ShardOverlay.update();
    }

    private static int ticks = 0;
    private static int lastVersion = -1;
    private static boolean packetRead;
    private static Object lastScreen;
    private static String screenTitle = "";
    private static boolean boxScreen, menuScreen, bazaarScreen;

    /** One title regex per tick instead of one per call - these are asked for every frame. */
    private static void refreshScreen(Minecraft mc) {
        lastScreen = mc.screen;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            screenTitle = "";
            boxScreen = menuScreen = bazaarScreen = false;
            return;
        }
        screenTitle = StringUtil.stripTrim(cs.getTitle().getString());
        boxScreen = BOX_TITLE.matcher(screenTitle).matches();
        menuScreen = !boxScreen && MENU_TITLE.matcher(screenTitle).matches();
        bazaarScreen = !boxScreen && !menuScreen && BAZAAR_TITLE.matcher(screenTitle).matches();
    }

    /**
     * An instant buy drops the shards into the inventory, so they count as already bought.
     * A real shard always carries its id, so this never touches an item's hover name.
     */
    private static void scanPlayerInventory(Minecraft mc) {
        if (mc.player == null) {
            ShardData.setInventory(java.util.Map.of());
            return;
        }
        java.util.Map<String, Integer> counts = null;
        for (ItemStack stack : mc.player.getInventory()) {
            if (stack.isEmpty()) continue;
            String id = ItemUtil.skyblockId(stack);
            if (id.isEmpty() || !id.startsWith("SHARD_")) continue;
            ShardData.Shard shard = ShardData.bySkyblockId(id);
            if (shard == null) continue;
            if (counts == null) counts = new java.util.HashMap<>();
            counts.merge(shard.bazaarId(), stack.getCount(), Integer::sum);
        }
        ShardData.setInventory(counts == null ? java.util.Map.of() : counts);
    }

    public static boolean inBoxScreen() { return boxScreen; }

    public static boolean inOverlayScreen() { return menuScreen || bazaarScreen; }

    // ---------------------------------------------------------------- hunting box

    public record BoxEntry(ShardData.Shard shard, int amount, ItemStack stack, int slot) {}

    private static List<BoxEntry> boxEntries = List.of();

    public static List<BoxEntry> boxEntries() { return boxEntries; }

    private static void readBox(AbstractContainerScreen<?> cs, String title) {
        List<Slot> slots = cs.getMenu().slots;
        List<BoxEntry> entries = new ArrayList<>();
        Set<String> present = new HashSet<>();
        boolean searchActive = false;

        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            if (!isShardSlot(i)) {
                if (!searchActive) {
                    for (String line : lore(stack)) {
                        if (QUERY.matcher(line).matches()) { searchActive = true; break; }
                    }
                }
                continue;
            }
            ShardData.Shard shard = resolve(stack);
            if (shard == null) continue;

            int owned = 0, tier = 0, toNext = 0;
            for (String line : lore(stack)) {
                Matcher o = OWNED.matcher(line);
                if (o.matches()) { owned = parseInt(o.group("amount")); continue; }
                Matcher s = SYPHON.matcher(line);
                if (s.matches()) { toNext = parseInt(s.group("amount")); continue; }
                Matcher n = LORE_NAME.matcher(line);
                if (n.matches()) tier = ShardData.romanToInt(n.group("tier"));
            }

            ShardData.setInBox(shard, owned);
            if (ShardData.isConsumable(shard)) {
                ShardData.setSyphoned(shard, ShardData.totalSyphoned(shard, tier, toNext));
            }
            present.add(shard.bazaarId());
            entries.add(new BoxEntry(shard, owned, stack.copy(), i));
        }

        boxEntries = entries;
        syncPages(title, present, searchActive);
    }

    /** A shard missing from the box is gone, not unknown - but only once every page was seen. */
    private static void syncPages(String title, Set<String> present, boolean searchActive) {
        if (searchActive) return;
        Matcher m = BOX_TITLE.matcher(title);
        if (!m.matches()) return;
        int page = m.group("page") == null ? 1 : parseInt(m.group("page"));
        int pages = m.group("pages") == null ? 1 : parseInt(m.group("pages"));

        if (page == 1) {
            seenShards.clear();
            seenPages.clear();
        }
        seenShards.addAll(present);
        seenPages.add(page);
        if (seenPages.size() < pages) return;

        for (String id : new ArrayList<>(TeslaMapsConfig.get().shardsInBox.keySet())) {
            if (!seenShards.contains(id)) TeslaMapsConfig.get().shardsInBox.put(id, 0);
        }
    }

    // ---------------------------------------------------------------- attribute menu

    private static void readAttributeMenu(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            Matcher m = MENU_NAME.matcher(name);
            if (!m.matches()) continue;
            ShardData.Shard shard = ShardData.byAbilityName(m.group("name"));
            if (shard == null) shard = resolve(stack);
            if (shard == null || !ShardData.isConsumable(shard)) continue;

            int tier = ShardData.romanToInt(m.group("tier"));
            int toNext = 0;
            for (String line : lore(stack)) {
                Matcher s = SYPHON.matcher(line);
                if (s.matches()) { toNext = parseInt(s.group("amount")); break; }
            }
            ShardData.setSyphoned(shard, ShardData.totalSyphoned(shard, tier, toNext));
        }
    }

    private static final Pattern STATE = Pattern.compile("Enabled: (?<state>.+)");

    /** True only for an attribute the menu explicitly reports as switched off. */
    public static boolean isDisabled(ItemStack stack) {
        for (String line : lore(stack)) {
            Matcher m = STATE.matcher(line);
            if (m.matches()) return !"Yes".equals(m.group("state"));
        }
        return false;
    }

    /** Menu tier for the stack-size overlay, without another lore parse. */
    public static int menuTier(ItemStack stack) {
        ShardData.Shard shard = shardOf(stack);
        if (shard == null) return -1;
        return ShardData.tiers(shard, ShardData.syphoned(shard))[0];
    }

    public static ShardData.Shard shardOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        Matcher m = MENU_NAME.matcher(name);
        if (m.matches()) {
            ShardData.Shard byAbility = ShardData.byAbilityName(m.group("name"));
            if (byAbility != null) return byAbility;
        }
        return resolve(stack);
    }

    private static void readFusion(AbstractContainerScreen<?> cs) {
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= 31) return;
        ItemStack a = slots.get(12).getItem(), b = slots.get(14).getItem(), out = slots.get(31).getItem();
        ShardData.Shard first = shardOf(a), second = shardOf(b), output = shardOf(out);
        int firstAmount = required(a), secondAmount = required(b);
        if (first == null || second == null || output == null || firstAmount < 0 || secondAmount < 0) return;
        fusion = new Fusion(first, firstAmount, second, secondAmount, output);
    }

    private static int required(ItemStack stack) {
        for (String line : lore(stack)) {
            Matcher m = REQUIRED.matcher(line);
            if (m.find()) return parseInt(m.group("amount"));
        }
        return -1;
    }

    // ---------------------------------------------------------------- chat

    public static void onChatMessage(String raw) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.huntingBoxValue && !config.shardOverlay) return;
        if (!ShardData.loaded()) return;
        String text = StringUtil.stripTrim(raw);

        Matcher sent = SENT_TO_BOX.matcher(text);
        if (sent.matches()) {
            ShardData.Shard shard = ShardData.byDisplayName(sent.group("shardName"));
            if (shard != null) ShardData.addInBox(shard, sent.group("amount") == null ? 1 : parseInt(sent.group("amount")));
            return;
        }

        if (config.shardFusionTracking && fusion != null) {
            Matcher f = FUSION.matcher(text);
            if (f.matches()) {
                ShardData.addInBox(fusion.output(), f.group("amount") == null ? 1 : parseInt(f.group("amount")));
                ShardData.addInBox(fusion.first(), -fusion.firstAmount());
                ShardData.addInBox(fusion.second(), -fusion.secondAmount());
                return;
            }
        }

        if (config.shardSyphonHint && System.currentTimeMillis() - lastSyphon < 1000) {
            Matcher more = AND_MORE.matcher(text);
            if (more.matches()) {
                String n = more.group("amount");
                com.teslamaps.utils.TickScheduler.schedule(1, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return;
                    mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §aClick here and scroll through /attributemenu to refresh "
                                    + n + " more shards")
                            .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/attributemenu"))));
                });
                return;
            }
        }

        Matcher syphon = SYPHONED.matcher(text);
        if (syphon.matches()) {
            lastSyphon = System.currentTimeMillis();
            applySyphon(syphon.group("attributeName"), parseInt(syphon.group("level")),
                    parseInt(syphon.group("untilNext")), parseInt(syphon.group("amount")));
            return;
        }

        Matcher maxed = SYPHONED_MAXED.matcher(text);
        if (maxed.matches()) {
            lastSyphon = System.currentTimeMillis();
            applySyphon(maxed.group("attributeName"), 10, 0, parseInt(maxed.group("amount")));
            return;
        }

        BazaarOrders.onChatMessage(text);
    }

    private static void applySyphon(String abilityName, int level, int untilNext, int amount) {
        ShardData.Shard shard = ShardData.byAbilityName(abilityName);
        if (shard == null) return;
        ShardData.setSyphoned(shard, ShardData.totalSyphoned(shard, level, untilNext));
        ShardData.addInBox(shard, -amount);
    }

    // ---------------------------------------------------------------- helpers

    private static ShardData.Shard resolve(ItemStack stack) {
        ShardData.Shard shard = ShardData.bySkyblockId(ItemUtil.skyblockId(stack));
        if (shard != null) return shard;

        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        shard = ShardData.byDisplayName(name);
        if (shard != null) return shard;

        for (String line : lore(stack)) {
            Matcher m = LORE_NAME.matcher(line);
            if (!m.matches()) continue;
            shard = ShardData.byAbilityName(m.group("name"));
            if (shard != null) return shard;
        }
        return null;
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) out.add(StringUtil.stripTrim(line.getString()));
        return out;
    }

    private static int parseInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private HuntingBox() {}
}
