package com.teslamaps.features;

// After SkyHanni's EstimatedWardrobePrice / AbstractWardrobeApi.createPriceLore
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wardrobe slot buttons list the estimated value of every piece in their set. */
public final class WardrobeValue {

    private static final Pattern TITLE = Pattern.compile("^\\(\\d+/\\d+\\) (Armor|Equipment) Sets$|^Wardrobe \\(\\d+/\\d+\\)$");

    private static ItemStack lastStack;
    private static long lastAt;
    private static List<Component> lastLines = List.of();

    private WardrobeValue() {}

    public static void register() {
        Feature feature = Feature.of("WardrobeValue").when(() -> TeslaMapsConfig.get().wardrobeValue);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!feature.active() || lines.isEmpty()) return;
            List<Component> add = lines(stack);
            if (add.isEmpty()) return;
            lines.add(Component.empty());
            lines.addAll(add);
        });
    }

    private static List<Component> lines(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return List.of();
        Slot focused = ((com.teslamaps.mixin.HandledScreenAccessor) cs).getFocusedSlot();
        if (focused == null || focused.getItem() != stack || focused.index < 36 || focused.index > 44) return List.of();
        long now = System.currentTimeMillis();
        if (stack == lastStack && now - lastAt < 1000) return lastLines;
        lastStack = stack;
        lastAt = now;
        lastLines = build(cs, focused.index - 36);
        return lastLines;
    }

    private static List<Component> build(AbstractContainerScreen<?> cs, int col) {
        Matcher m = TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString()));
        if (!m.matches()) return List.of();
        String kind = "Equipment".equals(m.group(1)) ? "Equipment" : "Armor";
        PriceManager.ensureFresh();
        var slots = cs.getMenu().slots;
        List<Component> out = new ArrayList<>(6);
        double total = 0;
        for (int row = 0; row < 4; row++) {
            int idx = col + row * 9;
            if (idx >= slots.size()) break;
            ItemStack piece = slots.get(idx).getItem();
            if (piece.isEmpty() || ItemUtil.skyblockId(piece).isEmpty()) continue;
            double v = EstimatedValue.compute(piece);
            if (v <= 0) continue;
            total += v;
            out.add(Component.literal("  §7- ").append(piece.getHoverName().copy())
                    .append(Component.literal("§7: §6" + EstimatedValue.shortFormat(v))));
        }
        if (out.isEmpty()) return List.of();
        out.add(0, Component.literal("§aEstimated " + kind + " Value:"));
        out.add(Component.literal(" §aTotal Value: §6§l" + EstimatedValue.shortFormat(total) + " coins"));
        return out;
    }
}
