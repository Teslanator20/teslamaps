package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

// Blocks throwing away items worth more than a configured value. Only guards the two
// ways an item actually gets lost - the Q drop and dropping the cursor stack outside
// the slots - so normal moving around in inventories still works. Sneak overrides.
public class ItemProtection {

    private static long lastWarn = 0;

    private static double threshold() {
        return TeslaMapsConfig.get().itemProtectionValue * 1_000_000.0;
    }

    // ctrl = "yes I really mean it". Not sneak: shift is part of the sell gesture,
    // so a sneak override would cancel the guard exactly when it should fire.
    private static boolean overridden() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long handle = mc.getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return (c.itemProtection || c.itemProtectionUuid) && !overridden();
    }

    private static boolean tooValuable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (uuidProtected(stack)) return true;
        if (!TeslaMapsConfig.get().itemProtection) return false;
        if (TeslaMapsConfig.get().itemProtectionStarred && starred(stack)) return true;
        return EstimatedValue.compute(stack) * stack.getCount() >= threshold();
    }

    // ---------------------------------------------------------------- rarity

    public static final String[] RARITY_OPTIONS = {"Off", "Any", "Common", "Uncommon", "Rare", "Epic",
            "Legendary", "Mythic", "Divine", "Special"};
    private static final int RARITY_OFF = Integer.MAX_VALUE, RARITY_ANY = -2;

    private static int minRank(String v) {
        for (int i = 0; i < RARITY_OPTIONS.length; i++) {
            if (!RARITY_OPTIONS[i].equals(v)) continue;
            return i == 0 ? RARITY_OFF : i == 1 ? RARITY_ANY : i - 2;
        }
        return RARITY_OFF;
    }

    private static boolean rarityProtected(ItemStack stack, boolean hotbar) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtectionRarity || stack == null || stack.isEmpty()) return false;
        int min = minRank(hotbar ? c.itemProtectionHotbarRarity : c.itemProtectionInvRarity);
        if (min == RARITY_OFF) return false;
        if (min == RARITY_ANY) return true;
        return RarityBackground.rarityRank(stack) >= min;
    }

    private static int lastSel = -1;
    private static long lastSwitch;
    private static int tries;
    private static long firstTry;
    private static String tryKey;

    private static void trackSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int sel = mc.player.getInventory().getSelectedSlot();
        if (sel != lastSel) {
            if (lastSel != -1) lastSwitch = System.currentTimeMillis();
            lastSel = sel;
        }
    }

    /** Hotbar: hard block. Inventory: blocked until the 3rd try within 3s, if enabled. */
    private static boolean rarityBlocksDrop(ItemStack stack, boolean hotbar) {
        if (!TeslaMapsConfig.get().itemProtectionRarity || overridden()) return false;
        if (hotbar) {
            if (!rarityProtected(stack, true)) return false;
            warn(stack);
            return true;
        }
        if (!rarityProtected(stack, false)) return false;
        if (!TeslaMapsConfig.get().itemProtectionConfirm) {
            warn(stack);
            return true;
        }
        long now = System.currentTimeMillis();
        String key = stack.getHoverName().getString() + "#" + stack.getCount();
        if (!key.equals(tryKey) || now - firstTry > 3000) {
            tryKey = key;
            firstTry = now;
            tries = 0;
        }
        if (++tries >= 3) {
            tryKey = null;
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int left = 3 - tries;
            mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fDrop §e" + stack.getHoverName().getString()
                    + " §f" + left + " more time" + (left == 1 ? "" : "s") + " within 3s to confirm"));
            LoudSound.play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
        }
        return true;
    }

    private static boolean switchGuard(ItemStack held) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtectionRarity || !c.itemProtectionSwitchGuard || overridden()) return false;
        trackSelection();
        if (System.currentTimeMillis() - lastSwitch > 200) return false;
        if (!rarityProtected(held, true) && !rarityProtected(held, false)) return false;
        warn(held);
        return true;
    }

    private static boolean hotbarSlot(net.minecraft.world.inventory.Slot slot) {
        return slot != null && slot.container instanceof net.minecraft.world.entity.player.Inventory
                && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9;
    }

    // Devonian's ProtectStarredItems
    private static boolean starred(ItemStack stack) {
        net.minecraft.world.item.component.CustomData d = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (d == null) return false;
        net.minecraft.nbt.CompoundTag t = d.copyTag();
        return t.contains("upgrade_level") || t.contains("dungeon_item_level");
    }

    // ---------------------------------------------------------------- per-item (uuid)

    private static String uuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String id = ((com.teslamaps.injected.CustomizableStack) (Object) stack).teslamaps$uuid();
        return id.isEmpty() ? null : id;
    }

    public static boolean uuidProtected(ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtectionUuid || c.protectedItems.isEmpty()) return false;
        String id = uuid(stack);
        return id != null && c.protectedItems.contains(id);
    }

    /** Toggles protection of an item; true when the item has a uuid. */
    public static boolean toggle(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String id = uuid(stack);
        if (id == null) {
            mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fThis item has no uuid, it can not be protected"));
            return false;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean added = c.protectedItems.add(id);
        if (!added) c.protectedItems.remove(id);
        TeslaMapsConfig.save();
        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, added ? 1.5f : 0.7f);
        mc.player.sendSystemMessage(Component.literal((added ? "§a[TeslaMaps] §fProtected §e" : "§7[TeslaMaps] Unprotected §e")
                + stack.getHoverName().getString()));
        return true;
    }

    public static boolean onKey(int key, AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot hovered) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtectionUuid || c.itemProtectionKey == -1 || key != c.itemProtectionKey) return false;
        if (hovered == null || hovered.getItem().isEmpty()) return false;
        String title = com.teslamaps.utils.StringUtil.strip(screen.getTitle().getString());
        boolean own = hovered.container instanceof net.minecraft.world.entity.player.Inventory || isPersonalStorage(title);
        if (!own) return false;
        toggle(hovered.getItem());
        return true;
    }

    private static boolean isPersonalStorage(String t) {
        return t.equals("Storage") || t.startsWith("Storage (")
                || t.equals("Rift Storage") || t.startsWith("Rift Storage (")
                || t.startsWith("Ender Chest") || t.startsWith("Chest") || t.startsWith("Trapped Chest")
                || (t.contains("Backpack") && t.contains("(Slot #"))
                || t.startsWith("Medium Shelves") || t.startsWith("Wood Chest") || t.startsWith("Diamond Chest")
                || t.startsWith("Emerald Chest") || t.startsWith("Iron Chest") || t.startsWith("Gold Chest")
                || t.startsWith("Lapis Chest") || t.startsWith("Redstone Chest") || t.startsWith("Endstone Chest")
                || t.startsWith("Skull Chest") || t.startsWith("Weapon Rack") || t.startsWith("Armor Stand")
                || t.startsWith("Your Equipment and Stats") || t.contains("Equipment Sets")
                || t.contains("Armor Sets") || t.contains("Loadouts") || t.contains("Pets");
    }

    // ---------------------------------------------------------------- menus

    private static boolean isNpcSellButton(ItemStack stack) {
        String name = com.teslamaps.utils.StringUtil.strip(stack.getHoverName().getString());
        return name.equals("Sell Item") || name.equals("Sell Inventory")
                || com.teslamaps.utils.ItemUtil.loreContains(stack, "buyback");
    }

    public static boolean isNpcSellMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && isNpcSellButton(stack)) return true;
        }
        return false;
    }

    /** Trade, auction, dungeon chest, any NPC shop, and salvage for uuid items. Runs on every slot click. */
    public static boolean blockMenuClick(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (slot != null && c.itemProtectionRarity && c.itemProtectionRaritySell && !overridden()
                && slot.container instanceof net.minecraft.world.entity.player.Inventory
                && screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu
                && rarityProtected(slot.getItem(), false) && isNpcSellMenu(screen.getMenu())) {
            warnKept(slot.getItem(), "selling");
            return true;
        }
        if (slot == null || (!c.itemProtectionMenus && !c.itemProtectionUuid)) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || overridden()) return false;
        boolean uuid = uuidProtected(stack);
        if (!uuid && !(c.itemProtectionMenus && tooValuable(stack))) return false;

        String title = com.teslamaps.utils.StringUtil.strip(screen.getTitle().getString());
        String what = null;
        if (uuid && title.startsWith("Salvage Items")) what = "salvaging";
        else if (title.startsWith("You  ")) what = "trading";
        else if (title.endsWith("Auction House") || title.equals("Create Auction") || title.equals("Create BIN Auction")) what = "auctioning";
        else if (title.equals("Chest") && com.teslamaps.dungeon.DungeonManager.isInDungeon()) what = "putting away";
        else if (screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu
                && !isNpcSellButton(stack) && isNpcSellMenu(screen.getMenu())) what = "selling";
        if (what == null) return false;
        warnKept(stack, what);
        return true;
    }

    /** Protected item into an empty item frame on the island or garden. */
    public static boolean blockItemFrame(ItemStack held, net.minecraft.world.entity.Entity target) {
        if (!(target instanceof net.minecraft.world.entity.decoration.ItemFrame frame) || !frame.getItem().isEmpty()) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean uuidBlock = c.itemProtectionUuid && (uuidProtected(held) || SlotLock.blockSelectedDrop());
        boolean lockBlock = c.slotLockItemFrames && SlotLock.blockSelectedDrop();
        if (!uuidBlock && !lockBlock) return false;
        if (!com.teslamaps.utils.SkyblockUtils.isInArea("Private Island") && !com.teslamaps.utils.SkyblockUtils.isInGarden()) return false;
        warnKept(held, "framing");
        return true;
    }

    // ---------------------------------------------------------------- icon

    public static void renderSlots(net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtectionUuid || c.protectedItems.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        com.teslamaps.mixin.HandledScreenAccessor acc = (com.teslamaps.mixin.HandledScreenAccessor) cs;
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            if (uuidProtected(slot.getItem()))
                com.teslamaps.render.gui.LockIcon.draw(ctx, acc.getX() + slot.x, acc.getY() + slot.y);
        }
    }

    public static void renderHotbar(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        int left = ctx.guiWidth() / 2 - 91, top = ctx.guiHeight() - 19;
        for (int i = 0; i < 9; i++) {
            if (uuidProtected(mc.player.getInventory().getItem(i)))
                com.teslamaps.render.gui.LockIcon.draw(ctx, left + 3 + i * 20, top);
        }
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("ItemProtection")
                .when(() -> TeslaMapsConfig.get().itemProtectionUuid && !TeslaMapsConfig.get().protectedItems.isEmpty())
                .hud("itemprotection", ItemProtection::renderHotbar);
        com.teslamaps.registry.Feature.of("ItemProtectionRarity")
                .when(() -> TeslaMapsConfig.get().itemProtectionRarity && TeslaMapsConfig.get().itemProtectionSwitchGuard)
                .tick(ItemProtection::trackSelection);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                level.isClientSide() && blockItemFrame(player.getItemInHand(hand), entity)
                        ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
    }

    /** Q key on the selected hotbar slot. */
    public static boolean blockSelectedDrop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack held = mc.player.getInventory().getSelectedItem();
        if (switchGuard(held) || rarityBlocksDrop(held, true)) return true;
        if (!enabled()) return false;
        if (!tooValuable(held)) return false;
        warn(held);
        return true;
    }

    /** Clicking outside the slots while carrying an item drops it. */
    public static boolean blockCursorDrop(AbstractContainerScreen<?> screen, boolean overSlot) {
        if (overSlot) return false;
        ItemStack carried = screen.getMenu().getCarried();
        if (rarityBlocksDrop(carried, false)) return true;
        if (!enabled()) return false;
        if (!tooValuable(carried)) return false;
        warn(carried);
        return true;
    }

    /** Q on a hovered slot inside any inventory screen, and Q while carrying a stack. */
    public static boolean blockSlotDrop(net.minecraft.world.inventory.Slot slot) {
        ItemStack stack;
        if (slot != null) {
            stack = slot.getItem();
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return false;
            stack = screen.getMenu().getCarried();
        }
        boolean ownSlot = slot == null || slot.container instanceof net.minecraft.world.entity.player.Inventory;
        if (ownSlot && rarityBlocksDrop(stack, hotbarSlot(slot))) return true;
        if (!enabled()) return false;
        if (!tooValuable(stack)) return false;
        warn(stack);
        return true;
    }

    // NPC shops whose title is the plain merchant name - shift-clicking sells there.
    // Best effort list, extend as they turn up in game.
    private static final java.util.Set<String> SELL_GUIS = java.util.Set.of(
            "Adventurer", "Alchemist", "Armorsmith", "Bartender", "Blacksmith", "Builder",
            "Farm Merchant", "Fish Merchant", "Lumber Merchant", "Mine Merchant",
            "Weaponsmith", "Ophelia", "Market");

    /** Salvage destroys the item outright, a shop shift-click sells it. Ctrl overrides. */
    public static boolean blockDestructiveClick(AbstractContainerScreen<?> screen,
                                                net.minecraft.world.inventory.Slot slot, boolean shift) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemProtection || !c.itemProtectionSell || slot == null) return false;
        if (overridden()) return false;

        String title = com.teslamaps.utils.StringUtil.strip(screen.getTitle().getString());
        boolean salvage = title.startsWith("Salvage Items");
        boolean shop = SELL_GUIS.contains(title);
        if (!salvage && !shop) return false;

        boolean ownInventory = slot.container instanceof net.minecraft.world.entity.player.Inventory;
        ItemStack stack = slot.getItem();
        if (!tooValuable(stack)) return false;

        // salvage: the item sits in the upper container. shop: sold out of your own inventory.
        if (salvage && !ownInventory) { warnKept(stack, "salvaging"); return true; }
        if (shop && ownInventory && shift) { warnKept(stack, "selling"); return true; }
        return false;
    }

    private static void warnKept(ItemStack stack, String what) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1500) return;
        lastWarn = now;

        mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fBlocked " + what + " §e"
                + stack.getHoverName().getString() + " §7(hold ctrl to override)"));
        LoudSound.play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
    }

    private static void warn(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1500) return; // no spam while a key is held
        lastWarn = now;

        mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fBlocked dropping §e"
                + stack.getHoverName().getString() + " §7(hold ctrl to override)"));
        LoudSound.play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
    }
}
