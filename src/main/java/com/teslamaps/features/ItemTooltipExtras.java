package com.teslamaps.features;

// Ported from Devonian's DungeonItemStats.kt/ArmorHexColor.kt, SkyOcean's DungeonQualityLoreModifier.kt,
// Skyblocker's ColorTooltip.java
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.RepoCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Tooltip lines: dungeon item quality/tier, armor dye hex + exotic check. */
public final class ItemTooltipExtras {

    public static final String[] QUALITY_STYLES = {"42/50", "42%", "84%"};
    private static final long WITHER_GLITCHED_AFTER = 1605830400000L;

    private static final Set<String> SEYMOUR = Set.of("VELVET_TOP_HAT", "CASHMERE_JACKET", "SATIN_TROUSERS", "OXFORD_SHOES");
    private static final Set<String> CRYSTAL = Set.of("1F0030", "46085E", "54146E", "5D1C78", "63237D", "6A2C82", "7E4196",
            "8E51A6", "9C64B3", "A875BD", "B88BC9", "C6A3D4", "D9C1E3", "E5D1ED", "EFE1F5", "FCF3FF");
    private static final Set<String> FAIRY = Set.of("330066", "4C0099", "660033", "660066", "6600CC", "7F00FF", "99004C",
            "990099", "9933FF", "B266FF", "CC0066", "CC00CC", "CC99FF", "E5CCFF", "FF007F", "FF00FF", "FF3399", "FF33FF",
            "FF66B2", "FF66FF", "FF99CC", "FF99FF", "FFCCE5", "FFCCFF");
    private static final Set<String> OG_FAIRY = Set.of("FF99FF", "FFCCFF", "E5CCFF", "CC99FF", "CC00CC", "FF00FF", "FF33FF",
            "FF66FF", "B266FF", "9933FF", "7F00FF", "660066", "6600CC", "4C0099", "330066", "990099", "660033", "99004C",
            "CC0066", "FFCCE5", "FF99CC", "FF66B2");
    private static final Set<String> SPOOK = Set.of("000000", "070008", "0E000F", "150017", "1B001F", "220027", "29002E",
            "300036", "37003E", "3E0046", "45004D", "4C0055", "52005D", "590065", "60006C", "670074", "6E007C", "750084",
            "7C008B", "830093", "89009B", "9000A3", "9700AA", "993399", "9E00B2");
    private static final Map<String, String> MISC_GLITCHED = Map.of("FFDC51", "SHARK_SCALE", "F7DA33", "FROZEN_BLAZE",
            "606060", "BAT_PERSON");
    private static final Map<String, String> WITHER_CHEST = Map.of("E7413C", "POWER_WITHER_CHESTPLATE",
            "45413C", "TANK_WITHER_CHESTPLATE", "4A14B7", "SPEED_WITHER_CHESTPLATE", "1793C4", "WISE_WITHER_CHESTPLATE",
            "000000", "WITHER_CHESTPLATE");
    private static final Map<String, String> WITHER_LEGS = Map.of("E75C3C", "POWER_WITHER_LEGGINGS",
            "65605A", "TANK_WITHER_LEGGINGS", "5D2FB9", "SPEED_WITHER_LEGGINGS", "17A8C4", "WISE_WITHER_LEGGINGS",
            "000000", "WITHER_LEGGINGS");
    private static final Map<String, String> WITHER_BOOTS = Map.of("E76E3C", "POWER_WITHER_BOOTS",
            "88837E", "TANK_WITHER_BOOTS", "8969C8", "SPEED_WITHER_BOOTS", "1CD4E4", "WISE_WITHER_BOOTS",
            "000000", "WITHER_BOOTS");
    private static final Set<String> GLITCHED = Set.of("FFDC51", "F7DA33", "606060", "E7413C", "45413C", "4A14B7", "1793C4",
            "000000", "E75C3C", "65605A", "5D2FB9", "17A8C4", "E76E3C", "88837E", "8969C8", "1CD4E4");
    private static final Set<String> RANCHERS = Set.of("CC5500", "000000", "0");
    private static final Set<String> REAPER = Set.of("1B1B1B", "FF0000");
    private static final Set<String> ADAPTIVE_CHEST = Set.of("3ABE78", "82E3D8", "BFBCB2", "D579FF", "FF4242", "FFC234");
    private static final Set<String> ADAPTIVE = Set.of("169F57", "2AB5A5", "6E00A0", "BB0000", "BFBCB2", "FFF7E6");


    private static ItemStack lastStack;
    private static int lastKey;
    private static List<Component> lastTop = List.of();

