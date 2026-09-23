package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keys that pick a wardrobe slot or a loadout, plus page flipping.
 * <p>
 * Wardrobe and loadouts are kept apart on purpose: a wardrobe page holds 9 entries, a loadout page
 * holds 12. Driving both off the 9 hotbar keys left loadouts 10-12 unreachable. Skyblocker splits
 * them the same way and ships 12 loadout binds for exactly this reason.
 * <p>
 * Every lookup runs twice, once against our own binds and once against the vanilla hotbar mappings,
 * and both entry points share it - a hotbar key bound to a mouse button produces no KeyEvent at
 * all, so the keyboard path alone would never see it.
 */
public class WardrobeKeys {

    private static final Pattern PAGE = Pattern.compile("\\((\\d+)/\\d+\\)");
    private static final int WARDROBE_PER_PAGE = 9;
    private static final int LOADOUTS_PER_PAGE = 12;

    public static boolean handleKey(AbstractContainerScreen<?> cs, KeyEvent event) {
        return handle(cs, event.key(), mapping -> mapping.matches(event));
    }

    public static boolean handleMouse(AbstractContainerScreen<?> cs, MouseButtonEvent event) {
        return handle(cs, KeyNames.forMouse(event.button()), mapping -> mapping.matchesMouse(event));
    }

    public static boolean isWardrobeScreen(String strippedTitle) {
        return strippedTitle.endsWith("Loadouts")
                || strippedTitle.startsWith("Wardrobe")
                || strippedTitle.endsWith("Armor Sets")
                || strippedTitle.endsWith("Equipment Sets");
    }

    /**
     * @param bind        the input encoded the way our config stores binds
     * @param hotbarMatch tests a vanilla hotbar mapping against the same input
     * @return true when the input was consumed
     */
    private static boolean handle(AbstractContainerScreen<?> cs, int bind, Predicate<KeyMapping> hotbarMatch) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.wardrobeKeybinds) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;

        String title = StringUtil.strip(cs.getTitle().getString());
        if (!isWardrobeScreen(title)) return false;
        boolean loadouts = title.endsWith("Loadouts");

        if (bind != -1 && bind == c.wardrobeNextPageKey) return clickNamed(cs, "Next Page");
        if (bind != -1 && bind == c.wardrobePrevPageKey) return clickNamed(cs, "Previous Page");

        int[] own = loadouts ? c.loadoutKeys : c.wardrobeKeys;
        int want = -1;
        for (int i = 0; i < own.length; i++) {
            if (own[i] != -1 && own[i] == bind) { want = i; break; }
        }
        // the hotbar fallback only ever covers the first nine, there are no more hotbar keys
        if (want < 0) {
            for (int i = 0; i < 9; i++) {
                if (hotbarMatch.test(mc.options.keyHotbarSlots[i])) { want = i; break; }
            }
        }
        if (want < 0) return false;

        if (!mc.player.containerMenu.getCarried().isEmpty()) return true;
        long now = System.currentTimeMillis();
        if (now - lastClick < 300) return true;

        int target = findTarget(cs, title, loadouts, want);
        if (target < 0) return true;
        if (c.wardrobeKeepEquipped && isEquipped(cs.getMenu().slots.get(target).getItem())) return true;
        lastClick = now;
        click(cs, target);
        return true;
    }

    private static long lastClick = 0;

    private static int findTarget(AbstractContainerScreen<?> cs, String title, boolean loadouts, int want) {
        int page = 1;
        Matcher m = PAGE.matcher(title);
        if (m.find()) page = Integer.parseInt(m.group(1));

        int perPage = loadouts ? LOADOUTS_PER_PAGE : WARDROBE_PER_PAGE;
        int n = (page - 1) * perPage + want + 1;
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
        if (target < 0 || target >= cs.getMenu().slots.size() || cs.getMenu().slots.get(target).getItem().isEmpty()) return -1;
        return target;
    }

    private static boolean isEquipped(net.minecraft.world.item.ItemStack stack) {
        if (stack.is(net.minecraft.world.item.Items.LIME_DYE)) return true;
        return com.teslamaps.utils.ItemUtil.loreContains(stack, "Click to unequip");
    }

    // ---------------------------------------------------------------- labels

    private static java.util.Map<Integer, String> labels = java.util.Map.of();
    private static int ticks = 0;

    public static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.wardrobeKeybinds || !c.wardrobeShowKeys || !(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (!labels.isEmpty()) labels = java.util.Map.of();
            return;
        }
        String title = StringUtil.strip(cs.getTitle().getString());
        if (!isWardrobeScreen(title)) {
            if (!labels.isEmpty()) labels = java.util.Map.of();
            return;
        }
        boolean loadouts = title.endsWith("Loadouts");
        int[] own = loadouts ? c.loadoutKeys : c.wardrobeKeys;
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        for (int i = 0; i < own.length; i++) {
            String label = own[i] != -1 ? KeyNames.display(own[i])
                    : i < 9 ? mc.options.keyHotbarSlots[i].getTranslatedKeyMessage().getString() : null;
            if (label == null || label.isEmpty()) continue;
            int target = findTarget(cs, title, loadouts, i);
            if (target >= 0) out.put(target, label.length() > 3 ? label.substring(0, 3) : label);
        }
        labels = out;
    }

    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        if (labels.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        com.teslamaps.mixin.HandledScreenAccessor acc = (com.teslamaps.mixin.HandledScreenAccessor) cs;
        var slots = cs.getMenu().slots;
        for (var e : labels.entrySet()) {
            if (e.getKey() >= slots.size()) continue;
            Slot slot = slots.get(e.getKey());
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            ctx.pose().pushMatrix();
            ctx.pose().translate(x + 1, y + 10);
            ctx.pose().scale(0.75f, 0.75f);
            ctx.text(mc.font, e.getValue(), 0, 0, 0xFF55FFFF, true);
            ctx.pose().popMatrix();
        }
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("WardrobeKeys")
                .when(() -> TeslaMapsConfig.get().wardrobeKeybinds)
                .tick(WardrobeKeys::tick);
    }

    /** The page arrows are ordinary items, so they are found by their name like everything else. */
    private static boolean clickNamed(AbstractContainerScreen<?> cs, String needle) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            String s = StringUtil.strip(slot.getItem().getHoverName().getString());
            if (s.startsWith(needle)) {
                click(cs, slot.index);
                return true;
            }
        }
        return true;   // swallow it either way, the key was meant for us
    }

    private static void click(AbstractContainerScreen<?> cs, int slotIndex) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slotIndex, 0,
                ContainerInput.PICKUP, mc.player);
    }
}
