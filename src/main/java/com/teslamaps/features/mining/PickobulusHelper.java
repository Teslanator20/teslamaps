package com.teslamaps.features.mining;

// Ported from Skyblocker's PickobulusHelper.java / PickobulusHudWidget.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Outlines the blocks Pickobulus would break where you aim, HUD with the block count and expected drops. */
public final class PickobulusHelper {

    private enum Place { NONE, CONVERT, HOLLOWS, TUNNELS, MINESHAFT }

    private static final Set<Block> CONVERT_INTO_BEDROCK = Set.of(
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.POLISHED_DIORITE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE, Blocks.CYAN_TERRACOTTA, Blocks.LIGHT_BLUE_WOOL, Blocks.GRAY_WOOL,
            Blocks.LAPIS_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
            Blocks.REDSTONE_BLOCK, Blocks.COAL_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.GOLD_ORE, Blocks.IRON_ORE,
            Blocks.COAL_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE, Blocks.NETHERRACK, Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.END_STONE);

    private static final int PITY = 0, HARDSTONE = 1, ICE = 2, TUNGSTEN = 3, UMBER = 4, MITHRIL = 5,
            TITANIUM = 6, GEMSTONES = 7, POWDER = 8;
    private static final String[] DROP_NAMES = {"§5Mineshaft Pity", "§7Hardstone", "§bIce", "§8Tungsten", "§6Umber",
            "§3Mithril", "§fTitanium", "§dGemstones", "§2Mithril Powder"};
    private static final String TITLE = "§9§lPickobulus";
    private static final List<String> SAMPLE = List.of(TITLE, "§fTotal Blocks: §e87", "§2Mithril Powder: §e132", "§dGemstones: §e14");

    private static final BlockState[][][] blocks = new BlockState[8][8][8];
    private static final int[] drops = new int[DROP_NAMES.length];
    private static final List<AABB> boxes = new ArrayList<>();
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static boolean visible;
    private static Place place = Place.NONE;
    private static String cooldown;
    private static ItemLore lastLore;
    private static boolean lastHasAbility;
    private static int ticks;

