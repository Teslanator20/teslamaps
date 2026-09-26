package com.teslamaps.features.pets;

// Ported from Odin's PetKeybinds, plus uuid binds (Odin's /petkeys)
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.function.Predicate;

public final class PetKeybinds {

    private static final int[] PET_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20};
    private static Feature feature;
    private static long lastClick;

    private PetKeybinds() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PetKeybinds").section("Inventory").when(() -> cfg().petKeybinds);
    }

    public static boolean handleKey(AbstractContainerScreen<?> cs, KeyEvent event) {
        return handle(cs, event.key(), m -> m.matches(event));
    }

    public static boolean handleMouse(AbstractContainerScreen<?> cs, MouseButtonEvent event) {
        return handle(cs, KeyNames.forMouse(event.button()), m -> m.matchesMouse(event));
    }

    private static boolean handle(AbstractContainerScreen<?> cs, int bind, Predicate<KeyMapping> hotbarMatch) {
        TeslaMapsConfig c = cfg();
        if (!c.petKeybinds && !c.petFavorites) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!PetStore.isPetsMenu(title)) return false;
        if (PetMenus.toggleFavorite(cs, bind)) return true;
        if (!c.petKeybinds || feature == null || !feature.active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;

        if (bind != -1 && bind == c.petNextPageKey) return clickNamed(cs, "Next Page");
        if (bind != -1 && bind == c.petPrevPageKey) return clickNamed(cs, "Previous Page");

        int target = -1;
        boolean unequip = bind != -1 && bind == c.petUnequipKey;
        if (unequip) {
            for (Slot s : cs.getMenu().slots) {
                if (s.container instanceof Inventory) continue;
                if (ItemUtil.loreContains(s.getItem(), "Click to despawn!")) { target = s.index; break; }
            }
            if (target < 0) {
                msg("§cCouldn't find the equipped pet on this page.");
                return true;
            }
        } else {
            int want = -1;
            for (int i = 0; i < c.petKeys.length; i++) {
                if (c.petKeys[i] != -1 && c.petKeys[i] == bind) { want = i; break; }
            }
            if (want < 0) {
                for (int i = 0; i < 9; i++) {
                    if (hotbarMatch.test(mc.options.keyHotbarSlots[i])) { want = i; break; }
                }
            }
            if (want < 0) return false;

            if (mc.hasShiftDown()) {
                bindHovered(cs, want);
                return true;
            }
            String uuid = c.petKeyUuids[want];
            if (uuid != null) {
                target = findUuid(cs, uuid);
                if (target < 0) {
                    msg("§cPet " + (want + 1) + " is not on this page.");
                    return true;
                }
            } else {
                target = PET_SLOTS[want];
            }
        }

        if (target >= cs.getMenu().slots.size() || cs.getMenu().slots.get(target).getItem().isEmpty()) return true;
        if (!mc.player.containerMenu.getCarried().isEmpty()) return true;
        long now = System.currentTimeMillis();
        if (now - lastClick < 300) return true;

        if (!unequip && ItemUtil.loreContains(cs.getMenu().slots.get(target).getItem(), "Click to despawn!")) {
            if (c.petKeysCloseIfEquipped) {
                mc.player.closeContainer();
                return true;
            }
            if (c.petKeysNoUnequip) {
                msg("§cThat pet is already equipped!");
                return true;
            }
        }
        lastClick = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, target, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    /** Shift + pet key over a pet binds that pet's uuid; over anything else clears the bind. */
    private static void bindHovered(AbstractContainerScreen<?> cs, int index) {
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        JsonObject pi = hovered == null || hovered.container instanceof Inventory ? null : PetStore.petInfo(hovered.getItem());
        String uuid = pi == null ? null : PetStore.str(pi, "uuid");
        cfg().petKeyUuids[index] = uuid;
        TeslaMapsConfig.save();
        if (uuid == null) msg("§ePet key " + (index + 1) + " §7now uses slot position again.");
        else msg("§ePet key " + (index + 1) + " §7bound to " + hovered.getItem().getHoverName().getString());
    }

    private static int findUuid(AbstractContainerScreen<?> cs, String uuid) {
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            JsonObject pi = PetStore.petInfo(s.getItem());
            if (pi != null && uuid.equals(PetStore.str(pi, "uuid"))) return s.index;
        }
        return -1;
    }

    private static boolean clickNamed(AbstractContainerScreen<?> cs, String needle) {
        Minecraft mc = Minecraft.getInstance();
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory) continue;
            if (StringUtil.stripTrim(s.getItem().getHoverName().getString()).startsWith(needle)) {
                long now = System.currentTimeMillis();
                if (now - lastClick < 300) return true;
                lastClick = now;
                mc.gameMode.handleContainerInput(cs.getMenu().containerId, s.index, 0, ContainerInput.PICKUP, mc.player);
                return true;
            }
        }
        return true;
    }

    public static void clearBinds() {
        java.util.Arrays.fill(cfg().petKeyUuids, null);
        TeslaMapsConfig.save();
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
