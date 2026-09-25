package com.teslamaps.features;

// Ported from SkyHanni's HideNotClickableItems.kt / HideNotClickableItemsFeature.kt (lean, no repo filters)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Greys out own items the open menu does not take, with the reason in the tooltip; optional click block. */
public final class HideNotClickable {

    private record Reason(String text, boolean bypass) {}

    private static Map<ItemStack, Reason> reasons = Map.of();
    private static Set<ItemStack> allowed = Set.of();
    private static int timer;

    private static final Set<String> SEEDS = Set.of("SEEDS", "CARROT_ITEM", "POTATO_ITEM", "PUMPKIN_SEEDS",
            "SUGAR_CANE", "MELON_SEEDS", "CACTUS", "INK_SACK-3", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE");
    private static final Set<String> BIRD_FOOD = Set.of("BAG_OF_SEEDS", "WRIGGLEWORM", "YOGI_BERRY");
    private static final Set<String> CANDY = Set.of("Green Candy", "Purple Candy", "Dark Candy");
    private static final Set<String> EQUIPMENT = Set.of("NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET",
            "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
    private static final Set<String> AUCTION = Set.of("Auction House", "Co-op Auction House",
            "Create BIN Auction", "Create Auction");

    private HideNotClickable() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        com.teslamaps.registry.Feature.of("HideNotClickable").section("Inventory").when(() -> cfg().hideNotClickable)
                .tick(HideNotClickable::tick)
                .config("Inventory", b -> {
                    b.toggle("Hide Not Clickable Items", () -> cfg().hideNotClickable, v -> cfg().hideNotClickable = v)
                            .desc("Greys out own items the open menu won't take (NPC sell, salvage, trade, bazaar/AH, bags, equipment...), reason in the tooltip. Hold ctrl to bypass");
                    b.toggle("  Block Clicks", () -> cfg().hideNotClickableBlock, v -> cfg().hideNotClickableBlock = v);
                    b.toggle("  Green Line On Usable Items", () -> cfg().hideNotClickableGreen, v -> cfg().hideNotClickableGreen = v);
                    b.slider("  Grey Opacity", 0.1f, 1f, () -> cfg().hideNotClickableOpacity, v -> cfg().hideNotClickableOpacity = v, 2);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (reasons.isEmpty() || lines.isEmpty() || !active()) return;
            Reason r = reasons.get(stack);
            if (r == null || (r.bypass && bypass())) return;
            Component first = lines.get(0);
            lines.clear();
            lines.add(Component.literal("§7" + StringUtil.strip(first.getString())));
            lines.add(Component.empty());
            lines.add(Component.literal("§c" + r.text));
            if (r.bypass) lines.add(Component.literal("  §7(Bypass by holding ctrl)"));
        });
    }

    private static boolean active() {
        TeslaMapsConfig c = cfg();
        return c.hideNotClickable && c.section("Inventory");
    }

    private static boolean bypass() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long h = mc.getWindow().handle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen cs)) {
            if (!reasons.isEmpty() || !allowed.isEmpty()) { reasons = Map.of(); allowed = Set.of(); }
            timer = 0;
            return;
        }
        if (timer-- > 0) return;
        timer = 4;
        if (!SkyblockUtils.isInSkyblock()) { reasons = Map.of(); allowed = Set.of(); return; }

        String title = StringUtil.stripTrim(cs.getTitle().getString());
        AbstractContainerMenu menu = cs.getMenu();
        boolean npcSell = ItemProtection.isNpcSellMenu(menu);
        Map<ItemStack, Reason> out = new IdentityHashMap<>();
        Set<ItemStack> ok = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            int[] rule = {0};
            Reason r = check(title, npcSell, st, rule);
            if (r != null) out.put(st, r);
            else if (rule[0] == 1) ok.add(st);
        }
        reasons = out;
        allowed = ok;
    }

    private static Reason no(String text) { return new Reason(text, true); }

    private static Reason menuItem(String what) { return new Reason("The SkyBlock Menu cannot " + what + "!", false); }

    // rule[0] = 1 when the menu has a rule and the item passes it (green line)
    private static Reason check(String title, boolean npcSell, ItemStack st, int[] rule) {
        String id = ItemUtil.skyblockId(st);
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        boolean menu = id.equals("SKYBLOCK_MENU");
        rule[0] = 1;

        if (title.startsWith("You    ")) {
            if (soulbound(st)) return no("Soulbound items cannot be traded!");
            if (menu) return menuItem("be traded");
            if (sack(id, name)) return no("Sacks cannot be traded!");
            rule[0] = 0;
            return null;
        }
        if (title.startsWith("Salvage Item")) {
            if (menu) return menuItem("be salvaged");
            if (recombobulated(st)) return no("This item should not be salvaged! (Recombobulated)");
            if (ItemUtil.loreContains(st, "LEGENDARY DUNGEON")) return no("This item should not be salvaged! (Legendary)");
            if (museum(st)) return no("This item cannot be salvaged! (Donated to Museum)");
            if (!ItemUtil.loreContains(st, "DUNGEON")) return no("This item cannot be salvaged!");
            return null;
        }
        if (title.startsWith("Bazaar")) {
            if (menu) return menuItem("be sold on the Bazaar");
            if (!PriceManager.hasBazaar(id)) return no("This item is not a Bazaar Product!");
            return null;
        }
        if (AUCTION.contains(title)) {
            if (menu) return menuItem("be auctioned");
            if (PriceManager.hasBazaar(id)) return no("Bazaar Products cannot be auctioned!");
            if (soulbound(st)) return no("Soulbound items cannot be auctioned!");
            if (sack(id, name)) return no("Sacks cannot be auctioned!");
            return null;
        }
        if (title.startsWith("Accessory Bag")) {
            if (menu) return menuItem("go into the accessory bag");
            if (ItemUtil.loreContains(st, "ACCESSORY") || ItemUtil.loreContains(st, "HATCESSORY")) return null;
            return no("This item is not an accessory!");
        }
        if (title.startsWith("Sack of Sacks")) {
            if (menu || sack(id, name)) return null;
            return no("This item is not a sack!");
        }
        if (title.startsWith("Basket of Seeds")) {
            if (menu) return menuItem("go into the basket of seeds");
            return SEEDS.contains(id) ? null : no("This item is not a seed!");
        }
        if (title.startsWith("Nether Wart Pouch")) {
            if (menu) return menuItem("go into the nether wart pouch");
            return id.equals("NETHER_STALK") ? null : no("This item is not a nether wart!");
        }
        if (title.startsWith("Trick or Treat Bag")) {
            if (menu) return menuItem("go into the trick or treat bag");
            return CANDY.contains(name) ? null : no("This item is not a spooky candy!");
        }
        if (title.startsWith("Fishing Bag")) {
            if (menu) return menuItem("go into the fishing bag");
            for (String l : ItemUtil.loreLines(st)) if (l.trim().equals("Fishing Bait")) return null;
            return no("This item is not a fishing bait!");
        }
        if (title.startsWith("Potion Bag")) {
            if (menu) return menuItem("go into the potion bag");
            return name.endsWith(" Potion") || name.equals("Water Bottle") ? null : no("This item is not a potion!");
        }
        if (title.startsWith("Attribute Fusion")) {
            CompoundTag tag = tag(st);
            return tag != null && tag.contains("attributes") ? null : no("This item has no attributes!");
        }
        if (title.startsWith("Your Equipment")) {
            if (menu) return menuItem("be put into your equipment");
            return EQUIPMENT.contains(SkyblockItems.category(id)) ? null : no("This item cannot be put into your equipment!");
        }
        if (title.equals("Research Center")) {
            return id.endsWith("_FOSSIL") || id.equals("HELIX") ? null : no("Not a fossil!");
        }
        if (title.equals("Birdfeeder")) {
            return BIRD_FOOD.contains(id) ? null : no("Not bird food!");
        }
        if (title.equals("Fossil Excavator")) {
            return id.equals("SUSPICIOUS_SCRAP") || SkyblockItems.category(id).equals("CHISEL") ? null : no("Not a chisel or scrap!");
        }
        if (title.equals("Rift Transfer Chest")) {
            return ItemUtil.loreContains(st, "Rift-Transferable") || ItemUtil.loreContains(st, "Rift-Exportable")
                    ? null : no("Not Rift-Transferable or Rift-Exportable!");
        }
        if (title.contains("Ender Chest") || title.contains("Backpack") || title.equals("Storage")) {
            rule[0] = 0;
            return menu ? menuItem("be put into the storage") : null;
        }
        if ((title.equals("Chest") || title.equals("Large Chest")) && SkyblockUtils.isInArea("Private Island")) {
            rule[0] = 0;
            return soulbound(st) ? no("This item cannot be stored into a chest!") : null;
        }
        if (npcSell && !SkyblockUtils.isInArea("The Rift")) {
            if (id.isEmpty()) { rule[0] = 0; return null; }
            if (menu || (!SkyblockItems.hasNpcPrice(id) || SkyblockItems.npcPrice(id) <= 0) && !ItemUtil.loreContains(st, "Click to sell!"))
                return menu ? menuItem("be sold") : no("This item cannot be sold at the NPC!");
            if (museum(st)) return no("This item cannot be sold at the NPC! (Donated to Museum)");
            if (recombobulated(st)) return no("This item should not be sold at the NPC! (Recombobulated)");
            return null;
        }
        rule[0] = 0;
        return null;
    }

    private static CompoundTag tag(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    private static boolean soulbound(ItemStack st) {
        for (String l : ItemUtil.loreLines(st)) if (l.contains("Soulbound") && l.contains("*")) return true;
        return false;
    }

    private static boolean museum(ItemStack st) {
        CompoundTag t = tag(st);
        return t != null && t.contains("donated_museum");
    }

    private static boolean recombobulated(ItemStack st) {
        CompoundTag t = tag(st);
        return t != null && t.getIntOr("rarity_upgrades", 0) > 0;
    }

    private static boolean sack(String id, String name) {
        return id.endsWith("_SACK") || name.endsWith(" Sack");
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if ((reasons.isEmpty() && allowed.isEmpty()) || !active() || bypass()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int alpha = (int) (Math.max(0.1f, Math.min(1f, cfg().hideNotClickableOpacity)) * 255);
        int grey = (alpha << 24) | 0x2A2A2A;
        boolean green = cfg().hideNotClickableGreen;
        for (Slot slot : cs.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            ItemStack st = slot.getItem();
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            if (reasons.containsKey(st)) {
                ctx.fill(x, y, x + 16, y + 16, grey);
            } else if (green && allowed.contains(st)) {
                ctx.fill(x, y + 15, x + 16, y + 16, 0xC855FF55);
            }
        }
    }

    /** Swallows clicks on greyed items when enabled; the SkyBlock Menu can't be bypassed. */
    public static boolean blockClick(Slot slot) {
        if (slot == null || !cfg().hideNotClickableBlock || reasons.isEmpty() || !active()) return false;
        if (!(slot.container instanceof Inventory)) return false;
        Reason r = reasons.get(slot.getItem());
        return r != null && !(r.bypass && bypass());
    }
}
