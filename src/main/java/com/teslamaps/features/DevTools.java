package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Internal name / uuid / prices in the tooltip, copy keys, slot numbers, items the repo lacks. */
public final class DevTools {

    private DevTools() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        com.teslamaps.registry.Feature.of("DevTools")
                .config("Interface", b -> {
                    b.label("Dev Tools");
                    b.toggle("Dev Item Info", () -> cfg().devItemInfo, v -> cfg().devItemInfo = v)
                            .desc("Tooltip lines: internal name, rarity, category, NPC / Bazaar / BIN / craft price, uuid");
                    b.keybind("  Copy Internal Name Key (over slot)", () -> cfg().devCopyIdKey, v -> cfg().devCopyIdKey = v);
                    b.keybind("  Copy Item NBT Key (over slot)", () -> cfg().devCopyNbtKey, v -> cfg().devCopyNbtKey = v);
                    b.keybind("  Show Slot Numbers (hold)", () -> cfg().devSlotNumbersKey, v -> cfg().devSlotNumbersKey = v);
                    b.toggle("  Highlight Items Missing From Repo", () -> cfg().devMissingRepoItems, v -> cfg().devMissingRepoItems = v);
                    b.keybind("  Dump Open Menu Key", () -> cfg().dumpGuiKey, v -> cfg().dumpGuiKey = v)
                            .desc("Writes the open container to config/teslamaps/gui_dump.txt, shift = tooltip");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().devItemInfo || lines.isEmpty() || stack.isEmpty()) return;
            addInfo(stack, lines);
        });
    }

    private static void addInfo(ItemStack stack, java.util.List<Component> lines) {
        String id = ItemUtil.skyblockId(stack);
        if (!id.isEmpty()) {
            lines.add(Component.literal("§8Internal: §7" + id));
            String tier = SkyblockItems.tier(id);
            String cat = SkyblockItems.category(id);
            if (!tier.isEmpty() || !cat.isEmpty())
                lines.add(Component.literal("§8Rarity: §7" + (tier.isEmpty() ? "?" : tier) + " §8Category: §7" + (cat.isEmpty() ? "?" : cat)));
            StringBuilder p = new StringBuilder();
            if (SkyblockItems.hasNpcPrice(id)) p.append("§8NPC: §7").append(NumberUtil.formatSuffixed(SkyblockItems.npcPrice(id))).append(' ');
            Double bzBuy = PriceManager.bazaarBuyOrNull(id), bzSell = PriceManager.bazaarSellOrNull(id);
            if (bzBuy != null || bzSell != null)
                p.append("§8BZ: §7").append(bzBuy == null ? "-" : NumberUtil.formatSuffixed(bzBuy)).append('/')
                        .append(bzSell == null ? "-" : NumberUtil.formatSuffixed(bzSell)).append(' ');
            Double bin = PriceManager.lowestBinOrNull(id);
            if (bin != null) p.append("§8BIN: §7").append(NumberUtil.formatSuffixed(bin)).append(' ');
            Double craft = CraftCost.costOrNull(id, PriceManager.SOURCE_BAZAAR_BUY);
            if (craft != null) p.append("§8Craft: §7").append(NumberUtil.formatSuffixed(craft));
            if (!p.isEmpty()) lines.add(Component.literal(p.toString().trim()));
        }
        String uuid = ItemUtil.itemUuid(stack);
        if (uuid != null && !uuid.isEmpty()) lines.add(Component.literal("§8UUID: §7" + uuid));
    }

    /** Copy keys over a hovered slot. */
    public static boolean onKey(int key, Slot hovered) {
        if (key == -1 || hovered == null || hovered.getItem().isEmpty()) return false;
        TeslaMapsConfig c = cfg();
        ItemStack stack = hovered.getItem();
        String text;
        if (key == c.devCopyIdKey) {
            text = ItemUtil.skyblockId(stack);
            if (text.isEmpty()) text = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } else if (key == c.devCopyNbtKey) {
            text = StorageCache.encode(stack);
        } else {
            return false;
        }
        if (text == null || text.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(text);
        if (mc.player != null) {
            String shown = text.length() > 60 ? text.substring(0, 60) + "…" : text;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Copied §f" + shown));
        }
        return true;
    }

    /** Slot numbers while the key is held, red slots for ids the item table lacks. */
    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = cfg();
        boolean numbers = c.devSlotNumbersKey != -1 && KeyNames.isDown(c.devSlotNumbersKey);
        boolean missing = c.devMissingRepoItems && SkyblockItems.loaded();
        if (!numbers && !missing) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            if (missing && !slot.getItem().isEmpty()) {
                String id = ItemUtil.skyblockId(slot.getItem());
                if (!id.isEmpty() && !SkyblockItems.known(id)) ctx.fill(x, y, x + 16, y + 16, 0x80FF0000);
            }
            if (numbers) {
                var pose = ctx.pose();
                pose.pushMatrix();
                pose.translate(x + 1, y + 1);
                pose.scale(0.5f, 0.5f);
                ctx.text(mc.font, String.valueOf(slot.index), 0, 0, 0xFFFFFF55);
                pose.popMatrix();
            }
        }
    }
}
