package com.teslamaps.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
    @Accessor("sign")
    SignBlockEntity getSign();

    @Accessor("messages")
    String[] getMessages();

    @Accessor("line")
    int getLine();

    @Accessor("line")
    void setLine(int line);

    @Accessor("signField")
    net.minecraft.client.gui.font.TextFieldHelper getSignField();

    @Invoker("setMessage")
    void invokeSetMessage(String message);
}
