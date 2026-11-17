package com.teslamaps.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

// 26.3 only sends typed chars while a text input is started; our drawn text fields must ask for it
public class TextInputSync {
    private static final Object OWNER = new Object();
    private static boolean on = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean want = wants(mc.gui.screen());
            // re-claim every tick: a vanilla EditBox losing focus would otherwise stop our input
            if (want) mc.textInputManager().onTextInputFocusChange(OWNER, true);
            else if (on) mc.textInputManager().onTextInputFocusChange(OWNER, false);
            on = want;
        });
    }

    private static boolean wants(Screen s) {
        if (s instanceof AbstractContainerScreen<?>)
            return com.teslamaps.features.StorageOverlay.wantsText()
                    || com.teslamaps.features.hunting.ShardOverlay.wantsText()
                    || com.teslamaps.features.Searchbar.wantsText();
        if (s instanceof com.teslamaps.screen.MapConfigScreen m) return m.wantsText();
        if (s instanceof com.teslamaps.profileviewer.screen.ProfileViewerScreen p) return p.wantsText();
        return false;
    }
}
