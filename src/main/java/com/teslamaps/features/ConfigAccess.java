package com.teslamaps.features;

// GUI-017/018/019: per-option reset, gear in the pause menu and the SkyBlock Menu (Skyblocker showConfigButton)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.MapConfigScreen;
import com.teslamaps.screen.TeslaSettingsButton;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class ConfigAccess {

    private ConfigAccess() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ConfigAccess")
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Config Access");
                    b.toggle("Reset Options To Default", () -> c.configResetOptions, v -> c.configResetOptions = v)
                            .desc("Right-click a setting to put it back to its default. Adds a \"Reset To Defaults\" button "
                                    + "above each category (click twice).")
                            .tags("reset", "default");
                    b.toggle("Pause Menu Button", () -> c.pauseMenuConfigButton, v -> c.pauseMenuConfigButton = v)
                            .desc("Gear in the top right of the pause menu, opens this screen and returns to the menu.")
                            .tags("pause", "escape", "gear");
                    b.toggle("SkyBlock Menu Button", () -> c.skyblockMenuConfigButton, v -> c.skyblockMenuConfigButton = v)
                            .desc("Small gear in the top right of the SkyBlock Menu.")
                            .tags("skyblock menu", "gear");
                });

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            TeslaMapsConfig c = cfg();
            if (screen instanceof PauseScreen pause) {
                if (!c.pauseMenuConfigButton || !pause.showsPauseMenu()) return;
                int size = 20;
                Screens.getWidgets(screen).add(new TeslaSettingsButton(w - size - 8, 8, size,
                        () -> mc.setScreen(new MapConfigScreen(pause))));
                return;
            }
            if (!c.skyblockMenuConfigButton || !(screen instanceof AbstractContainerScreen<?> cs)) return;
            if (!"SkyBlock Menu".equals(StringUtil.strip(screen.getTitle().getString()).trim())) return;
            HandledScreenAccessor a = (HandledScreenAccessor) cs;
            Screens.getWidgets(screen).add(new TeslaSettingsButton(a.getX() + a.getImageWidth() - 16, a.getY() + 4, 12,
                    () -> {
                        Minecraft m = Minecraft.getInstance();
                        if (m.player != null) m.player.closeContainer();
                        m.setScreen(new MapConfigScreen());
                    }));
        });
    }
}
