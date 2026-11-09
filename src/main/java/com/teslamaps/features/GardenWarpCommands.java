package com.teslamaps.features;

// Ported from SkyHanni's GardenWarpCommands.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** On the Garden only: /home, /barn and /tp <plot> become warp/plottp, plus three hotkeys. */
public final class GardenWarpCommands {

    private static final boolean[] down = new boolean[3];

    private GardenWarpCommands() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientSendMessageEvents.MODIFY_COMMAND.register(GardenWarpCommands::modify);
        Feature.of("GardenWarpKeys").section("Garden")
                .when(() -> cfg().gardenWarpKey != -1 || cfg().gardenSetspawnKey != -1 || cfg().gardenBarnKey != -1)
                .tick(GardenWarpCommands::tick);
    }

    static String modify(String command) {
        if (command == null || !cfg().gardenWarpAliases || !cfg().section("Garden") || !SkyblockUtils.isInGarden()) return command;
        String low = command.trim().toLowerCase(Locale.ROOT);
        if (low.equals("home")) return "warp garden";
        if (low.equals("barn")) return "plottp barn";
        if (low.startsWith("tp ") && low.length() > 3) return "plottp " + command.trim().substring(3).trim();
        return command;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || !SkyblockUtils.isInGarden() || mc.getConnection() == null) {
            java.util.Arrays.fill(down, true);
            return;
        }
        TeslaMapsConfig c = cfg();
        key(0, c.gardenWarpKey, "warp garden");
        key(1, c.gardenSetspawnKey, "setspawn");
        key(2, c.gardenBarnKey, "plottp barn");
    }

    private static void key(int i, int key, String command) {
        boolean now = key != -1 && KeyNames.isDown(key);
        if (now && !down[i]) Minecraft.getInstance().getConnection().sendCommand(command);
        down[i] = now;
    }
}
