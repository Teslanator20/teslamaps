package com.teslamaps.features.mining;

// Block breakdown, needed-to-pity and chat hover ported from SkyHanni's MineshaftPityDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Glacite Mineshaft pity counter, read off the "Pity:" tab widget.
 * Needs the widget enabled via /tab > Pity > Glacite Tunnels.
 */
public class MineshaftPity {

    public static final int MAX = 2000;

    private static final Pattern PITY = Pattern.compile("Glacite Mineshafts: ([\\d,]+)/[\\d,]+");
    private static final String FOUND = "You found a Glacite Mineshaft";

    enum PityBlock {
        MITHRIL("Mithril", 2, Items.LIGHT_BLUE_WOOL),
        GEMSTONE("Low Tier Gemstone", 8, Items.RED_STAINED_GLASS),
        HIGH_TIER_GEMSTONE("High Tier Gemstone", 10, Items.BLUE_STAINED_GLASS),
        GLACITE("Glacite", 4, Items.PACKED_ICE),
        TUNGSTEN("Tungsten", 4, Items.CLAY),
        UMBER("Umber", 4, Items.RED_SANDSTONE),
        TITANIUM("Titanium", 8, Items.POLISHED_DIORITE);

        final String name;
        final int multiplier;
        final net.minecraft.world.item.Item icon;
        int broken, spread;

        PityBlock(String name, int multiplier, net.minecraft.world.item.Item icon) {
            this.name = name;
            this.multiplier = multiplier;
            this.icon = icon;
        }

        double pity() {
            return (broken + spread / 2.0) * multiplier;
        }

        static PityBlock of(BlockState state) {
            Block b = state.getBlock();
            if (b == Blocks.GRAY_WOOL || b == Blocks.CYAN_TERRACOTTA || b == Blocks.PRISMARINE
                    || b == Blocks.PRISMARINE_BRICKS || b == Blocks.DARK_PRISMARINE || b == Blocks.LIGHT_BLUE_WOOL) return MITHRIL;
            if (b == Blocks.POLISHED_DIORITE) return TITANIUM;
            if (b == Blocks.PACKED_ICE) return GLACITE;
            if (b == Blocks.CLAY || b == Blocks.INFESTED_COBBLESTONE) return TUNGSTEN;
            if (b == Blocks.TERRACOTTA || b == Blocks.BROWN_TERRACOTTA || b == Blocks.SMOOTH_RED_SANDSTONE) return UMBER;
            DyeColor color = b instanceof StainedGlassBlock g ? g.getColor()
                    : b instanceof StainedGlassPaneBlock p ? p.getColor() : null;
            if (color == null) return null;
            return switch (color) {
                case RED, ORANGE, PURPLE, LIME, LIGHT_BLUE -> GEMSTONE;
                case BLUE, BROWN, BLACK, GREEN -> HIGH_TIER_GEMSTONE;
                default -> null;
            };
        }
    }

    private static final int[] MULTIPLIERS = {2, 4, 8, 10};
    private static final ItemStack[][] ICONS = new ItemStack[MULTIPLIERS.length][];

    private static final String[] SAMPLE = {
            "§9§lMineshaft Pity", "§3Pity: §e561§6/§e2000", "§3Chance: §e1§6/§e1439 §7(0.069%)"
    };

    private static final String[] MISSING = {
            "§9§lMineshaft Pity", "§cPity tab widget missing",
            "§c/tab §7> §cPity §7> §cGlacite Tunnels"
    };

