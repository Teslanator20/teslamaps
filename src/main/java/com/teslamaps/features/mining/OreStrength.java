package com.teslamaps.features.mining;

// Block strengths and formulas after SkyHanni's OreBlock.kt (values from the NEU repo / wiki)
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/** SkyBlock mining blocks: strength, breaking power and ticks at a given mining speed. */
public enum OreStrength {
    STONE("Stone", Items.STONE, 15, 1, true),
    COBBLESTONE("Cobblestone", Items.COBBLESTONE, 20, 1, true),
    ORES("Vanilla Ores", Items.IRON_ORE, 30, 1, false),
    PURE_ORES("Pure Ores", Items.IRON_BLOCK, 600, 2, false),
    END_STONE("End Stone", Items.END_STONE, 30, 1, true),
    NETHERRACK("Netherrack", Items.NETHERRACK, 4, 1, true),
    HARD_STONE("Hard Stone", Items.STONE, 50, 4, true),
    OBSIDIAN("Obsidian", Items.OBSIDIAN, 500, 1, false),
    GRAY_MITHRIL("Gray Mithril", Items.GRAY_WOOL, 500, 4, false),
    GREEN_MITHRIL("Green Mithril", Items.PRISMARINE, 800, 4, false),
    BLUE_MITHRIL("Blue Mithril", Items.LIGHT_BLUE_WOOL, 1500, 4, false),
    TITANIUM("Titanium", Items.POLISHED_DIORITE, 2000, 5, false),
    RUBY("Ruby", Items.RED_STAINED_GLASS, 2300, 6, false),
    NUCLEUS_GEMS("Amber/Amethyst/Jade/Sapphire", Items.LIGHT_BLUE_STAINED_GLASS, 3000, 7, false),
    TOPAZ("Topaz", Items.YELLOW_STAINED_GLASS, 3800, 8, false),
    OPAL("Opal", Items.WHITE_STAINED_GLASS, 3800, 8, false),
    JASPER("Jasper", Items.MAGENTA_STAINED_GLASS, 4800, 9, false),
    TUNNEL_GEMS("Onyx/Aquamarine/Citrine/Peridot", Items.BLACK_STAINED_GLASS, 5200, 10, false),
    UMBER("Umber", Items.RED_SANDSTONE, 5600, 9, false),
    TUNGSTEN("Tungsten", Items.CLAY, 5600, 9, false),
    GLACITE("Glacite", Items.PACKED_ICE, 6000, 9, false);

    public final String label;
    public final Item icon;
    public final int strength, breakingPower;
    public final boolean plainBlock;

    OreStrength(String label, Item icon, int strength, int breakingPower, boolean plainBlock) {
        this.label = label;
        this.icon = icon;
        this.strength = strength;
        this.breakingPower = breakingPower;
        this.plainBlock = plainBlock;
    }

    public int softCap() { return (int) Math.ceil(20.0 / 3.0 * strength); }

    public int instant() { return strength * (plainBlock ? 30 : 60); }

    public int ticks(double speed) {
        if (speed <= 0) return -1;
        if (speed >= instant()) return 1;
        if (speed >= softCap()) return 4;
        return (int) Math.round(strength * 30.0 / speed);
    }

    /** Speed still missing for one tick less, -1 at instant. */
    public int missingForNext(double speed) {
        int t = ticks(speed);
        if (t <= 1) return -1;
        if (t == 4) return (int) Math.ceil(instant() - speed);
        return (int) Math.ceil(strength * 30.0 / (t - 0.5) - speed);
    }

    /** The block under the crosshair as a mining block, null if unknown. */
    public static OreStrength of(BlockState state, boolean hollows) {
        Block b = state.getBlock();
        if (b == Blocks.STONE) return hollows ? HARD_STONE : STONE;
        if (b == Blocks.COBBLESTONE) return COBBLESTONE;
        if (b == Blocks.END_STONE) return END_STONE;
        if (b == Blocks.NETHERRACK) return NETHERRACK;
        if (b == Blocks.OBSIDIAN) return OBSIDIAN;
        if (b == Blocks.COAL_ORE || b == Blocks.IRON_ORE || b == Blocks.GOLD_ORE || b == Blocks.LAPIS_ORE
                || b == Blocks.REDSTONE_ORE || b == Blocks.EMERALD_ORE || b == Blocks.DIAMOND_ORE
                || b == Blocks.NETHER_QUARTZ_ORE) return ORES;
        if (b == Blocks.COAL_BLOCK || b == Blocks.IRON_BLOCK || b == Blocks.GOLD_BLOCK || b == Blocks.LAPIS_BLOCK
                || b == Blocks.REDSTONE_BLOCK || b == Blocks.EMERALD_BLOCK || b == Blocks.DIAMOND_BLOCK
                || b == Blocks.QUARTZ_BLOCK) return PURE_ORES;
        if (b == Blocks.GRAY_WOOL || b == Blocks.CYAN_TERRACOTTA) return GRAY_MITHRIL;
        if (b == Blocks.PRISMARINE || b == Blocks.PRISMARINE_BRICKS || b == Blocks.DARK_PRISMARINE) return GREEN_MITHRIL;
        if (b == Blocks.LIGHT_BLUE_WOOL) return BLUE_MITHRIL;
        if (b == Blocks.POLISHED_DIORITE) return TITANIUM;
        if (b == Blocks.PACKED_ICE) return GLACITE;
        if (b == Blocks.CLAY || b == Blocks.INFESTED_COBBLESTONE) return TUNGSTEN;
        if (b == Blocks.TERRACOTTA || b == Blocks.BROWN_TERRACOTTA || b == Blocks.SMOOTH_RED_SANDSTONE) return UMBER;
        DyeColor color = b instanceof StainedGlassBlock g ? g.getColor()
                : b instanceof StainedGlassPaneBlock p ? p.getColor() : null;
        if (color == null) return null;
        return switch (color) {
            case RED -> RUBY;
            case ORANGE, PURPLE, LIME, LIGHT_BLUE -> NUCLEUS_GEMS;
            case YELLOW -> TOPAZ;
            case WHITE -> OPAL;
            case MAGENTA -> JASPER;
            case BLACK, BLUE, BROWN, GREEN -> TUNNEL_GEMS;
            default -> null;
        };
    }

    // ---- mining speed from the Stats tab widget ----

    private static final Pattern SPEED = Pattern.compile("Mining Speed: ⸕?([\\d,.]+)");
    private static int speed = -1;
    private static long speedAt;

    /** Last Mining Speed from the tab list, -1 if the Stats widget never showed it. */
    public static int miningSpeed() {
        long now = System.currentTimeMillis();
        if (now - speedAt < 1000) return speed;
        speedAt = now;
        for (String raw : TabListUtils.getTabListLines()) {
            Matcher m = SPEED.matcher(StringUtil.stripTrim(raw));
            if (m.find()) {
                try {
                    speed = (int) Double.parseDouble(m.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }
        return speed;
    }
}
