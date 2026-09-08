package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hotbar keys pick the matching wardrobe slot or loadout. Split out of ContainerKeyMixin so the
 * mouse path can reuse it: a hotbar key bound to a mouse button never produces a KeyEvent, and
 * container screens route clicks to mouseClicked instead of keyPressed.
 */
public class WardrobeKeys {

    private static final Pattern PAGE = Pattern.compile("\\((\\d+)/\\d+\\)");

    /** @return 0-8 for the requested slot, -1 when this input is not a wardrobe key */
    public static int wantedFromKey(KeyEvent event) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 9; i++) {
            if (c.wardrobeKeys[i] != -1 && event.key() == c.wardrobeKeys[i]) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.options.keyHotbarSlots[i].matches(event)) return i;
        }
        return -1;
    }

    /** Same, for a mouse button - both our own binds and a mouse-bound vanilla hotbar key. */
    public static int wantedFromMouse(MouseButtonEvent event) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        int encoded = KeyNames.forMouse(event.button());
        for (int i = 0; i < 9; i++) {
            if (c.wardrobeKeys[i] != -1 && c.wardrobeKeys[i] == encoded) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.options.keyHotbarSlots[i].matchesMouse(event)) return i;
        }
        return -1;
    }

    public static boolean isWardrobeScreen(String strippedTitle) {
        return strippedTitle.endsWith("Loadouts")
                || strippedTitle.startsWith("Wardrobe")
                || strippedTitle.endsWith("Armor Sets")
                || strippedTitle.endsWith("Equipment Sets");
    }

    /** @return true when the input was consumed */
    public static boolean handle(AbstractContainerScreen<?> cs, int want) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.wardrobeKeybinds || want < 0) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;

        String title = StringUtil.strip(cs.getTitle().getString());
        if (!isWardrobeScreen(title)) return false;
        boolean loadouts = title.endsWith("Loadouts");

        int page = 1;
        Matcher m = PAGE.matcher(title);
        if (m.find()) page = Integer.parseInt(m.group(1));

        int n = (page - 1) * (loadouts ? 12 : 9) + want + 1;
        String name = (loadouts ? "Loadout " : "Slot ") + n;
        int target = loadouts ? -1 : 36 + want;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            String s = StringUtil.strip(slot.getItem().getHoverName().getString());
            if (s.equalsIgnoreCase(name) || s.regionMatches(true, 0, name + ":", 0, name.length() + 1)) {
                target = slot.index;
                break;
            }
        }

        if (target >= 0) {
            mc.gameMode.handleContainerInput(cs.getMenu().containerId, target, 0,
                    ContainerInput.PICKUP, mc.player);
        }
        return true;
    }
}
