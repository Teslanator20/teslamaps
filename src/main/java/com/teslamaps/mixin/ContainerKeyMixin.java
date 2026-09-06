package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class ContainerKeyMixin {
    private static final java.util.regex.Pattern PAGE = java.util.regex.Pattern.compile("\\((\\d+)/\\d+\\)");

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$containerKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();

        if (com.teslamaps.features.StorageOverlay.active()) {
            if (com.teslamaps.features.StorageOverlay.keyPressed(event.key(), event.scancode())) cir.setReturnValue(true);
            return;
        }

        if (com.teslamaps.features.Searchbar.onKeyPressed(event.key(), event.scancode())) {
            cir.setReturnValue(true);
            return;
        }

        Slot hovered = ((HandledScreenAccessor) (Object) this).getFocusedSlot();
        if (com.teslamaps.features.SlotLock.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.GetFromSacks.onKey(event.key(), hovered)) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.SlotBinds.onKey(event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (com.teslamaps.features.AuctionPriceInput.onContainerKey(cs, event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (mc.options.keyDrop.matches(event) && com.teslamaps.features.SlotLock.blockSlot(hovered)) {
            cir.setReturnValue(true);
            return;
        }

        String title = cs.getTitle().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");

        boolean loadouts = title.endsWith("Loadouts");
        boolean sets = title.startsWith("Wardrobe") || title.endsWith("Armor Sets") || title.endsWith("Equipment Sets");
        if (c.wardrobeKeybinds && (sets || loadouts)) {
            int want = -1;
            for (int i = 0; i < 9; i++) {
                if (c.wardrobeKeys[i] >= 0 && event.key() == c.wardrobeKeys[i]) { want = i; break; }
            }
            if (want < 0) {
                for (int i = 0; i < 9; i++) {
                    if (mc.options.keyHotbarSlots[i].matches(event)) { want = i; break; }
                }
            }
            if (want >= 0) {
                int page = 1;
                java.util.regex.Matcher m = PAGE.matcher(title);
                if (m.find()) page = Integer.parseInt(m.group(1));
                int n = (page - 1) * (loadouts ? 12 : 9) + want + 1;
                String name = (loadouts ? "Loadout " : "Slot ") + n;
                int target = loadouts ? -1 : 36 + want;
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                    String s = slot.getItem().getHoverName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
                    if (s.equalsIgnoreCase(name) || s.regionMatches(true, 0, name + ":", 0, name.length() + 1)) {
                        target = slot.index;
                        break;
                    }
                }
                if (target >= 0) {
                    mc.gameMode.handleContainerInput(cs.getMenu().containerId, target, 0, ContainerInput.PICKUP, mc.player);
                }
                cir.setReturnValue(true);
                return;
            }
        }

        if (c.pageKeybinds) {
            String want = switch (event.key()) {
                case GLFW.GLFW_KEY_A -> "Previous Page";
                case GLFW.GLFW_KEY_D -> "Next Page";
                case GLFW.GLFW_KEY_W -> "Scroll Up";
                case GLFW.GLFW_KEY_S -> "Scroll Down";
                default -> null;
            };
            if (want == null) return;
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (slot.getItem().isEmpty()) continue;
                if (slot.getItem().getHoverName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "").contains(want)) {
                    mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

}
