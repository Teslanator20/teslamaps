package com.teslamaps.features.bazaar;

// After Skyblocker's ReorderHelper and SkyHanni's BazaarCancelledBuyOrderClipboard
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BazaarReorder {

    private static final Pattern BUY = Pattern.compile("(?:coins from |from |)([\\d,]+)x missing items\\.");
    private static final Pattern SELL = Pattern.compile("([\\d,]+)x items\\.");
    private static final Pattern BUY_CANCEL = Pattern.compile("^(?:\\[Bazaar] )?Cancelled! Refunded ([\\d,.]+) coins from cancelling Buy Order!$", Pattern.CASE_INSENSITIVE);

    private static Object scanned;
    private static int wait;
    private static int missing;
    private static String missingItem;

    private BazaarReorder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BazaarReorder").when(() -> cfg().bzReorderHelper)
                .tick(BazaarReorder::tick)
                .serverChat(BazaarReorder::onChat)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Bazaar Reorder Helper", () -> c.bzReorderHelper, v -> c.bzReorderHelper = v)
                            .desc("Order options: Ctrl + click on the cancel terracotta copies the missing / left amount")
                            .tags("bazaar", "reorder", "clipboard", "cancel", "order");
                    b.toggle("  Copy Amount After Cancelling", () -> c.bzReorderOnCancel, v -> c.bzReorderOnCancel = v)
                            .desc("A cancelled buy order puts the missing amount into the clipboard, with a chat line to re-order");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Order options")) {
            scanned = null;
            return;
        }
        if (cs != scanned) { scanned = cs; wait = 0; }
        if (++wait != 10 && wait != 30) return;
        if (cs.getMenu().slots.size() <= 11) return;
        ItemStack stack = cs.getMenu().slots.get(11).getItem();
        if (!StringUtil.stripTrim(stack.getHoverName().getString()).contains("Cancel Order")) return;
        Integer n = amount(stack, BUY);
        if (n != null) {
            missing = n;
            missingItem = BazaarOrderTracker.lastClickedBuy ? BazaarOrderTracker.lastClickedItem : null;
        }
    }

    private static Integer amount(ItemStack stack, Pattern p) {
        String joined = String.join(" ", ItemUtil.loreLines(stack));
        Matcher m = p.matcher(joined);
        return m.find() ? BazaarOrderTracker.parseInt(m.group(1)) : null;
    }

    /** Slot click hook, never swallows the click. */
    public static void onSlotClick(AbstractContainerScreen<?> cs, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.bzReorderHelper || slot == null || slot.container instanceof Inventory) return;
        if (slot.index != 11 && slot.index != 13) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Order options")) return;
        ItemStack stack = slot.getItem();
        if (!stack.is(net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.green().asItem())) return;
        if (!com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                && !com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL)) return;
        Integer n = amount(stack, slot.index == 13 ? SELL : BUY);
        if (n == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(String.valueOf(n));
        message("§7Copied §e" + n + " §7to the clipboard.");
    }

    private static void onChat(String raw) {
        if (!cfg().bzReorderOnCancel || missing <= 0) return;
        String s = StringUtil.stripTrim(raw);
        if (!BUY_CANCEL.matcher(s).matches()) return;
        int n = missing;
        String item = missingItem;
        missing = 0;
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(String.valueOf(n));
        if (item == null) {
            message("§7Buy order cancelled, copied §e" + n + " §7to the clipboard.");
            return;
        }
        BazaarPurchaseHelper.expect(item);
        TickScheduler.schedule(1, () -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Buy order cancelled, copied §e" + n
                            + "§7. §eClick to re-order §f" + item)
                    .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/bz " + item))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /bz " + item)))));
        });
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        TickScheduler.schedule(1, () -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + text));
        });
    }
}
