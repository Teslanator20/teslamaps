package com.teslamaps.adm.practice.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface AdmPracticeMinecraftAccessor {
    @Accessor("rightClickDelay")
    void teslamaps$admPracticeRightClickDelay(int delay);
}
