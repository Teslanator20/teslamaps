package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// Ported from SkyHanni's SkyblockGuideHighlightFeature. Marks what is still MISSING in a guide-style
// menu, not what is done: one menu pattern plus one lore condition per entry.
public class GuideHighlight {

    private record Entry(BooleanSupplier on, Pattern menu, Pattern condition, boolean wiki,
                         boolean checkProgress) {}

    // conditions run against the stripped name/lore line and have to match it whole. The "\d{1,2}"
    // in the progress ones is what excludes 100% - that is the SkyHanni trick, kept as is.
    private static final Pattern X = Pattern.compile("\\s*✖.*");
    private static final Pattern ONCE = Pattern.compile("This task can only be completed once!");
    private static final Pattern TOTAL = Pattern.compile("Total Progress: \\d{1,2}(?:\\.\\d)?%");
    private static final Pattern CATEGORY =
            Pattern.compile("Progress to Complete Category: \\d{1,2}(?:\\.\\d)?%");
    private static final Pattern MINION =
            Pattern.compile("\\s*✖.*|You haven't crafted this minion\\.");
    private static final Pattern CONSUMABLE = Pattern.compile("This task can be completed \\d+ times!");
    private static final Pattern DUNGEON_FLOOR =
            Pattern.compile("This task can only be completed once!|You have not unlocked the content.*");
    private static final Pattern COLLECTION = Pattern.compile(
            "Progress to .*|Find this item to add it to your.*|Kill this boss once to view collection.*"
                    + "|(?:Boss )?Collections (?:Unlocked|Maxed Out): .*");
    private static final Pattern INCOMPLETE = Pattern.compile("INCOMPLETE");

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static void add(BooleanSupplier on, String menu, Pattern condition) {
        add(on, menu, condition, false);
    }

    private static void add(BooleanSupplier on, String menu, Pattern condition, boolean wiki) {
        ENTRIES.add(new Entry(on, Pattern.compile(menu), condition, wiki, false));
    }

    private static void addProgress(BooleanSupplier on, String menu, Pattern condition) {
        ENTRIES.add(new Entry(on, Pattern.compile(menu), condition, false, true));
    }

    static {
        add(() -> cfg().guideMissingTasks, "Guide ➜", X);
        add(() -> cfg().guideAbiphone, "Miscellaneous ➜ Abiphone Contac", ONCE);
        add(() -> cfg().guideOneTime, "Core ➜ Bank Upgrades", ONCE);
        add(() -> cfg().guideStory, "Core ➜ Fast Travels Unlocked", ONCE, true);
        add(() -> cfg().guideOneTime, "Event ➜ Spooky Festival", ONCE);
        add(() -> cfg().guideOneTime, "Miscellaneous ➜ The Dojo", ONCE);
        add(() -> cfg().guideJacob, "Event ➜ Jacob's Farming Contest", ONCE);
        add(() -> cfg().guideOneTime, "Slaying ➜ ", ONCE);
        add(() -> cfg().guideStory, "Story ➜ Complete Objectives", ONCE);
        add(() -> cfg().guideOneTime, "Mining ➜ Rock Milestones", ONCE);
        add(() -> cfg().guideOneTime, "Fishing ➜ Dolphin Milestones", ONCE);
        add(() -> cfg().guideEssence, "Essence Shop ➜", X);
        add(() -> cfg().guideMinion, "(?:\\(\\d/\\d\\) )?(?:Crafted Minions|\\w+ ➜ Minions)", MINION);
        add(() -> cfg().guideStory, "Miscellaneous ➜ Harp Songs", X);
        add(() -> cfg().guideConsumable, "Miscellaneous ➜ Consumable Items", CONSUMABLE, true);
        add(() -> cfg().guideOneTime, "Complete Dungeons ➜", DUNGEON_FLOOR);
        add(() -> cfg().guideOneTime, "Dungeon ➜ Complete Dungeons", X);
        add(() -> cfg().guideMenu, "Tasks ➜ ", TOTAL);
        add(() -> cfg().guideMenu, "Skill Related Tasks", CATEGORY);
        addProgress(() -> cfg().guideCollection, "Collections", COLLECTION);
        add(() -> cfg().guideCentury, "Raffle Tasks", INCOMPLETE);
    }

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();
    private static Object lastScreen;
    private static Entry active;

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { lastScreen = null; raffleScreen = null; return; }
        if (raffle(ctx, cs)) return;

