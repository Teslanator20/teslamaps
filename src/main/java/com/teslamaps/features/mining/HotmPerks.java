package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Heart of the Mountain: perk tint, level/token stack sizes, powder costs and the Sky Mall readout. */
public class HotmPerks {

    private static final Pattern NOT_UNLOCKED = Pattern.compile("Requires.*|.*Mountain!|Click to unlock!");
    private static final Pattern LEVEL = Pattern.compile("Level (\\d+)(?:/(\\d+))?.*");
    private static final Pattern TOKENS = Pattern.compile("Tokens? of the Mountain: (\\d+)");
    private static final Pattern BUFF = Pattern.compile(" ?(?:New buff: |■ )?(.*)");
    private static final int NONE = 0, ENABLED = 1, DISABLED = 2;

    // Ported from SkyHanni's HotmData: cost of level n -> n+1 is floor((n + 1 + offset)^exp)
    private record Perk(int max, double exp, int offset, int powder) {}
    private static final int MITHRIL = 0, GEMSTONE = 1, GLACITE = 2;
    private static final String[] POWDER_NAMES = {"§2Mithril Powder", "§dGemstone Powder", "§bGlacite Powder"};
    private static final Map<String, Perk> PERKS = Map.ofEntries(
            Map.entry("Mining Speed", new Perk(50, 3.0, 0, MITHRIL)),
            Map.entry("Mining Fortune", new Perk(50, 3.05, 0, MITHRIL)),
            Map.entry("Titanium Insanium", new Perk(50, 3.1, 0, MITHRIL)),
            Map.entry("Luck of the Cave", new Perk(45, 3.07, 0, MITHRIL)),
            Map.entry("Efficient Miner", new Perk(100, 2.6, 0, MITHRIL)),
            Map.entry("Quick Forge", new Perk(20, 3.2, 0, MITHRIL)),
            Map.entry("Old-School", new Perk(20, 4.0, 0, GEMSTONE)),
            Map.entry("Professional", new Perk(140, 2.3, 0, GEMSTONE)),
            Map.entry("Mole", new Perk(200, 2.17883, 0, GEMSTONE)),
            Map.entry("Gem Lover", new Perk(20, 4.0, 0, GEMSTONE)),
            Map.entry("Seasoned Mineman", new Perk(100, 2.3, 0, GEMSTONE)),
            Map.entry("Fortunate Mineman", new Perk(50, 3.2, 0, GEMSTONE)),
            Map.entry("Blockhead", new Perk(20, 4.0, 0, GEMSTONE)),
            Map.entry("Keep It Cool", new Perk(50, 3.07, 0, GEMSTONE)),
            Map.entry("Lonesome Miner", new Perk(45, 3.07, 0, GEMSTONE)),
            Map.entry("Great Explorer", new Perk(20, 4.0, 0, GEMSTONE)),
            Map.entry("Powder Buff", new Perk(50, 3.2, 0, GEMSTONE)),
            Map.entry("Speedy Mineman", new Perk(50, 3.2, 0, GEMSTONE)),
            Map.entry("Subterranean Fisher", new Perk(40, 3.07, 0, GEMSTONE)),
            Map.entry("No Stone Unturned", new Perk(50, 3.05, 0, GLACITE)),
            Map.entry("Strong Arm", new Perk(100, 2.3, 0, GLACITE)),
            Map.entry("Steady Hand", new Perk(100, 2.6, 0, GLACITE)),
            Map.entry("Warm Heart", new Perk(50, 3.1, 0, GLACITE)),
            Map.entry("Surveyor", new Perk(20, 4.0, 0, GLACITE)),
            Map.entry("Metal Head", new Perk(20, 4.0, 0, GLACITE)),
            Map.entry("Rags to Riches", new Perk(50, 3.05, 0, GLACITE)),
            Map.entry("Eager Adventurer", new Perk(100, 2.3, 0, GLACITE)),
            Map.entry("Crystalline", new Perk(50, 3.3, 0, GLACITE)),
            Map.entry("Gifts from the Departed", new Perk(100, 2.45, 0, GLACITE)),
            Map.entry("Mining Master", new Perk(10, 5.0, 6, GLACITE)),
            Map.entry("Dead Man's Chest", new Perk(50, 3.2, 0, GLACITE)),
            Map.entry("Vanguard Seeker", new Perk(50, 3.1, 0, GLACITE)));

    // what one slot shows, built once per (immutable) lore component
    private record Info(int state, String tip) {}
    private static final Info EMPTY = new Info(NONE, null);

    private static final String[] SAMPLE = {"§bSky Mall§8: §6+100 Mining Speed"};

