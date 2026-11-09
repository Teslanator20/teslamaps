package com.teslamaps.features;

import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

// Ported from SkyHanni's PersonalCompactorOverlay.kt, grid layout from Skyblocker's CompactorDeletorPreview
public class CompactorPreview {

    /** Slots of a compactor/deletor tier, laid out as rows x columns. */
    public record Contents(int rows, int cols, List<ItemStack> slots, boolean enabled) implements TooltipComponent {}

    private static int[] gridForTier(int tier) {
        return switch (tier) {
            case 7000 -> new int[]{2, 6};
            case 6000 -> new int[]{1, 7};
            case 5000 -> new int[]{1, 3};
            case 4000 -> new int[]{1, 1};
            default -> null;
        };
    }

    private static String prefixFor(String id) {
        if (id.startsWith("PERSONAL_COMPACTOR_")) return "personal_compact_";
        if (id.startsWith("PERSONAL_DELETOR_")) return "personal_deletor_";
        return null;
    }

    private static int tierOf(String id) {
        try {
            return Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static final String[] MODES = {"Always", "Only With Key", "Hide With Key"};

    public static boolean previewVisible() {
        com.teslamaps.config.TeslaMapsConfig c = com.teslamaps.config.TeslaMapsConfig.get();
        String m = c.compactorPreviewMode;
        if ("Only With Key".equals(m)) return com.teslamaps.utils.KeyNames.isDown(c.compactorPreviewKey);
        if ("Hide With Key".equals(m)) return !com.teslamaps.utils.KeyNames.isDown(c.compactorPreviewKey);
        return true;
    }

    // SkyHanni's PersonalCompactorOverlay status icon: 0 none, 1 on, 2 off
    private static final java.util.IdentityHashMap<ItemStack, Integer> status = new java.util.IdentityHashMap<>();

    private static int status(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer s = status.get(stack);
        if (s != null) return s;
        int v = 0;
        String id = ItemUtil.skyblockId(stack);
        if (prefixFor(id) != null) {
            CustomData d = stack.get(DataComponents.CUSTOM_DATA);
            if (d != null) v = d.copyTag().getByteOr("PERSONAL_DELETOR_ACTIVE", (byte) 0) == 1 ? 1 : 2;
        }
        if (status.size() > 1000) status.clear();
        status.put(stack, v);
        return v;
    }

    public static void renderStatus(net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        com.teslamaps.config.TeslaMapsConfig c = com.teslamaps.config.TeslaMapsConfig.get();
        if (!c.compactorPreview || !c.compactorStatusIcon) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return;
        com.teslamaps.mixin.HandledScreenAccessor acc = (com.teslamaps.mixin.HandledScreenAccessor) cs;
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            int s = status(slot.getItem());
            if (s == 0) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(sx + 10, sy);
            pose.scale(0.75f, 0.75f);
            ctx.text(mc.font, s == 1 ? "✔" : "✖", 0, 0, s == 1 ? 0xFF55FF55 : 0xFFFF5555);
            pose.popMatrix();
        }
    }

    /** The slot grid for the tooltip image, null for anything that is not a compactor/deletor. */
    public static Contents contents(ItemStack stack) {
        String id = ItemUtil.skyblockId(stack);
        String prefix = prefixFor(id);
        if (prefix == null) return null;

        int[] grid = gridForTier(tierOf(id));
        if (grid == null) return null;

        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag tag = d.copyTag();

        int count = grid[0] * grid[1];
        List<ItemStack> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String itemId = tag.getStringOr(prefix + i, "");
            slots.add(itemId.isEmpty() ? ItemStack.EMPTY : SkyblockItems.stackNow(itemId));
        }
        boolean enabled = tag.getByteOr("PERSONAL_DELETOR_ACTIVE", (byte) 0) == 1;
        return new Contents(grid[0], grid[1], slots, enabled);
    }
}
