package com.teslamaps.features.mining;

// Ported from SkyHanni's features/mining/MiningCommissionsBlocksColor.kt (+ the block lists of OreBlock.kt)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CommissionBlockColor {

    private static final int NONE = 0, HOLLOWS = 1, TUNNELS = 2, MINESHAFT = 3;

    private enum Com {
        MITHRIL("Mithril Everywhere"), AMBER("Amber Gemstone Collector"), TOPAZ("Topaz Gemstone Collector"),
        AMETHYST("Amethyst Gemstone Collector"), RUBY("Ruby Gemstone Collector"), JADE("Jade Gemstone Collector"),
        SAPPHIRE("Sapphire Gemstone Collector"), GLACITE("Glacite Collector"), UMBER("Umber Collector"),
        TUNGSTEN("Tungsten Collector"), PERIDOT("Peridot Gemstone Collector"),
        AQUAMARINE("Aquamarine Gemstone Collector"), CITRINE("Citrine Gemstone Collector"), ONYX("Onyx Gemstone Collector");

        final String tab;
        volatile boolean highlight;
        long ignoredUntil;

        Com(String tab) { this.tab = tab; }
    }

    public static final String[] COLORS = {"Red", "Orange", "Yellow", "Lime", "Green", "Cyan", "Light Blue", "Blue",
            "Purple", "Magenta", "Pink", "White", "Light Gray", "Brown", "Black"};

    private static volatile boolean enabled, active = true;
    private static volatile int zone = NONE;
    private static volatile DyeColor color = DyeColor.RED;
    private static final Map<BlockState, BlockState> cache = new ConcurrentHashMap<>();
    private static volatile long applied;
    private static boolean oldSneak;
    private static int ticks, cooldown;

    private CommissionBlockColor() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CommissionBlockColor")
                .tickAlways(CommissionBlockColor::tick)
                .serverChat(CommissionBlockColor::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Commission Blocks");
                    b.toggle("Color Commission Blocks", () -> c.commissionBlocks, v -> c.commissionBlocks = v)
                            .desc("Crystal Hollows / Glacite: ore blocks of your active commissions turn one color, other commission ores turn gray.")
                            .tags("commission", "gemstone", "glacite", "umber", "tungsten");
                    b.toggle("  Sneak To Toggle", () -> c.commissionBlocksSneak, v -> c.commissionBlocksSneak = v);
                    b.dropdown("  Color", COLORS, () -> c.commissionBlocksColor, v -> c.commissionBlocksColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean want = c.commissionBlocks && mc.player != null && c.section("Mining");
        if (!want && !enabled && applied == 0) return;
        if (++ticks >= 20 || want != enabled) {
            ticks = 0;
            int z = want ? zoneNow() : NONE;
            boolean on = want && z != NONE;
            if (on && !enabled) active = true;
            enabled = on;
            zone = z;
            if (on) {
                color = dye(c.commissionBlocksColor);
                readTab();
            }
        }
        if (enabled && c.commissionBlocksSneak && mc.player != null) {
            boolean sneaking = mc.player.isShiftKeyDown();
            if (sneaking != oldSneak) {
                oldSneak = sneaking;
                if (sneaking) active = !active;
            }
        }
        if (cooldown > 0) cooldown--;
        long sig = signature();
        if (sig != applied && cooldown == 0) {
            applied = sig;
            cooldown = 20;
            cache.clear();
            if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
        }
    }

    private static long signature() {
        if (!enabled || !active) return 0;
        long sig = 1L | (long) zone << 1 | (long) color.ordinal() << 3;
        for (Com com : Com.values()) if (com.highlight) sig |= 1L << (8 + com.ordinal());
        return sig;
    }

    private static int zoneNow() {
        if (Mining.inHollows()) return HOLLOWS;
        String area = SkyblockUtils.getArea().toLowerCase();
        if (area.contains("mineshaft")) return MINESHAFT;
        if (!area.equals("dwarven mines")) return NONE;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(line);
            if (s.startsWith("⏣") || s.startsWith("ф")) {
                String sub = s.substring(1).trim();
                return sub.equals("Glacite Tunnels") || sub.equals("Great Glacite Lake") ? TUNNELS : NONE;
            }
        }
        return NONE;
    }

    private static void readTab() {
        long now = System.currentTimeMillis();
        java.util.List<String> lines = TabListUtils.getTabListLines();
        for (Com com : Com.values()) {
            boolean v = false;
            String prefix = com.tab + ":";
            for (String line : lines) {
                String s = StringUtil.stripTrim(line);
                if (s.startsWith(prefix) && !s.contains("DONE")) { v = true; break; }
            }
            if (v == com.highlight) continue;
            if (v && now < com.ignoredUntil) continue;
            com.highlight = v;
        }
    }

    private static void onChat(String text) {
        if (!enabled) return;
        String t = StringUtil.stripTrim(text);
        int i = t.indexOf(" Commission Complete!");
        if (i <= 0) return;
        String name = t.substring(0, i);
        for (Com com : Com.values()) {
            if (com.tab.equalsIgnoreCase(name)) {
                com.highlight = false;
                com.ignoredUntil = System.currentTimeMillis() + 5000;
            }
        }
    }

    /** Called from BlockStateModelSetMixin, on chunk-meshing threads too. */
    public static BlockState process(BlockState state) {
        if (applied == 0 || state == null) return state;
        return cache.computeIfAbsent(state, CommissionBlockColor::replace);
    }

    private static BlockState replace(BlockState state) {
        Com com = typeOf(state, zone);
        if (com == null) return state;
        DyeColor c = com.highlight ? color : DyeColor.GRAY;
        Block b = state.getBlock();
        if (b instanceof StainedGlassPaneBlock) return copy(state, block(c, "_stained_glass_pane").defaultBlockState());
        if (b instanceof StainedGlassBlock) return block(c, "_stained_glass").defaultBlockState();
        return block(c, "_wool").defaultBlockState();
    }

    private static Com typeOf(BlockState state, int zone) {
        Block b = state.getBlock();
        DyeColor glass = b instanceof StainedGlassBlock g ? g.getColor()
                : b instanceof StainedGlassPaneBlock p ? p.getColor() : null;
        if (glass != null) {
            switch (glass) {
                case RED: return Com.RUBY;
                case ORANGE: return Com.AMBER;
                case PURPLE: return Com.AMETHYST;
                case LIME: return Com.JADE;
                case LIGHT_BLUE: return Com.SAPPHIRE;
                case YELLOW: return Com.TOPAZ;
                default: break;
            }
            if (zone == HOLLOWS) return null;
            return switch (glass) {
                case BLUE -> Com.AQUAMARINE;
                case BROWN -> Com.CITRINE;
                case BLACK -> Com.ONYX;
                case GREEN -> Com.PERIDOT;
                default -> null;
            };
        }
        if (b == Blocks.PRISMARINE || b == Blocks.PRISMARINE_BRICKS || b == Blocks.DARK_PRISMARINE
                || b == Blocks.WOOL.lightBlue()) return Com.MITHRIL;
        if (zone == HOLLOWS) return null;
        if (b == Blocks.WOOL.gray() || b == Blocks.DYED_TERRACOTTA.cyan()) return Com.MITHRIL;
        if (b == Blocks.PACKED_ICE) return Com.GLACITE;
        if (b == Blocks.TERRACOTTA || b == Blocks.DYED_TERRACOTTA.brown() || b == Blocks.SMOOTH_RED_SANDSTONE) return Com.UMBER;
        if (b == Blocks.CLAY) return Com.TUNGSTEN;
        if (zone == TUNNELS && b == Blocks.INFESTED_COBBLESTONE) return Com.TUNGSTEN;
        if (zone == MINESHAFT && (b == Blocks.COBBLESTONE || b == Blocks.COBBLESTONE_SLAB || b == Blocks.COBBLESTONE_STAIRS)) {
            return Com.TUNGSTEN;
        }
        return null;
    }

    private static Block block(DyeColor c, String suffix) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(c.getName() + suffix));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState from, BlockState to) {
        for (Property p : from.getProperties()) {
            if (to.hasProperty(p)) to = to.setValue(p, (Comparable) from.getValue(p));
        }
        return to;
    }

    private static DyeColor dye(String name) {
        if (name == null) return DyeColor.RED;
        DyeColor d = DyeColor.byName(name.toLowerCase().replace(' ', '_'), null);
        return d == null ? DyeColor.RED : d;
    }
}
