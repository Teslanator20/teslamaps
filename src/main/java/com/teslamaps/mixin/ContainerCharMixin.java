package com.teslamaps.mixin;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;

// container screens never declared charTyped (it is the ContainerEventHandler default), so the
// search bars only ever saw raw key codes -> no shifted symbols like ':'
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerCharMixin implements ContainerEventHandler {

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (com.teslamaps.features.StorageOverlay.charTyped(event.codepoint())) return true;
        if (com.teslamaps.features.hunting.ShardOverlay.charTyped(event.codepoint())) return true;
        if (com.teslamaps.features.Searchbar.charTyped(event.codepoint())) return true;
        if (com.teslamaps.features.EcoOverlays.charTyped(event.codepoint())) return true;
        GuiEventListener focused = getFocused();
        return focused != null && focused.charTyped(event);
    }
}
