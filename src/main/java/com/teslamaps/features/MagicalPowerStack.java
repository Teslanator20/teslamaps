package com.teslamaps.features;

// Ported from SkyHanni's MagicalPowerDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MagicalPowerStack {

    private record Label(int slot, String text, int color) {}

    private static final String[] RARITIES = {"VERY SPECIAL", "SPECIAL", "UNCOMMON", "COMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final int[] MP = {5, 3, 5, 3, 8, 12, 16, 22};
    private static final int[] COLORS = {0xFFFF5555, 0xFFFF5555, 0xFF55FF55, 0xFFFFFFFF, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF};

    private static List<Label> labels = List.of();
    private static int ticks = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MagicalPowerStack").section("Inventory")
                .when(() -> cfg().magicalPowerStack)
                .tick(MagicalPowerStack::tick);
    }

    private static boolean accepted(String title) {
        return title.startsWith("Accessory Bag") || title.equals("Auctions Browser")
                || title.equals("Manage Auctions") || title.startsWith("Auctions: \"");
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        List<Slot> slots = cs.getMenu().slots;
        if (title.startsWith("Abiphone ")) readContacts(slots);
        if (!accepted(title)) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        List<Label> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            int r = accessoryRarity(stack);
            if (r < 0) continue;
            int mp = MP[r];
            String id = ItemUtil.skyblockId(stack);
            if (id.equals("HEGEMONY_ARTIFACT")) mp *= 2;
            else if (id.equals("RIFT_PRISM")) mp = 11;
            else if (id.startsWith("ABICASE_")) mp += cfg().abiphoneContacts / 2;
            out.add(new Label(i, String.valueOf(mp), cfg().magicalPowerRarityColor ? COLORS[r] : 0xFFAAAAAA));
        }
        labels = out;
    }

    private static int accessoryRarity(ItemStack stack) {
        List<String> lore = ItemUtil.loreLines(stack);
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line = StringUtil.stripTrim(lore.get(i)).replace(" DUNGEON", "");
            if (line.isEmpty()) continue;
            if (!line.contains("ACCESSORY") && !line.contains("HATCESSORY")) return -1;
            for (int r = 0; r < RARITIES.length; r++) {
                if (line.contains(RARITIES[r] + " ACCESSORY") || line.contains(RARITIES[r] + " HATCESSORY")) return r;
            }
            return -1;
        }
        return -1;
    }

    private static void readContacts(List<Slot> slots) {
        if (slots.size() <= 51) return;
        for (String line : ItemUtil.loreLines(slots.get(51).getItem())) {
            String s = StringUtil.stripTrim(line);
            if (!s.startsWith("Your contacts: ")) continue;
            int slash = s.indexOf('/');
            if (slash < 0) return;
            try {
                int n = Integer.parseInt(s.substring(15, slash).trim());
                if (n != cfg().abiphoneContacts) {
                    cfg().abiphoneContacts = n;
                    TeslaMapsConfig.save();
                }
            } catch (NumberFormatException ignored) {}
            return;
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty() || !cfg().magicalPowerStack || !cfg().section("Inventory")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            ctx.text(mc.font, l.text, left + slot.x + 17 - mc.font.width(l.text), top + slot.y + 9, l.color);
        }
    }
}
