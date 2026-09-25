package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// Ported from SkyOcean's DungeonGambling.kt / DungeonGamblingRenderer.kt / DungeonCard.kt
public final class DungeonGambling {

    private static final int ITEM_SCALE = 4;
    private static final int ITEM_COUNT = 50;
    private static final int WINNER_INDEX = ITEM_COUNT - 10;
    private static final int ITEM_GAP = 5;
    private static final int CARD_W = 24, CARD_H = 18;
    private static final int FULL_W = CARD_W * ITEM_SCALE + ITEM_GAP;
    private static final int FULL_H = CARD_H * ITEM_SCALE;

    private static final List<ItemStack> reel = new ArrayList<>();
    private static final List<Integer> colors = new ArrayList<>();
    private static long start = 0;
    private static int randomOffset;
    private static int lastSound;
    private static int menuId = -1;
    private static boolean rendering;

    private DungeonGambling() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DungeonGambling").when(() -> cfg().dungeonGambling)
                .tick(DungeonGambling::tick)
                .config("Croesus", b -> {
                    b.label("Gambling");
                    b.toggle("Dungeon Chest Gambling", () -> cfg().dungeonGambling, v -> cfg().dungeonGambling = v)
                            .desc("CSGO case opening on Obsidian/Bedrock chests, lands on the most valuable item. Esc skips");
                    b.toggle("  Also In Croesus", () -> cfg().dungeonGamblingCroesus, v -> cfg().dungeonGamblingCroesus = v);
                    b.slider("  Spin Time (s)", 2f, 10f, () -> cfg().dungeonGamblingTime, v -> cfg().dungeonGamblingTime = v, 1);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (menuId != -1) cancel();
            menuId = -1;
            return;
        }
        int id = cs.getMenu().containerId;
        if (id == menuId) return;
        String title = StringUtil.strip(cs.getTitle().getString()).trim();
        if (!title.equals("Obsidian") && !title.equals("Obsidian Chest")
                && !title.equals("Bedrock") && !title.equals("Bedrock Chest")) return;
        boolean ready = false;
        List<ItemStack> candidates = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack it = slot.getItem();
            if (it.isEmpty()) continue;
            if (it.is(Items.BARRIER) && DungeonManager.isInDungeon()) ready = true;
            if (it.is(Items.ARROW) && cfg().dungeonGamblingCroesus && hasToLine(it)) ready = true;
            if (!ItemUtil.skyblockId(it).isEmpty()) candidates.add(it);
        }
        if (!ready) return;
        menuId = id;
        if (candidates.isEmpty()) return;
        ItemStack winner = candidates.get(0);
        double best = -1;
        for (ItemStack it : candidates) {
            double v = value(it);
            if (v > best) { best = v; winner = it; }
        }
        init(candidates, winner);
    }

    private static boolean hasToLine(ItemStack it) {
        ItemLore lore = it.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component c : lore.lines()) if (StringUtil.strip(c.getString()).startsWith("To ")) return true;
        return false;
    }

    private static double value(ItemStack it) {
        String id = ItemUtil.skyblockId(it);
        if ("ENCHANTED_BOOK".equals(id)) {
            CustomData d = it.get(DataComponents.CUSTOM_DATA);
            if (d != null) {
                var ench = d.copyTag().getCompoundOrEmpty("enchantments");
                double v = 0;
                for (String k : ench.keySet()) {
                    int lvl = ench.getIntOr(k, 1);
                    String key = k.toUpperCase();
                    double p = PriceManager.getPrice("ENCHANTMENT_" + key + "_" + lvl);
                    if (p <= 0 && key.startsWith("ULTIMATE_")) p = PriceManager.getPrice("ENCHANTMENT_" + key.substring(9) + "_" + lvl);
                    v += p;
                }
                return v;
            }
        }
        return PriceManager.getPrice(id) * it.getCount();
    }

    private static void init(List<ItemStack> pool, ItemStack winner) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        reel.clear();
        colors.clear();
        for (int i = 0; i < ITEM_COUNT; i++) reel.add(pool.get(r.nextInt(pool.size())));
        reel.set(WINNER_INDEX, winner);
        for (ItemStack it : reel) colors.add(rarityColor(it));
        randomOffset = (4 * ITEM_SCALE + r.nextInt(4 * ITEM_SCALE)) * (r.nextBoolean() ? 1 : -1);
        lastSound = 0;
        start = System.currentTimeMillis();
    }

    private static int rarityColor(ItemStack it) {
        Component name = it.getHoverName();
        TextColor tc = name.getStyle().getColor();
        if (tc == null) for (Component s : name.getSiblings()) { if ((tc = s.getStyle().getColor()) != null) break; }
        if (tc != null) return 0xFF000000 | tc.getValue();
        String raw = name.getString();
        int i = raw.indexOf('§');
        if (i >= 0 && i + 1 < raw.length()) {
            net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(raw.charAt(i + 1));
            if (f != null && f.getColor() != null) return 0xFF000000 | f.getColor();
        }
        return 0xFFFFFFFF;
    }

    public static void cancel() {
        start = 0;
        reel.clear();
        colors.clear();
        rendering = false;
    }

    /** True while the animation eats clicks and keys. */
    public static boolean blocking() {
        return rendering;
    }

    private static float ease(float t) {
        return t < 0.5f ? (float) (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2f
                : (float) (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2f;
    }

    /** AbstractContainerScreen.extractRenderState HEAD. True = skip the vanilla screen. */
    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g) {
        rendering = false;
        if (start == 0 || reel.isEmpty() || cs.getMenu().containerId != menuId) return false;
        TeslaMapsConfig c = cfg();
        if (!c.dungeonGambling) return false;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float raw = (System.currentTimeMillis() - start) / (Math.max(1f, c.dungeonGamblingTime) * 1000f);
        float progress = Math.min(1f, Math.max(0f, raw + 0.25f));
        if (progress >= 0.96f && raw >= 1f) { cancel(); return false; }
        rendering = true;
        float endOffset = WINNER_INDEX * FULL_W * ease(progress);
        int soundIndex = (int) endOffset / FULL_W;
        if (soundIndex > lastSound) {
            if (mc.player != null) mc.player.playSound(SoundEvents.ITEM_PICKUP, 1f, 2f);
            lastSound = soundIndex;
        }
        g.fill(0, 0, w, h, 0xD0000000);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f - endOffset - (8 * ITEM_SCALE + randomOffset), (h - FULL_H) / 2f);
        int first = Math.max(0, (int) ((endOffset - w / 2f) / FULL_W) - 2);
        int last = Math.min(reel.size() - 1, (int) ((endOffset + w / 2f) / FULL_W) + 2);
        for (int i = first; i <= last; i++) {
            pose.pushMatrix();
            pose.translate(i * FULL_W, 0);
            pose.scale(ITEM_SCALE, ITEM_SCALE);
            int col = colors.get(i);
            int tint = 0x60000000 | (col & 0xFFFFFF);
            g.fillGradient(0, 0, CARD_W, CARD_H - 1, 0x80303030, tint);
            g.fill(0, CARD_H - 1, CARD_W, CARD_H, col);
            g.item(reel.get(i), CARD_W / 2 - 8, CARD_H / 2 - 8);
            pose.popMatrix();
        }
        pose.popMatrix();
        g.fill(w / 2 - 1, (h + FULL_H) / 2, w / 2 + 1, (h + FULL_H) / 2 + FULL_H / 4, 0xFFFF5555);
        g.fill(w / 2 - 1, (h - FULL_H) / 2 - FULL_H / 4, w / 2 + 1, (h - FULL_H) / 2, 0xFFFF5555);
        if (progress >= 0.96f) {
            Component name = reel.get(WINNER_INDEX).getHoverName();
            float s = 1f + 2f * Math.min(1f, (progress - 0.96f) / 0.04f);
            pose.pushMatrix();
            pose.translate(w / 2f, h * 0.2f);
            pose.scale(s, s);
            g.text(mc.font, name, -mc.font.width(name) / 2, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
        return true;
    }
}
