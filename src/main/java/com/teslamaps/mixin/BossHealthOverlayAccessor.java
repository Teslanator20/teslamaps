package com.teslamaps.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Exposes the active boss bar events so the Blood Camp HP bar can read the Watcher's progress. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> teslamaps$getEvents();
}
