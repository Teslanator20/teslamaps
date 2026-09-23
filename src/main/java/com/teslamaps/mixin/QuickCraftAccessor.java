package com.teslamaps.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public interface QuickCraftAccessor {
    @Accessor("quickCraftSlots")
    Set<Slot> teslamaps$quickCraftSlots();

    @Accessor("isQuickCrafting")
    boolean teslamaps$isQuickCrafting();

    @Accessor("quickCraftingType")
    int teslamaps$quickCraftingType();
}
