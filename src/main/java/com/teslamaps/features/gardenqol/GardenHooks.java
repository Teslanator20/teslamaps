package com.teslamaps.features.gardenqol;

import com.teslamaps.debug.Perf;
import com.teslamaps.features.visitors.VisitorRewardGuard;
import com.teslamaps.features.visitors.VisitorShoppingList;
import com.teslamaps.features.visitors.Visitors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/** Container hooks of the w14 garden features, one call site each in the shared mixins. */
public final class GardenHooks {

    private GardenHooks() {}

    public static void render(GuiGraphicsExtractor g) {
        Perf.begin("GardenHooks.render");
        try {
            VisitorRewardGuard.render(g);
            VisitorShoppingList.renderContainer(g);
        } finally {
            Perf.end();
        }
    }

    public static boolean click(double mx, double my, int button) {
        return VisitorShoppingList.click(mx, my, button);
    }

    public static boolean blockSlotClick(AbstractContainerScreen<?> cs, Slot slot, int button, ContainerInput type) {
        if (GardenSafety.blockPresetDelete(cs, slot, button, type)) return true;
        if (VisitorRewardGuard.blockClick(slot)) return true;
        Visitors.onAnswer(slot, type);
        return false;
    }
}
