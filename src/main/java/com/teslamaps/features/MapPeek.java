package com.teslamaps.features;

// IllegalMap peekKey + FunnyMap peekMode (Toggle/Hold)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;

public class MapPeek {

    private static boolean active = false;
    private static boolean toggled = false;
    private static boolean wasDown = false;

    public static void register() {
        Feature.of("MapPeek").section("Map").when(() -> TeslaMapsConfig.get().mapPeekKey != -1)
                .tick(MapPeek::tick);
    }

    public static boolean active() {
        return active && TeslaMapsConfig.get().mapPeekKey != -1;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean down = mc.gui.screen() == null && mc.getWindow() != null && KeyNames.isDown(c.mapPeekKey);
        if (down && !wasDown) toggled = !toggled;
        wasDown = down;
        active = c.mapPeekToggle ? toggled : down;
    }
}
