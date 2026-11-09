package com.teslamaps.features.fishing;

// Ported from SkyHanni's OdgerTotalCaught.kt, OdgerWaypoint.kt, TrophyFishFillet.kt and NEU's TrophyRewardOverlay
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OdgerHelper {

    private static final BlockPos ODGER = new BlockPos(-373, 207, -808);
    private static final Map<String, int[]> FILLET = Map.ofEntries(
            Map.entry("sulphur_skitter", new int[] {40, 60, 80, 120}),
            Map.entry("obfuscated_fish_1", new int[] {16, 24, 32, 48}),
            Map.entry("steaming_hot_flounder", new int[] {20, 28, 40, 60}),
            Map.entry("obfuscated_fish_2", new int[] {40, 60, 80, 120}),
            Map.entry("gusher", new int[] {32, 48, 64, 96}),
            Map.entry("blobfish", new int[] {4, 8, 12, 16}),
            Map.entry("slugfish", new int[] {40, 60, 80, 120}),
            Map.entry("obfuscated_fish_3", new int[] {400, 700, 1000, 1300}),
            Map.entry("flyfish", new int[] {32, 48, 64, 96}),
            Map.entry("lava_horse", new int[] {12, 16, 20, 24}),
            Map.entry("mana_ray", new int[] {40, 60, 80, 120}),
            Map.entry("volcanic_stonefish", new int[] {20, 28, 40, 60}),
            Map.entry("vanille", new int[] {80, 120, 160, 240}),
            Map.entry("skeleton_fish", new int[] {32, 48, 64, 96}),
            Map.entry("moldfin", new int[] {32, 48, 64, 96}),
            Map.entry("soul_fish", new int[] {32, 48, 64, 96}),
            Map.entry("karate_fish", new int[] {40, 60, 80, 120}),
            Map.entry("golden_fish", new int[] {400, 700, 1000, 1300}));

    private record Trophy(String key, int tier) {}

    private static Feature feature;
    private static boolean fishInInv, inIsle, lavaRod;
    private static int ticks;
    private static List<String> panel = List.of();
    private static long panelAt;

    private OdgerHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("OdgerHelper").section("Fishing").when(() -> cfg().odgerHelper)
                .tick(OdgerHelper::tick)
                .world(OdgerHelper::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Trophy Fishing Extras");
                    b.toggle("Odger Helpers", () -> c.odgerHelper, v -> c.odgerHelper = v)
                            .desc("Trophy fish QoL around Odger: total per fish in his menu, a waypoint to him and the Magmafish you get for filleting")
                            .tags("odger", "trophy", "fillet", "magmafish", "crimson");
                    b.toggle("  Total In Odger Menu", () -> c.odgerTotal, v -> c.odgerTotal = v)
                            .desc("\"Total: N\" per trophy fish, in the colour of the best tier you have");
                    b.toggle("  Odger Waypoint", () -> c.odgerWaypoint, v -> c.odgerWaypoint = v)
                            .desc("Crimson Isle, trophy fish in the inventory and no lava rod in hand");
                    b.toggle("  Fillet Value Tooltip", () -> c.trophyFilletTooltip, v -> c.trophyFilletTooltip = v)
                            .desc("Magmafish and coins for filleting, hold Shift for the whole stack");
                    b.toggle("  Fillet Panel", () -> c.trophyFilletPanel, v -> c.trophyFilletPanel = v)
                            .desc("Next to Odger's menus: Magmafish yield of every trophy fish in your inventory");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            TeslaMapsConfig c = cfg();
            if (!c.odgerHelper || feature == null || stack.isEmpty() || !feature.active()) return;
            if (c.odgerTotal) addTotal(stack, lines);
            if (c.trophyFilletTooltip) addFillet(stack, lines);
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs) || !odgerMenu(screen.getTitle().getString())) return;
            panelAt = 0;
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                TeslaMapsConfig c = cfg();
                if (!c.odgerHelper || !c.trophyFilletPanel || feature == null || !feature.active()) return;
                if (System.currentTimeMillis() - panelAt > 500) buildPanel();
                drawPanel(cs, g);
            });
        });
    }

    private static boolean odgerMenu(String title) {
        String t = StringUtil.stripTrim(title);
        return t.contains("Fillet") || t.equals("Odger") || t.equals("Trophy Fish");
    }

    private static Trophy trophy(ItemStack stack) {
        String id = ItemUtil.skyblockId(stack);
        int i = id == null ? -1 : id.lastIndexOf('_');
        if (i <= 0) return null;
        int tier = java.util.Arrays.asList(TrophyFishData.TIERS).indexOf(id.substring(i + 1));
        if (tier < 0) return null;
        String key = id.substring(0, i).toLowerCase(Locale.ROOT);
        return FILLET.containsKey(key) ? new Trophy(key, tier) : null;
    }

    private static void addTotal(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null || !StringUtil.stripTrim(mc.gui.screen().getTitle().getString()).equals("Trophy Fish")) return;
        int bronze = -1;
        boolean discovered = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = StringUtil.stripTrim(lines.get(i).getString());
            if (l.contains("Discovered")) discovered = true;
            if (bronze < 0 && l.startsWith("Bronze")) bronze = i;
        }
        if (!discovered || bronze <= 0) return;
        String key = TrophyFishData.keyFor(StringUtil.stripTrim(stack.getHoverName().getString()));
        if (key == null) return;
        int[] c = TrophyFishData.counts(key);
        int best = 0;
        long total = 0;
        for (int t = 0; t < 4; t++) {
            total += c[t];
            if (c[t] > 0) best = t;
        }
        lines.add(bronze + 1, Component.empty());
        lines.add(bronze + 2, Component.literal("§7Total: " + TrophyFishData.TIER_COLORS[best] + String.format(Locale.ROOT, "%,d", total)));
    }

    private static void addFillet(ItemStack stack, List<Component> lines) {
        Trophy t = trophy(stack);
        if (t == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null && mc.gui.screen().getTitle().getString().contains("Sack")) return;
        boolean shift = GLFW.glfwGetKey(mc.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) == com.mojang.blaze3d.platform.InputConstants.PRESS;
        long value = (long) FILLET.get(t.key)[t.tier] * (shift ? stack.getCount() : 1);
        double coins = value * PriceManager.getPrice("MAGMA_FISH");
        lines.add(Component.literal("§7Fillet: §8" + String.format(Locale.ROOT, "%,d", value) + " Magmafish §7(§6" + ItemPrice.format(coins) + "§7)"));
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        inIsle = SkyblockUtils.isInArea("Crimson Isle");
        lavaRod = FishingHelpers.holdingLavaRod();
        boolean found = false;
        if (inIsle && cfg().odgerWaypoint) {
            for (int i = 0; i < 36 && !found; i++) found = trophy(mc.player.getInventory().getItem(i)) != null;
        }
        fishInInv = found;
    }

    private static void buildPanel() {
        panelAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { panel = List.of(); return; }
        Map<Trophy, Integer> amounts = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            Trophy t = trophy(st);
            if (t != null) amounts.merge(t, st.getCount(), Integer::sum);
        }
        List<String> out = new ArrayList<>();
        out.add("§6Fillet Value");
        long total = 0;
        for (Map.Entry<Trophy, Integer> e : amounts.entrySet()) {
            Trophy t = e.getKey();
            long v = (long) FILLET.get(t.key)[t.tier] * e.getValue();
            total += v;
            TrophyFishData.Fish f = TrophyFishData.byKey(t.key);
            String name = f == null ? t.key : f.display();
            out.add(TrophyFishData.TIER_COLORS[t.tier] + e.getValue() + "x " + name + " §8" + String.format(Locale.ROOT, "%,d", v));
        }
        if (amounts.isEmpty()) out.add("§7No trophy fish in inventory");
        else out.add("§7Total: §8" + String.format(Locale.ROOT, "%,d", total) + " Magmafish §7(§6"
                + ItemPrice.format(total * PriceManager.getPrice("MAGMA_FISH")) + "§7)");
        panel = out;
    }

    private static void drawPanel(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        List<String> lines = panel;
        if (lines.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        w += 8;
        HandledScreenAccessor a = (HandledScreenAccessor) cs;
        int x = a.getX() - w - 6, y = a.getY();
        if (x < 2) return;
        HudStyle.screenPanel(g, x, y, w, 6 + lines.size() * 10, 4);
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFFFF);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!c.odgerWaypoint || !inIsle || !fishInInv || lavaRod) return;
        ESPRenderer.drawFilledBox(pose, new AABB(ODGER), 0x80FFFFFF, cam, true);
        ESPRenderer.drawText(pose, "§fOdger", new Vec3(ODGER.getX() + 0.5, ODGER.getY() + 1.5, ODGER.getZ() + 0.5), 1.5f, cam);
    }
}