    private static int progress = -1;   // -1 while the widget was never seen
    private static int sessionFound = 0;
    private static long lastFound = 0;
    private static int ticks = 0;
    private static List<String> cached = List.of(MISSING);
    private static int[] needed = new int[MULTIPLIERS.length];
    private static boolean showNeeded;
    private static boolean inDwarven;
    private static BlockPos lastOriginal;
    private static long lastOriginalTime;

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.mineshaftPityDisplay && !c.mineshaftFoundChat) return;
        if (!Mining.onMiningIsland()) return;
        if (++ticks < 10) return;
        ticks = 0;
        inDwarven = com.teslamaps.utils.SkyblockUtils.getArea().toLowerCase().contains("dwarven");

        for (String raw : TabListUtils.getTabListLines()) {
            Matcher m = PITY.matcher(StringUtil.stripTrim(raw));
            if (m.find()) {
                progress = Integer.parseInt(m.group(1).replace(",", ""));
                break;
            }
        }
        cached = buildLines();
    }

    public static boolean tracking() {
        if (!inDwarven) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return (c.mineshaftPityChatHover || c.mineshaftPityShowAverage) && c.section("Mining") && Mining.onMiningIsland();
    }

    /** Block update packet, main thread. Original = the crosshair block, spread = what breaks right after it. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState) return;
        Block nb = newState.getBlock();
        if (nb != Blocks.AIR && nb != Blocks.BEDROCK) return;
        PityBlock type = PityBlock.of(oldState);
        if (type == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inDwarven) return;
        if (pos.distToCenterSqr(mc.player.position()) > 49) return;
        long now = System.currentTimeMillis();
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos)) {
            type.broken++;
            lastOriginal = pos.immutable();
            lastOriginalTime = now;
        } else if (lastOriginal != null && now - lastOriginalTime < 300 && !pos.equals(lastOriginal)) {
            type.spread++;
        }
    }

    public static void onChatMessage(String text) {
        if (!StringUtil.strip(text).contains(FOUND)) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        int totalBlocks = 0;
        for (PityBlock b : PityBlock.values()) totalBlocks += b.broken + b.spread;
        sessionFound++;
        c.mineshaftTotalFound++;
        c.mineshaftTotalBlocks += totalBlocks;
        TeslaMapsConfig.save();

        if (c.mineshaftFoundChat) {
            StringBuilder sb = new StringBuilder("§b[TeslaMaps] §7Mineshaft at pity §e");
            sb.append(progress < 0 ? "?" : progress).append("§7/§e").append(MAX);
            if (progress >= 0) sb.append(" §7(§e1§6/§e").append(String.format("%.1f", chance())).append("§7)");
            if (lastFound > 0) sb.append(" §7after §b").append(time(System.currentTimeMillis() - lastFound));
            Component line = Component.literal(sb.toString());
            if (c.mineshaftPityChatHover) {
                Component hover = Component.literal(String.join("\n", hover(totalBlocks)));
                line = line.copy().withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)));
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(line);
        }

        for (PityBlock b : PityBlock.values()) b.broken = b.spread = 0;
        lastFound = System.currentTimeMillis();
        progress = 0;
        cached = buildLines();
    }

    private static List<String> hover(int totalBlocks) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int remaining = MAX - Math.max(0, progress);
        List<String> out = new ArrayList<>();
        out.add("§7Blocks mined: §e" + totalBlocks);
        out.add("§7Pity Counter: §e" + (progress < 0 ? "?" : progress));
        if (progress >= 0) {
            double chance = chance();
            out.add("§7Chance: §e1§6/§e" + String.format("%.1f", chance) + " §7(§b" + String.format("%.3f", 100.0 / chance) + "%§7)");
        }
        for (PityBlock b : PityBlock.values()) {
            if (b.broken == 0 && b.spread == 0) continue;
            out.add("    §7" + b.name + " mined: §e" + b.broken + " [+" + b.spread + " spread] §6("
                    + (int) b.pity() + "/" + remaining + ")");
        }
        if (c.mineshaftTotalFound > 0) {
            out.add("");
            out.add("§7Average Blocks/Mineshaft: §e" + String.format("%.1f", c.mineshaftTotalBlocks / (double) c.mineshaftTotalFound));
        }
        if (lastFound > 0) {
            out.add("");
            out.add("§7Time since Last Mineshaft: §b" + time(System.currentTimeMillis() - lastFound));
        }
        return out;
    }

    /** Denominator of the spawn chance, so 1439.0 means 1/1439 per block. */
    private static double chance() {
        int left = Math.max(1, MAX - Math.max(0, progress));
        return left / (1 + TeslaMapsConfig.get().mineshaftChanceBonus / 100.0);
    }

    private static String time(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    public static List<String> lines() {
        return cached;
    }

    private static List<String> buildLines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        showNeeded = false;
        if (progress < 0) return List.of(MISSING);

        List<String> out = new ArrayList<>();
        out.add("§9§lMineshaft Pity");
        out.add("§3Pity: §e" + progress + "§6/§e" + MAX);
        double chance = chance();
        if (!c.mineshaftPityHideChance) {
            out.add("§3Chance: §e1§6/§e" + String.format("%.1f", chance)
                    + " §7(" + String.format("%.3f", 100.0 / chance) + "%)");
        }
        if (lastFound > 0 && !c.mineshaftPityHideLast) out.add("§3Last: §e" + time(System.currentTimeMillis() - lastFound));
        if (c.mineshaftPityShowAverage && c.mineshaftTotalFound > 0) {
            out.add("§3Average Blocks/Mineshaft: §e" + String.format("%.1f", c.mineshaftTotalBlocks / (double) c.mineshaftTotalFound));
        }
        if ((sessionFound > 0 || c.mineshaftTotalFound > 0) && !c.mineshaftPityHideFound) {
            out.add("§3Found: §e" + sessionFound + " §7session §8| §e" + c.mineshaftTotalFound + " §7total");
        }
        if (c.mineshaftPityShowNeeded) {
            int remaining = MAX - Math.max(0, progress);
            for (int i = 0; i < MULTIPLIERS.length; i++) needed[i] = remaining / MULTIPLIERS[i];
            showNeeded = true;
            out.add("§3Needed to pity:");
        }
        return out;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.mineshaftPityDisplay) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (!Mining.onMiningIsland()) return;

        draw(context, Minecraft.getInstance(), c.mineshaftPityX, c.mineshaftPityY, c.mineshaftPityScale, cached, showNeeded);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        if (progress < 0) draw(context, mc, x, y, scale, List.of(SAMPLE), false);
        else draw(context, mc, x, y, scale, cached, showNeeded);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines) {
        draw(context, mc, x, y, scale, lines, false);
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines, boolean needed) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int lh = mc.font.lineHeight + 1;
        int row = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, row * lh, 0xFFFFFFFF);
            row++;
        }
        if (needed) {
            for (int i = 0; i < MULTIPLIERS.length; i++) {
                ItemStack[] icons = icons(i);
                int ix = 10;
                int iy = row * lh;
                for (ItemStack icon : icons) {
                    pose.pushMatrix();
                    pose.translate(ix, iy);
                    pose.scale(0.5f, 0.5f);
                    context.item(icon, 0, 0);
                    pose.popMatrix();
                    ix += 9;
                }
                context.text(mc.font, "§b" + MineshaftPity.needed[i], ix + 2, iy, 0xFFFFFFFF);
                row++;
            }
        }
        pose.popMatrix();
    }

    private static ItemStack[] icons(int group) {
        if (ICONS[group] == null) {
            List<ItemStack> list = new ArrayList<>();
            for (PityBlock b : PityBlock.values()) if (b.multiplier == MULTIPLIERS[group]) list.add(new ItemStack(b.icon));
            ICONS[group] = list.toArray(new ItemStack[0]);
        }
        return ICONS[group];
    }

    public static int[] previewSize(Font font) {
        List<String> lines = progress < 0 ? List.of(SAMPLE) : cached;
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        int rows = lines.size() + (progress >= 0 && showNeeded ? MULTIPLIERS.length : 0);
        if (progress >= 0 && showNeeded) w = Math.max(w, 10 + 3 * 9 + 2 + font.width("1000"));
        return new int[] { w, rows * (font.lineHeight + 1) };
    }

    public static void resetStats() {
        sessionFound = 0;
        lastFound = 0;
        for (PityBlock b : PityBlock.values()) b.broken = b.spread = 0;
        TeslaMapsConfig.get().mineshaftTotalFound = 0;
        TeslaMapsConfig.get().mineshaftTotalBlocks = 0;
        TeslaMapsConfig.save();
        cached = buildLines();
    }
}