    private ItemTooltipExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature feature = Feature.of("ItemTooltipExtras")
                .when(() -> cfg().dungeonQualityTooltip || cfg().armorHexTooltip
                        || cfg().tooltipAnvilUses || cfg().tooltipSwordKills)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Item Details");
                    b.toggle("Dungeon Item Quality", () -> c.dungeonQualityTooltip, v -> c.dungeonQualityTooltip = v)
                            .desc("Adds \"Item Quality: 42/50 (Floor 7)\" under the name of dungeon drops.");
                    b.dropdown("  Quality Format", QUALITY_STYLES, () -> c.dungeonQualityStyle, v -> c.dungeonQualityStyle = v);
                    b.toggle("  Show Tier Line", () -> c.dungeonTierTooltip, v -> c.dungeonTierTooltip = v)
                            .desc("Extra \"Tier: n/10\" line, red at max.");
                    b.toggle("Armor Dye Hex Code", () -> c.armorHexTooltip, v -> c.armorHexTooltip = v)
                            .desc("Dyed armor shows \"Color: #RRGGBB\" in its own colour.");
                    b.toggle("  Exotic Detection", () -> c.armorExoticTooltip, v -> c.armorExoticTooltip = v)
                            .desc("Compares the colour with the item's original one: Exotic, Crystal, Fairy, OG Fairy, Spook, Glitched.");
                    b.toggle("Anvil Uses", () -> c.tooltipAnvilUses, v -> c.tooltipAnvilUses = v)
                            .desc("\"Anvil Uses: n\" at the bottom, hot potato books not counted.");
                    b.toggle("Sword Kills", () -> c.tooltipSwordKills, v -> c.tooltipSwordKills = v)
                            .desc("Kill counter stored on the item (sword kills / Book of Stats).");
                    b.toggle("Total Stars In Name", () -> c.tooltipItemStars, v -> c.tooltipItemStars = v)
                            .desc("Item name ends in the total star count (\"15✪\"), Kuudra armor tiers included.");
                    b.toggle("Booster Cookie Bits", () -> c.tooltipCookieBits, v -> c.tooltipCookieBits = v)
                            .desc("Bits gained, available bits before/after and buff days. Open the SkyBlock menu once to read your bits.");
                    b.toggle("Stonks Auction Coins Per Stonk", () -> c.tooltipStonksPrice, v -> c.tooltipStonksPrice = v)
                            .desc("Coins per Stock of Stonks for every rank, the cheapest in green.");
                    b.toggle("Fann Training Cost", () -> c.tooltipFannCost, v -> c.tooltipFannCost = v)
                            .desc("Coins per XP and XP per bit in Fann's training menu.");
                });
        RepoCache.onReload("ItemTooltipExtras", () -> lastStack = null);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!stack.isEmpty() && !lines.isEmpty() && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig c = cfg();
        int key = (c.dungeonQualityTooltip ? 1 : 0) | (c.dungeonTierTooltip ? 2 : 0) | (c.armorHexTooltip ? 4 : 0)
                | (c.armorExoticTooltip ? 8 : 0) | (c.dungeonQualityStyle.hashCode() << 4) | (SkyblockItems.loaded() ? 1 << 20 : 0)
                ^ ((c.tooltipAnvilUses ? 1 : 0) | (c.tooltipSwordKills ? 2 : 0)) << 27;
        if (stack != lastStack || key != lastKey) {
            lastStack = stack;
            lastKey = key;
            lastTop = build(stack, c);
            lastBottom = buildBottom(stack, c);
        }
        if (!lastTop.isEmpty()) lines.addAll(1, lastTop);
        if (!lastBottom.isEmpty()) lines.addAll(lastBottom);
    }

    private static List<Component> lastBottom = List.of();

    private static List<Component> buildBottom(ItemStack stack, TeslaMapsConfig c) {
        if (!c.tooltipAnvilUses && !c.tooltipSwordKills) return List.of();
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return List.of();
        CompoundTag tag = d.copyTag();
        List<Component> out = new ArrayList<>();
        if (c.tooltipAnvilUses) {
            int uses = tag.getInt("anvil_uses").orElse(0) - tag.getInt("hot_potato_count").orElse(0);
            if (uses > 0) out.add(Component.literal("Anvil Uses: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(uses)).withStyle(ChatFormatting.RED)));
        }
        if (c.tooltipSwordKills) {
            int kills = tag.getInt("sword_kills").orElse(0);
            String label = "Sword Kills: ";
            if (kills <= 0) {
                kills = tag.getInt("stats_book").orElse(0);
                label = "Kills: ";
            }
            if (kills > 0) out.add(Component.literal(label).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(java.util.Locale.ROOT, "%,d", kills)).withStyle(ChatFormatting.RED)));
        }
        return out;
    }

    private static List<Component> build(ItemStack stack, TeslaMapsConfig c) {
        List<Component> out = new ArrayList<>();
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = d == null ? new CompoundTag() : d.copyTag();
        if (c.dungeonQualityTooltip) {
            Optional<Integer> boost = tag.getInt("baseStatBoostPercentage");
            Optional<Integer> floor = tag.getInt("item_tier");
            if (boost.isPresent()) {
                int q = boost.get();
                String val = switch (c.dungeonQualityStyle) {
                    case "42%" -> q + "%";
                    case "84%" -> (q * 2) + "%";
                    default -> q + "/50";
                };
                ChatFormatting qc = q >= 50 ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE;
                MutableComponent line = Component.literal("Item Quality: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(val).withStyle(qc));
                floor.ifPresent(f -> line.append(Component.literal(" (Floor ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(f)).withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
                out.add(line);
                if (c.dungeonTierTooltip && floor.isPresent()) {
                    int t = floor.get();
                    ChatFormatting tc = t >= 10 ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE;
                    out.add(Component.literal("Tier: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(t + "/10").withStyle(tc)));
                }
            }
        }
        if (c.armorHexTooltip) {
            DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
            if (dyed != null) {
                int rgb = dyed.rgb() & 0xFFFFFF;
                String hex = hex6(rgb);
                MutableComponent line = Component.literal("Color: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("#" + hex).withStyle(Style.EMPTY.withColor(rgb)));
                if (c.armorExoticTooltip) {
                    String id = tag.getString("id").orElse("");
                    String type = exoticType(id, hex, tag);
                    if (type != null) line.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(type).withStyle(typeColor(type)))
                            .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
                }
                out.add(line);
            }
        }
        return out;
    }

    private static String hex6(int rgb) {
        String s = Integer.toHexString(rgb).toUpperCase(java.util.Locale.ROOT);
        return "000000".substring(s.length()) + s;
    }

    private static String exoticType(String id, String hex, CompoundTag tag) {
        int orig = SkyblockItems.dyeColor(id);
        if (id.isEmpty() || orig < 0 || hex6(orig).equals(hex) || tag.contains("dye_item") || isException(id, hex)) return null;
        if (CRYSTAL.contains(hex)) return "Crystal";
        if (FAIRY.contains(hex)) return "Fairy";
        if (OG_FAIRY.contains(hex)) return "OG Fairy";
        if (SPOOK.contains(hex) && id.startsWith("FAIRY_")) return "Spook";
        if (GLITCHED.contains(hex) && isGlitched(id, hex, tag)) return "Glitched";
        return "Exotic";
    }

    private static ChatFormatting typeColor(String type) {
        return switch (type) {
            case "Crystal" -> ChatFormatting.AQUA;
            case "Fairy" -> ChatFormatting.LIGHT_PURPLE;
            case "OG Fairy" -> ChatFormatting.DARK_PURPLE;
            case "Spook" -> ChatFormatting.RED;
            case "Glitched" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GOLD;
        };
    }

    private static boolean isException(String id, String hex) {
        if (id.startsWith("LEATHER") || id.equals("GHOST_BOOTS") || SEYMOUR.contains(id)) return true;
        if (id.startsWith("RANCHER")) return RANCHERS.contains(hex);
        if (id.contains("ADAPTIVE_CHESTPLATE")) return ADAPTIVE_CHEST.contains(hex);
        if (id.contains("ADAPTIVE")) return ADAPTIVE.contains(hex);
        if (id.contains("REAPER")) return REAPER.contains(hex);
        if (id.contains("FAIRY")) return FAIRY.contains(hex);
        if (id.contains("CRYSTAL")) return CRYSTAL.contains(hex);
        if (id.contains("SPOOK")) return SPOOK.contains(hex);
        return false;
    }

    private static boolean isGlitched(String id, String hex, CompoundTag tag) {
        if (id.contains("WITHER")) {
            long obtained = tag.getLong("timestamp").orElse(0L);
            if (hex.equals("000000") && obtained < WITHER_GLITCHED_AFTER) return false;
            Map<String, String> m = id.contains("CHESTPLATE") ? WITHER_CHEST : id.contains("LEGGINGS") ? WITHER_LEGS
                    : id.contains("BOOTS") ? WITHER_BOOTS : null;
            return m != null && m.containsKey(hex) && m.containsValue(id) && !m.get(hex).equals(id);
        }
        String misc = MISC_GLITCHED.get(hex);
        return misc != null && id.startsWith(misc);
    }
}
