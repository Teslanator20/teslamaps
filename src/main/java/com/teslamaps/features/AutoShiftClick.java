package com.teslamaps.features;

// SkyHanni's ShiftClickNpcSell.kt, ShiftClickBrewing.kt, ShiftClickEquipment.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/** Turns plain clicks into shift-clicks in NPC shops (own items), the Brewing Stand and Stats & Equipment. */
public final class AutoShiftClick {

    private AutoShiftClick() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AutoShiftClick").section("Inventory")
                .config("Inventory", b -> {
                    b.toggle("Auto Shift-Click", () -> cfg().autoShiftClick, v -> cfg().autoShiftClick = v)
                            .desc("A normal click acts as shift-click in these menus");
                    b.toggle("  NPC Shops (sell own items)", () -> cfg().autoShiftNpcSell, v -> cfg().autoShiftNpcSell = v);
                    b.toggle("  Brewing Stand", () -> cfg().autoShiftBrewing, v -> cfg().autoShiftBrewing = v);
                    b.toggle("  Stats & Equipment", () -> cfg().autoShiftEquipment, v -> cfg().autoShiftEquipment = v);
                });
    }

    /** True = the click was re-sent as a shift-click, swallow the original. */
    public static boolean handle(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button, ContainerInput type) {
        TeslaMapsConfig c = cfg();
        if (!c.autoShiftClick || !c.section("Inventory")) return false;
        if (slot == null || type != ContainerInput.PICKUP || (button != 0 && button != 1)) return false;
        if (!(screen instanceof ContainerScreen) || !SkyblockUtils.isInSkyblock()) return false;
        if (!matches(screen, slot, slotId, c)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
        return true;
    }

    private static boolean matches(AbstractContainerScreen<?> screen, Slot slot, int slotId, TeslaMapsConfig c) {
        boolean own = slot.container instanceof Inventory;
        if (c.autoShiftEquipment || c.autoShiftBrewing) {
            String title = StringUtil.stripTrim(screen.getTitle().getString());
            if (c.autoShiftBrewing && title.startsWith("Brewing Stand")) return slotId != 49;
            if (c.autoShiftEquipment && title.equals("Stats & Equipment")) return own && !slot.getItem().isEmpty();
        }
        return c.autoShiftNpcSell && own && !slot.getItem().isEmpty() && ItemProtection.isNpcSellMenu(screen.getMenu());
    }
}
