package com.teslamaps.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface GuiAccessor {
    @Accessor("overlayMessageTime")
    int teslamaps$overlayMessageTime();

    @Accessor("subtitle")
    Component teslamaps$subtitle();

    @Accessor("title")
    Component teslamaps$title();

    @Accessor("titleTime")
    int teslamaps$titleTime();
}