    private static Feature feature, skyMall;
    private static Screen cachedScreen;
    private static boolean cachedIn;
    private static final Map<ItemLore, Info> cache = new java.util.IdentityHashMap<>();

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HotmPerks").section("Mining")
                .when(() -> cfg().hotmHighlight || cfg().hotmLevelStack || cfg().hotmTokenStack || cfg().hotmPowderTooltip)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("HotM Perk Highlight", () -> c.hotmHighlight, v -> c.hotmHighlight = v)
                            .desc("Tints enabled and disabled perks in the Heart of the Mountain menu.");
                    b.color("  Enabled Color", () -> c.colorHotmEnabled, v -> c.colorHotmEnabled = v);
                    b.color("  Disabled Color", () -> c.colorHotmDisabled, v -> c.colorHotmDisabled = v);
                    b.toggle("  Level As Stack Size", () -> c.hotmLevelStack, v -> c.hotmLevelStack = v)
                            .desc("Perk level on the perk item, maxed perks stay blank.");
                    b.toggle("  Tokens On Heart Item", () -> c.hotmTokenStack, v -> c.hotmTokenStack = v)
                            .desc("Unused Tokens of the Mountain on the heart item.");
                    b.toggle("  Powder Costs In Tooltip", () -> c.hotmPowderTooltip, v -> c.hotmPowderTooltip = v)
                            .desc("Powder spent/total, cost of the next 10 levels (shift click) and cost to max.")
                            .tags("powder", "mithril", "gemstone", "glacite", "hotm");
                    b.toggle("  Sky Mall Display", () -> c.hotmSkyMallDisplay, v -> c.hotmSkyMallDisplay = v)
                            .desc("HUD line with today's Sky Mall buff, read from chat and the HotM menu.")
                            .tags("sky mall", "skymall", "hotm");
                    b.toggle("    Only On Mining Islands", () -> c.hotmSkyMallMiningOnly, v -> c.hotmSkyMallMiningOnly = v);
                });
        skyMall = Feature.of("HotmSkyMall").section("Mining").when(() -> cfg().hotmSkyMallDisplay)
                .hud("hotmskymalldisplay", HotmPerks::renderSkyMall)
                .serverChat(HotmPerks::onChat);
        HudEditElement.of("hotmSkyMallDisplay", "Sky Mall").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().hotmSkyMallDisplay)
                .size(() -> previewSize()[0], () -> previewSize()[1])
                .preview(HotmPerks::draw);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().hotmPowderTooltip || feature == null || !inMenu() || !feature.active()) return;
            addCostLines(stack, lines);
        });
    }

    private static boolean inMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != cachedScreen) {
            cachedScreen = mc.screen;
            cache.clear();
            cachedIn = mc.screen instanceof AbstractContainerScreen<?> cs
                    && StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Heart of the Mountain");
        }
        return cachedIn;
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = cfg();
        return feature != null && (c.hotmLevelStack || c.hotmTokenStack || c.hotmSkyMallDisplay)
                && inMenu() && c.section("Mining");
    }

    private static Info info(ItemStack item) {
        ItemLore raw = item.get(DataComponents.LORE);
        if (raw == null) return EMPTY;
        Info info = cache.get(raw);
        if (info == null) {
            info = build(item);
            if (cache.size() > 512) cache.clear();
            cache.put(raw, info);
        }
        return info;
    }

    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (feature == null || !cfg().hotmHighlight || !inMenu() || !feature.active()) return;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return;
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return;
        int state = info(item).state();
        if (state == NONE) return;
        TeslaMapsConfig c = cfg();
        String hex = state == ENABLED ? c.colorHotmEnabled : c.colorHotmDisabled;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF000000 | (TeslaMapsConfig.parseColor(hex) & 0xFFFFFF));
    }

    /** Level / token stack text, drawn over the container. */
    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            String tip = info(item).tip();
            if (tip == null) continue;
            int w = mc.font.width(tip);
            ctx.text(mc.font, tip, acc.getX() + slot.x + 17 - w, acc.getY() + slot.y + 9, 0xFFFFFFFF);
        }
    }

    private static Info build(ItemStack item) {
        TeslaMapsConfig c = cfg();
        String name = StringUtil.stripTrim(item.getHoverName().getString());
        List<String> lore = ItemUtil.loreLines(item);
        if (lore.isEmpty()) return EMPTY;
        if (name.startsWith("Heart of the Mountain")) {
            if (!c.hotmTokenStack) return EMPTY;
            for (String line : lore) {
                Matcher m = TOKENS.matcher(line.trim());
                if (m.matches() && !m.group(1).equals("0")) return new Info(NONE, "§b" + m.group(1));
            }
            return EMPTY;
        }
        if (name.startsWith("Reset Heart of the Mountain")) return EMPTY;
        if (name.equals("Sky Mall")) readSkyMall(lore);
        if (NOT_UNLOCKED.matcher(lore.get(lore.size() - 1).trim()).matches()) return EMPTY;

        boolean enabled = false, disabled = false;
        for (String line : lore) {
            String l = line.trim();
            if (l.equals("ENABLED") || l.equals("SELECTED")) enabled = true;
            else if (l.equals("DISABLED")) disabled = true;
        }
        int state = enabled ? ENABLED : disabled ? DISABLED : NONE;

        String tip = null;
        if (c.hotmLevelStack && state != NONE) {
            Matcher m = LEVEL.matcher(lore.get(0).trim());
            if (m.matches()) {
                int level = Integer.parseInt(m.group(1));
                Perk perk = PERKS.get(name);
                int max = m.group(2) != null ? Integer.parseInt(m.group(2)) : perk != null ? perk.max() : -1;
                if (level != 0 && level != max) tip = "§e" + level;
            }
        }
        return state == NONE && tip == null ? EMPTY : new Info(state, tip);
    }

    // the Sky Mall item lists today's buff under "Your Current Effect"
    private static void readSkyMall(List<String> lore) {
        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).trim().equals("Your Current Effect")) { index = i; break; }
        }
        if (index < 0) return;
        StringBuilder wrapped = new StringBuilder();
        for (int i = index + 1; i < lore.size(); i++) {
            String line = lore.get(i).trim();
            if (line.isEmpty()) break;
            if (!wrapped.isEmpty()) wrapped.append(' ');
            wrapped.append(line);
        }
        String display = skyMallDisplay(wrapped.toString());
        if (display != null) cfg().hotmSkyMallPerk = display;
    }

    private static void onChat(String message) {
        String t = StringUtil.strip(message).trim();
        if (!t.startsWith("New buff: ")) return;
        String display = skyMallDisplay(t);
        if (display != null) cfg().hotmSkyMallPerk = display;
    }

    // Ported from SkyHanni's HotmApi.SkymallPerk
    private static String skyMallDisplay(String text) {
        Matcher m = BUFF.matcher(text);
        if (!m.matches()) return null;
        String e = m.group(1);
        if (e.contains("Mining Speed") && e.startsWith("Gain")) return "§6+100 Mining Speed";
        if (e.contains("Mining Fortune") && e.startsWith("Gain") && !e.contains("Mangrove") && !e.contains("Fig")
                && !e.contains("Helix")) return "§6+50 Mining Fortune";
        if (e.contains("more Powder while mining")) return "§a+15% §7more Powder";
        if (e.contains("Pickaxe Ability cooldown")) return "§a-20% §7Pickaxe Ability cooldowns";
        if (e.contains("Golden and Diamond Goblins") || e.startsWith("10x chance to find Golden"))
            return "§a10x §6Gold §7& §bDiamond §7Goblin chance";
        if (e.contains("5x Titanium drops")) return "§a5x §9Titanium §7drops";
        return null;
    }

    private static void addCostLines(ItemStack stack, List<Component> lines) {
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        Perk perk = PERKS.get(name);
        if (perk == null) return;
        List<String> lore = ItemUtil.loreLines(stack);
        if (lore.isEmpty()) return;
        boolean locked = NOT_UNLOCKED.matcher(lore.get(lore.size() - 1).trim()).matches();
        int level = 0;
        Matcher m = LEVEL.matcher(lore.get(0).trim());
        if (m.matches()) level = Integer.parseInt(m.group(1));
        if (locked) level = 0;
        level = Math.min(level, perk.max());

        String powder = POWDER_NAMES[perk.powder()];
        long total = cost(perk, 1, perk.max());
        long spent = level <= 1 ? 0 : cost(perk, 1, level);
        lines.add(Component.empty());
        lines.add(Component.literal("§7Powder §f" + String.format("%,d", spent) + "§8/" + String.format("%,d", total)
                + " §8(§e" + (total == 0 ? 100 : Math.round(spent * 1000.0 / total) / 10.0) + "%§8)"));
        if (locked || level < 1 || level >= perk.max()) return;
        int shift = Math.min(10, perk.max() - level);
        lines.add(Component.literal("§7Cost (§e+" + shift + "§7): §f" + String.format("%,d", cost(perk, level, level + shift))
                + " " + powder));
        if (perk.max() - level > 10) {
            lines.add(Component.literal("§7Cost to max (§e+" + (perk.max() - level) + "§7): §f"
                    + String.format("%,d", cost(perk, level, perk.max())) + " " + powder));
        }
    }

    /** Powder from level {@code from} to level {@code to}. */
    private static long cost(Perk perk, int from, int to) {
        long sum = 0;
        for (int n = from; n < to; n++) sum += (long) Math.floor(Math.pow(n + 1 + perk.offset(), perk.exp()));
        return sum;
    }

    private static void renderSkyMall(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = cfg();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (c.hotmSkyMallMiningOnly && !Mining.onMiningIsland()) return;
        String line = line();
        if (line == null) return;
        draw(ctx, Minecraft.getInstance(), c.hotmSkyMallDisplayX, c.hotmSkyMallDisplayY, c.hotmSkyMallDisplayScale, line);
    }

    private static String line() {
        String perk = cfg().hotmSkyMallPerk;
        return perk == null || perk.isEmpty() ? null : "§bSky Mall§8: " + perk;
    }

    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        String line = line();
        draw(ctx, mc, x, y, scale, line == null ? SAMPLE[0] : line);
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String line) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(mc.font, line, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    private static int[] previewSize() {
        Minecraft mc = Minecraft.getInstance();
        String line = line();
        return new int[] {mc.font.width(line == null ? SAMPLE[0] : line), mc.font.lineHeight};
    }
}