        Entry entry = entryFor(cs);
        if (entry == null) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int col = TeslaMapsConfig.parseColor(cfg().colorGuideMissing) & 0x00FFFFFF;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            if (slot.index == 4) continue; // the overview item at the top
            if (!isMissing(entry, slot.getItem())) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
        }
    }

    // Ported from Skyblocker's RaffleRewardHighlight + RaffleTaskHighlight
    private static final IdentityHashMap<ItemStack, Integer> raffleCache = new IdentityHashMap<>();
    private static Object raffleScreen;
    private static boolean raffleMenu;

    private static boolean raffle(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs) {
        if (!cfg().guideHighlight || !cfg().raffleHighlight) return false;
        if (cs != raffleScreen) {
            raffleScreen = cs;
            raffleCache.clear();
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            raffleMenu = title.equals("Previous Raffles") || title.equals("Raffle Tasks");
        }
        if (!raffleMenu) return false;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            Integer col = raffleCache.get(item);
            if (col == null) {
                if (raffleCache.size() > 256) raffleCache.clear();
                col = raffleColor(item);
                raffleCache.put(item, col);
            }
            if (col == 0) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, col);
        }
        return true;
    }

    private static int raffleColor(ItemStack item) {
        List<String> lore = ItemUtil.loreLines(item);
        if (lore.isEmpty()) return 0;
        return switch (StringUtil.stripTrim(lore.get(lore.size() - 1))) {
            case "COMPLETE", "You claimed your prize!", "You've claimed this raffle!" -> 0x8055FF55;
            case "INCOMPLETE", "Click to reveal your prize!" -> 0x80FF5555;
            default -> 0;
        };
    }

    /** The wiki link SkyHanni puts on the unreachable travel scrolls and consumables. */
    public static void onSlotClick(AbstractContainerScreen<?> cs, Slot slot) {
        if (slot == null || cs == null) return;
        if (!cfg().guideWikiClick) return;
        Entry entry = entryFor(cs);
        if (entry == null || !entry.wiki()) return;
        if (slot.index == 4 || !isMissing(entry, slot.getItem())) return;

        String id = ItemUtil.skyblockId(slot.getItem());
        // the travel scrolls carry no usable id, they all land on the same wiki page anyway
        if (id.isEmpty()) id = "MUSEUM_TRAVEL_SCROLL";
        String cmd = "wiki " + id;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
        });
    }

    private static Entry entryFor(AbstractContainerScreen<?> cs) {
        if (!cfg().guideHighlight) return null;
        if (cs == lastScreen) return active;

        lastScreen = cs;
        cache.clear();
        active = null;

        String title = StringUtil.stripTrim(cs.getTitle().getString());
        for (Entry e : ENTRIES) {
            if (!e.on().getAsBoolean()) continue;
            if (e.menu().matcher(title).find()) { active = e; break; }
        }
        return active;
    }

    private static boolean isMissing(Entry entry, ItemStack item) {
        if (item.isEmpty()) return false;
        Boolean cached = cache.get(item);
        if (cached != null) return cached;
        if (cache.size() > 512) cache.clear();

        boolean missing = matches(entry, item.getHoverName().getString());
        if (!missing) {
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore != null) {
                for (Component cmp : lore.lines()) {
                    if (matches(entry, cmp.getString())) {
                        missing = true;
                        break;
                    }
                }
            }
        }
        cache.put(item, missing);
        return missing;
    }

    private static boolean matches(Entry entry, String raw) {
        String line = StringUtil.stripTrim(raw);
        if (!entry.condition().matcher(line).matches()) return false;
        return !entry.checkProgress() || !complete(line);
    }

    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final Pattern FRACTION =
            Pattern.compile("([\\d,]+)\\s*/\\s*([\\d,]+)");

    // "Collections Unlocked: 18/18" or "Progress to Tier V: 100%" is done, not missing.
    // Stripping the colour codes loses hypixel's own §a/§e hint, so read the numbers.
    private static boolean complete(String line) {
        var percent = PERCENT.matcher(line);
        if (percent.find()) {
            try {
                if (Double.parseDouble(percent.group(1)) >= 100) return true;
            } catch (NumberFormatException ignored) {}
        }
        var fraction = FRACTION.matcher(line);
        if (fraction.find()) {
            long have = number(fraction.group(1));
            long need = number(fraction.group(2));
            if (need > 0 && have >= need) return true;
        }
        return false;
    }

    private static long number(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
