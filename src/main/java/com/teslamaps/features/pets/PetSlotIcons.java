package com.teslamaps.features.pets;

// Ported from SkyHanni's PetCandyUsedDisplay.kt + PetItemDisplay.kt, max-level candy line after SkyOcean's PetCandy.kt
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Candy count and held-item symbol on pet icons, and the hidden candy line on max-level pets. */
public final class PetSlotIcons {

    record Info(int candy, String candyText, boolean maxed, String held, String uuid) {}

    private static final Info NONE = new Info(0, "", false, null, null);
    private static final Map<CustomData, Info> CACHE = new IdentityHashMap<>();

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("PET_ITEM_TIER_BOOST", "§6⬆"),
            Map.entry("PET_ITEM_EXP_SHARE", "§b✦"),
            Map.entry("MINOS_RELIC", "§5◆"),
            Map.entry("DWARF_TURTLE_SHELMET", "§a❖"),
            Map.entry("PET_ITEM_QUICK_CLAW", "§e⚡"),
            Map.entry("PET_ITEM_TEXTBOOK", "§9✎"),
            Map.entry("PET_ITEM_LUCKY_CLOVER", "§a☘"),
            Map.entry("CROCHET_TIGER_PLUSHIE", "§6✿"),
            Map.entry("ANTIQUE_REMEDIES", "§d✚"));

    private static Feature feature;
    private static int ticks;

    private PetSlotIcons() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PetSlotIcons").section("Skills")
                .when(() -> cfg().petCandyOnIcon || cfg().petCandyLore || cfg().petItemSymbol)
                .tick(PetSlotIcons::tick)
                .config("Skills", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Pet Items & Menus");
                    b.toggle("Pet Candy On Icon", () -> c.petCandyOnIcon, v -> c.petCandyOnIcon = v)
                            .desc("Red number top right on a pet's icon: how many Pet Candies were used on it")
                            .tags("candy", "pet");
                    b.toggle("  Not On Max Level Pets", () -> c.petCandyHideMaxed, v -> c.petCandyHideMaxed = v);
                    b.toggle("Candy Line On Max Level Pets", () -> c.petCandyLore, v -> c.petCandyLore = v)
                            .desc("Hypixel hides \"(n/10) Pet Candy Used\" once a pet is max level, this puts it back")
                            .tags("candy", "pet", "lore");
                    b.toggle("Pet Item Symbol On Icon", () -> c.petItemSymbol, v -> c.petItemSymbol = v)
                            .desc("A symbol for the held pet item on the pet's icon, in every inventory. "
                                    + "/tmap petsymbol <ITEM_ID> <symbol|off|reset> sets your own, § colours work")
                            .tags("pet item", "held item", "symbol", "tier boost");
                    b.slider("  Symbol Scale", 0.3f, 1.5f, () -> c.petItemSymbolScale, v -> c.petItemSymbolScale = v, 2);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().petCandyLore || stack.isEmpty() || lines.size() < 2 || feature == null || !feature.active()) return;
            candyLine(stack, lines);
        });
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("petsymbol")
                .then(ClientCommands.argument("item", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            String rem = sb.getRemaining().toUpperCase(Locale.ROOT);
                            for (String id : PetConstants.itemIds()) if (id.startsWith(rem)) sb.suggest(id);
                            return sb.buildFuture();
                        })
                        .then(ClientCommands.argument("symbol", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String id = StringArgumentType.getString(c, "item").toUpperCase(Locale.ROOT);
                                    String sym = StringArgumentType.getString(c, "symbol").replace('&', '§');
                                    TeslaMapsConfig cfg = cfg();
                                    if (sym.equalsIgnoreCase("reset")) cfg.petItemSymbols.remove(id);
                                    else cfg.petItemSymbols.put(id, sym.equalsIgnoreCase("off") ? "" : sym);
                                    TeslaMapsConfig.save();
                                    String now = symbol(id);
                                    c.getSource().sendFeedback(Component.literal("§a[TeslaMaps] §f" + id + " §7-> "
                                            + (now == null ? "§8none" : "§f" + now)));
                                    return 1;
                                })));
    }

    private static void tick() {
        if (++ticks % 40 != 0) return;
        if (Minecraft.getInstance().screen == null && !CACHE.isEmpty()) CACHE.clear();
    }

    static Info info(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return NONE;
        Info i = CACHE.get(d);
        if (i != null) return i;
        if (CACHE.size() > 512) CACHE.clear();
        i = parse(d);
        CACHE.put(d, i);
        return i;
    }

    private static Info parse(CustomData d) {
        try {
            var tag = d.copyTag();
            if (!"PET".equals(tag.getStringOr("id", ""))) return NONE;
            String raw = tag.getStringOr("petInfo", "");
            if (raw.isEmpty()) return NONE;
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            int candy = o.has("candyUsed") ? o.get("candyUsed").getAsInt() : 0;
            String held = o.has("heldItem") && !o.get("heldItem").isJsonNull() ? o.get("heldItem").getAsString() : null;
            String uuid = o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : null;
            boolean maxed = false;
            if (o.has("type") && o.has("exp")) {
                String type = o.get("type").getAsString();
                String tier = o.has("tier") ? o.get("tier").getAsString() : "COMMON";
                maxed = PetConstants.xpToLevel(type, tier, o.get("exp").getAsDouble()) >= PetConstants.maxLevel(type);
            }
            return new Info(candy, String.valueOf(candy), maxed, held, uuid);
        } catch (Exception e) {
            return NONE;
        }
    }

    private static String symbol(String held) {
        String own = cfg().petItemSymbols.get(held);
        if (own != null) return own.isEmpty() ? null : own;
        return DEFAULTS.get(held);
    }

    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.petCandyOnIcon && !c.petItemSymbol) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() != 1 || feature == null || !feature.active()) return;
        Info i = info(stack);
        if (i == NONE) return;
        Font font = Minecraft.getInstance().font;
        if (c.petCandyOnIcon && i.candy() > 0 && !(c.petCandyHideMaxed && i.maxed())) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(slot.x + 13, slot.y + 1);
            ctx.pose().scale(0.9f, 0.9f);
            ctx.text(font, i.candyText(), 0, 0, 0xFFFF5555);
            ctx.pose().popMatrix();
        }
        if (c.petItemSymbol && i.held() != null) {
            String sym = symbol(i.held());
            if (sym == null) return;
            float s = c.petItemSymbolScale;
            int w = (int) (font.width(sym) * s);
            ctx.pose().pushMatrix();
            ctx.pose().translate(slot.x + 22 - w, slot.y - 1);
            ctx.pose().scale(s, s);
            ctx.text(font, sym, 0, 0, 0xFFFFFFFF);
            ctx.pose().popMatrix();
        }
    }

    private static void candyLine(ItemStack stack, List<Component> lines) {
        if (!"PET".equals(ItemUtil.skyblockId(stack))) return;
        Info i = info(stack);
        if (i.candy() <= 0) return;
        int at = -1;
        for (int k = 0; k < lines.size(); k++) {
            String s = StringUtil.stripTrim(lines.get(k).getString());
            if (s.endsWith(") Pet Candy Used")) return;
            if (s.equals("MAX LEVEL")) at = k;
        }
        if (at < 0) return;
        lines.add(at, Component.literal("§a(" + i.candy() + "/10) Pet Candy Used"));
        lines.add(at + 1, Component.empty());
    }
}