    private PickobulusHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("pickobulusHud", "Pickobulus").ctx(HudEditElement.HudCtx.MINING)
                .section("Mining").enabled(() -> cfg().pickobulusHelper && cfg().pickobulusHud)
                .size(() -> Math.max(90, width), () -> Math.max(10, (visible ? lines.size() : SAMPLE.size()) * 10))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("PickobulusHelper").section("Mining").when(() -> cfg().pickobulusHelper)
                .tick(PickobulusHelper::tick)
                .world(PickobulusHelper::render)
                .hud("pickobulushud", PickobulusHelper::renderHud)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Pickobulus Helper", () -> c.pickobulusHelper, v -> c.pickobulusHelper = v)
                            .desc("While holding a Pickobulus tool: outlines the blocks it would break where you aim and shows the block count and expected drops. Move the HUD in /tmap gui.")
                            .tags("pickobulus", "ability", "powder", "pity", "hotm");
                    b.toggle("  Block Outlines", () -> c.pickobulusOutline, v -> c.pickobulusOutline = v);
                    b.color("  Outline Color", () -> c.colorPickobulus, v -> c.colorPickobulus = v);
                    b.toggle("  HUD", () -> c.pickobulusHud, v -> c.pickobulusHud = v);
                    b.toggle("  Hide HUD On Cooldown", () -> c.pickobulusHideOnCooldown, v -> c.pickobulusHideOnCooldown = v)
                            .desc("The cooldown is read from the Pickaxe Ability tab widget.");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ticks % 20 == 0) place = place();
        if (ticks % 10 == 0) cooldown = readCooldown();
        if (ticks++ % 2 != 0) return;

        boxes.clear();
        Arrays.fill(drops, 0);
        visible = false;
        if (place == Place.NONE) return;
        List<String> out = new ArrayList<>();
        out.add(TITLE);
        if (cooldown != null) {
            if (cfg().pickobulusHideOnCooldown || !hasAbility(mc.player.getMainHandItem())) return;
            out.add("§cOn cooldown: " + cooldown);
            finish(out);
            return;
        }
        if (!hasAbility(mc.player.getMainHandItem())) return;

        Vec3 start = mc.player.position().add(0, mc.player.getEyeHeight() + 0.53625, 0);
        BlockHitResult hit = mc.level.clip(new ClipContext(start, start.add(mc.player.getForward().scale(20)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            out.add("§cNot looking at a block");
            finish(out);
            return;
        }
        simulate(mc, hit.getBlockPos());
        out.add("§fTotal Blocks: §e" + boxes.size());
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] > 0) out.add(DROP_NAMES[i] + ": §e" + drops[i]);
        }
        finish(out);
    }

    private static void finish(List<String> out) {
        lines = out;
        visible = true;
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : out) w = Math.max(w, font.width(s));
        width = w;
    }

    private static Place place() {
        String area = SkyblockUtils.getArea();
        if (area.equalsIgnoreCase("Crystal Hollows")) return Place.HOLLOWS;
        if (area.contains("Mineshaft")) return Place.MINESHAFT;
        if (area.equalsIgnoreCase("Gold Mine") || area.equalsIgnoreCase("Deep Caverns")) return Place.CONVERT;
        if (area.equalsIgnoreCase("Dwarven Mines")) {
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                String l = ScoreboardUtils.cleanLine(line);
                if (l.contains("Glacite Tunnels") || l.contains("Great Glacite Lake")) return Place.TUNNELS;
            }
            return Place.CONVERT;
        }
        return Place.NONE;
    }

    private static String readCooldown() {
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            if (!line.startsWith("Pickobulus: ")) continue;
            String state = line.substring(12).trim();
            return state.equals("Available") ? null : state;
        }
        return null;
    }

    private static boolean hasAbility(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        if (lore == lastLore) return lastHasAbility;
        boolean has = false;
        for (Component line : lore.lines()) {
            if (StringUtil.stripTrim(line.getString()).startsWith("Ability: Pickobulus")) { has = true; break; }
        }
        lastLore = lore;
        return lastHasAbility = has;
    }

    private static void simulate(Minecraft mc, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int ox = center.getX() - 4, oy = center.getY() - 4, oz = center.getZ() - 4;
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                for (int k = 0; k < 8; k++)
                    blocks[i][j][k] = mc.level.getBlockState(m.set(ox + i, oy + j, oz + k));

        for (int i = 1; i < 7; i++) {
            for (int j = 1; j < 7; j++) {
                for (int k = 1; k < 7; k++) {
                    BlockState s = blocks[i][j][k];
                    if (s.isAir() || s.is(Blocks.BEDROCK)) continue;
                    boolean exposed = blocks[i - 1][j][k].isAir() || blocks[i + 1][j][k].isAir()
                            || blocks[i][j - 1][k].isAir() || blocks[i][j + 1][k].isAir()
                            || blocks[i][j][k - 1].isAir() || blocks[i][j][k + 1].isAir();
                    if (!exposed) continue;
                    int x = ox + i, y = oy + j, z = oz + k;
                    switch (place) {
                        case CONVERT -> {
                            if (CONVERT_INTO_BEDROCK.contains(s.getBlock()) || glass(s)) add(x, y, z);
                        }
                        case HOLLOWS -> {
                            hollows(s);
                            blocks[i][j][k] = Blocks.AIR.defaultBlockState();
                            add(x, y, z);
                        }
                        case MINESHAFT -> {
                            if (s.is(Blocks.STONE)) drops[HARDSTONE]++;
                            blocks[i][j][k] = Blocks.AIR.defaultBlockState();
                            add(x, y, z);
                        }
                        case TUNNELS -> {
                            int r = tunnels(s);
                            if (r == 0) continue;
                            if (r == 2) blocks[i][j][k] = Blocks.AIR.defaultBlockState();
                            add(x, y, z);
                        }
                        default -> { }
                    }
                }
            }
        }
    }

    private static void add(int x, int y, int z) {
        boxes.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
    }

    private static boolean glass(BlockState s) {
        Block b = s.getBlock();
        return b instanceof StainedGlassBlock || b instanceof StainedGlassPaneBlock;
    }

    private static boolean mithril(BlockState s) {
        return s.is(Blocks.PRISMARINE) || s.is(Blocks.PRISMARINE_BRICKS) || s.is(Blocks.DARK_PRISMARINE)
                || s.is(Blocks.LIGHT_BLUE_WOOL) || s.is(Blocks.GRAY_WOOL) || s.is(Blocks.CYAN_TERRACOTTA);
    }

    private static int powder(BlockState s) {
        if (s.is(Blocks.LIGHT_BLUE_WOOL)) return 5;
        if (s.is(Blocks.GRAY_WOOL) || s.is(Blocks.CYAN_TERRACOTTA)) return 1;
        return 3;
    }

    private static void hollows(BlockState s) {
        if (glass(s)) drops[GEMSTONES]++;
        else if (mithril(s)) drops[POWDER] += powder(s);
    }

    /** 0 = not broken, 1 = broken, 2 = broken and opens up the blocks behind. */
    private static int tunnels(BlockState s) {
        if (s.is(Blocks.PACKED_ICE)) {
            drops[PITY] += 2;
            drops[ICE]++;
            return 2;
        }
        if (glass(s)) {
            drops[PITY] += 2;
            drops[GEMSTONES]++;
            return 2;
        }
        if (s.is(Blocks.POLISHED_DIORITE)) {
            drops[PITY] += 4;
            drops[TITANIUM]++;
            return 1;
        }
        if (s.is(Blocks.INFESTED_STONE) || s.is(Blocks.LIGHT_GRAY_CARPET)) {
            drops[HARDSTONE]++;
            return 1;
        }
        if (mithril(s)) {
            drops[PITY]++;
            drops[MITHRIL]++;
            drops[POWDER] += powder(s);
            return 1;
        }
        if (s.is(Blocks.BROWN_TERRACOTTA) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.SMOOTH_RED_SANDSTONE)) {
            drops[PITY] += 2;
            drops[UMBER]++;
            return 1;
        }
        if (s.is(Blocks.INFESTED_COBBLESTONE) || s.is(Blocks.CLAY)) {
            drops[PITY] += 2;
            drops[TUNGSTEN]++;
            return 1;
        }
        return 0;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (boxes.isEmpty() || !cfg().pickobulusOutline) return;
        int color = 0xFF000000 | TeslaMapsConfig.parseColor(cfg().colorPickobulus);
        for (int i = 0, n = boxes.size(); i < n; i++) {
            ESPRenderer.drawBoxOutline(pose, boxes.get(i), color, 2f, cam, false);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || !visible || !cfg().pickobulusHud || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        if (l == lines) w = width;
        else for (String s : l) w = Math.max(w, mc.font.width(s));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * 10 + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
