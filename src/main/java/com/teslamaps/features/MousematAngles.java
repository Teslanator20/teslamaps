package com.teslamaps.features;

// Ported from SkyHanni's GardenOptimalAngles.kt (defaults from CustomAnglesConfig.kt)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.TeslaButton;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MousematAngles {

    private static final String[] CROPS = {"Cactus", "Carrot", "Cocoa Beans", "Melon", "Mushroom", "Nether Wart",
            "Potato", "Pumpkin", "Sugar Cane", "Wheat", "Sunflower", "Wild Rose"};
    private static final float[][] DEFAULTS = {{-90f, 0f}, {-90f, 2.8f}, {180f, -45f}, {90f, -58.5f}, {116.5f, 0f}, {90f, 0f},
            {-90f, 2.8f}, {90f, -58.5f}, {-135f, 0f}, {90f, 0f}, {45f, 0f}, {45f, 0f}};

    private static String lastCrop;
    private static String editCrop = CROPS[0];

    private MousematAngles() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MousematAngles").section("Garden").when(() -> cfg().mousematAngles)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mousemat Optimal Angles", () -> c.mousematAngles, v -> c.mousematAngles = v)
                            .desc("Clickable crop list next to the Squeaky Mousemat sign: a click fills in yaw and pitch. The last farmed crop is gold.")
                            .tags("mousemat", "squeaky", "yaw", "pitch", "angle");
                    b.toggle("  Compact (Group Same Angles)", () -> c.mousematCompact, v -> c.mousematCompact = v);
                    b.dropdown("  Edit Crop", CROPS, () -> editCrop, v -> editCrop = v);
                    b.slider("    Yaw", -180f, 180f, () -> get(editCrop, 0), v -> c.mousematAngleValues.put(key(editCrop, 0), round(v)), 1);
                    b.slider("    Pitch", -90f, 90f, () -> get(editCrop, 1), v -> c.mousematAngleValues.put(key(editCrop, 1), round(v)), 1);
                    b.button("    Reset Angles", () -> c.mousematAngleValues.clear());
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            TeslaMapsConfig c = cfg();
            if (!c.mousematAngles || !c.section("Garden") || !SkyblockUtils.isInGarden()) return;
            if (!(screen instanceof AbstractSignEditScreen)) return;
            String[] messages = ((AbstractSignEditScreenAccessor) screen).getMessages();
            if (messages == null || messages.length < 4) return;
            if (!messages[1].equals("Set Yaw Above!") || !messages[2].equals("Set Pitch Below!")) return;
            int x = 8, y = 30, w = 150;
            if (c.mousematCompact) {
                Map<String, List<String>> groups = new LinkedHashMap<>();
                for (String crop : CROPS) groups.computeIfAbsent(label(crop), k -> new ArrayList<>()).add(crop);
                for (Map.Entry<String, List<String>> e : groups.entrySet()) {
                    String first = e.getValue().get(0);
                    String names = String.join(", ", e.getValue());
                    String text = (e.getValue().contains(lastCrop) ? "§6" : "§a") + names + " §7- §e" + e.getKey();
                    int bw = Math.max(w, mc.font.width(text) + 8);
                    Screens.getWidgets(screen).add(new TeslaButton(x, y, bw, 14, text, () -> set(messages, first)));
                    y += 16;
                }
            } else {
                for (String crop : CROPS) {
                    String text = (crop.equals(lastCrop) ? "§6" : "§a") + crop + " §7- §e" + label(crop);
                    Screens.getWidgets(screen).add(new TeslaButton(x, y, w, 14, text, () -> set(messages, crop)));
                    y += 16;
                }
            }
        });
    }

    /** From FarmSpeed, every crop broken in the garden. */
    public static void onCrop(BlockState state) {
        if (!cfg().mousematAngles) return;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String crop = null;
        if (path.equals("cactus")) crop = "Cactus";
        else if (path.equals("carrots")) crop = "Carrot";
        else if (path.equals("cocoa")) crop = "Cocoa Beans";
        else if (path.startsWith("melon")) crop = "Melon";
        else if (path.contains("mushroom")) crop = "Mushroom";
        else if (path.equals("nether_wart")) crop = "Nether Wart";
        else if (path.equals("potatoes")) crop = "Potato";
        else if (path.contains("pumpkin")) crop = "Pumpkin";
        else if (path.equals("sugar_cane")) crop = "Sugar Cane";
        else if (path.equals("wheat") || path.equals("hay_block")) crop = "Wheat";
        else if (path.equals("sunflower") || path.contains("moonflower")) crop = "Sunflower";
        else if (path.equals("rose_bush") || path.contains("rose")) crop = "Wild Rose";
        if (crop != null) lastCrop = crop;
    }

    private static void set(String[] messages, String crop) {
        messages[0] = fmt(get(crop, 0));
        messages[3] = fmt(get(crop, 1));
    }

    private static String label(String crop) {
        return fmt(get(crop, 0)) + "§7/§e" + fmt(get(crop, 1));
    }

    private static String key(String crop, int i) {
        return crop.toLowerCase(Locale.ROOT).replace(' ', '_') + (i == 0 ? "_yaw" : "_pitch");
    }

    private static float get(String crop, int i) {
        Float v = cfg().mousematAngleValues.get(key(crop, i));
        if (v != null) return v;
        for (int k = 0; k < CROPS.length; k++) if (CROPS[k].equals(crop)) return DEFAULTS[k][i];
        return 0f;
    }

    private static float round(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static String fmt(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
