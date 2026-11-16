package com.teslamaps.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

// 26.3 only sends typed chars while a text input is started; our drawn search bars must ask for it
public class TextInputSync {
    private static final Object OWNER = new Object();
    private static boolean on = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean want = mc.gui.screen() instanceof AbstractContainerScreen<?>
                    && (com.teslamaps.features.StorageOverlay.wantsText()
                    || com.teslamaps.features.hunting.ShardOverlay.wantsText()
                    || com.teslamaps.features.Searchbar.wantsText());
            if (want == on) return;
            on = want;
            mc.textInputManager().onTextInputFocusChange(OWNER, want);
        });
    }
}
