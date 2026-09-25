package com.teslamaps.features.garden;

// Ported from SkyHanni's GardenOptimalSpeed.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.screen.TeslaButton;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OptimalSpeed {

    private static final List<String> SAMPLE = List.of("§cOptimal Speed: §f93 (§eCurrent: §f100§f)");

    private static List<String> lines = List.of();
    private static int width;
    private static long lastWarn;
    private static HudEditElement hud;

    private OptimalSpeed() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static int speed(GardenCrop crop) {
        GardenCrop key = crop == GardenCrop.MOONFLOWER ? GardenCrop.SUNFLOWER : crop;
        Integer v = cfg().gardenOptimalSpeeds.get(key.key);
        return v == null ? key.defaultSpeed : v;
    }

    public static void register() {
        hud = HudEditElement.of("gardenOptimalSpeed", "Optimal Speed").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().gardenOptimalSpeed && cfg().gardenOptimalSpeedHud)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("GardenOptimalSpeed").section("Garden").when(() -> cfg().gardenOptimalSpeed)
                .tick(OptimalSpeed::tick)
                .hudHiddenInInventory("gardenoptimalspeed", OptimalSpeed::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Optimal Speed");
                    b.toggle("Optimal Speed Helper", () -> c.gardenOptimalSpeed, v -> c.gardenOptimalSpeed = v)
                            .desc("Knows the right speed for each crop: HUD, wrong-speed warning and a crop list on the Rancher's Boots sign.")
                            .tags("speed", "rancher", "boots", "setmaxspeed", "farming");
                    b.toggle("  Show HUD", () -> c.gardenOptimalSpeedHud, v -> c.gardenOptimalSpeedHud = v)
                            .desc("\"Optimal Speed: X (Current: Y)\" while holding a farming tool, green when right. Position in /tmap gui.");
                    b.toggle("  Wrong Speed Warning", () -> c.gardenOptimalSpeedWarn, v -> c.gardenOptimalSpeedWarn = v)
                            .desc("Title and chat line while farming with the wrong speed, at most every 20s. With Rancher's Boots the chat line has a clickable /setmaxspeed.");
                    b.toggle("    Only With Rancher's Boots", () -> c.gardenOptimalSpeedRanchersOnly, v -> c.gardenOptimalSpeedRanchersOnly = v);
                    b.toggle("  Rancher's Boots Sign List", () -> c.gardenOptimalSpeedSign, v -> c.gardenOptimalSpeedSign = v)
                            .desc("Buttons with every crop's speed next to the Rancher's Boots speed sign, a click writes the value in.");
                    for (GardenCrop crop : GardenCrop.ALL) {
                        if (crop == GardenCrop.MOONFLOWER) continue;
                        String label = crop == GardenCrop.SUNFLOWER ? "Sunflower / Moonflower" : crop.cropName;
                        b.slider("  " + label + " Speed", 1f, 500f, () -> (float) speed(crop),
                                v -> c.gardenOptimalSpeeds.put(crop.key, Math.round(v)), 0);
                    }
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            TeslaMapsConfig c = cfg();
            if (!c.gardenOptimalSpeed || !c.gardenOptimalSpeedSign || !c.section("Garden")) return;
            if (!(screen instanceof AbstractSignEditScreen) || !SkyblockUtils.isInGarden()) return;
            String[] m = ((AbstractSignEditScreenAccessor) screen).getMessages();
            if (m == null || m.length < 4) return;
            if (!m[1].equals("^^^^^^") || !m[2].startsWith("Set your") || !m[3].endsWith("speed cap!")) return;
            addSignButtons(screen, m);
        });
    }

    private static void addSignButtons(net.minecraft.client.gui.screens.Screen screen, String[] messages) {
        Minecraft mc = Minecraft.getInstance();
        GardenCrop last = GardenCropData.inHand() != null ? GardenCropData.inHand() : GardenCropData.lastBroken();
        Map<Integer, List<GardenCrop>> bySpeed = new LinkedHashMap<>();
        for (GardenCrop crop : GardenCrop.ALL) bySpeed.computeIfAbsent(speed(crop), k -> new ArrayList<>()).add(crop);
        int y = 30;
        for (Map.Entry<Integer, List<GardenCrop>> e : bySpeed.entrySet()) {
            StringBuilder names = new StringBuilder();
            boolean current = false;
            for (GardenCrop crop : e.getValue()) {
                if (!names.isEmpty()) names.append(", ");
                names.append(crop.cropName);
                if (crop == last) current = true;
            }
            String label = (current ? "§6" : "§f") + names + " - " + e.getKey();
            String value = String.valueOf(e.getKey());
            int w = mc.font.width(label) + 10;
            Screens.getWidgets(screen).add(new TeslaButton(8, y, w, 16, label, () -> messages[0] = value));
            y += 18;
        }
    }

    private static int currentSpeed(Minecraft mc) {
        double v = mc.player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 1000;
        return (int) (Math.round(v * 100) / 100.0);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        GardenCrop crop = GardenCropData.inHand();
        if (crop == null || !SkyblockUtils.isInGarden()) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        int optimal = speed(crop);
        int current = currentSpeed(mc);
        boolean switched = System.currentTimeMillis() - GardenCropData.toolSwitchAt() < 1500;
        String text = "Optimal Speed: §f" + optimal + (optimal != current ? " (§eCurrent: §f" + current + "§f)" : "");
        String color = switched ? "§7" : optimal != current ? "§c" : "§a";
        lines = List.of(color + text);
        width = TextHud.width(lines);
        if (optimal != current && !switched && c.gardenOptimalSpeedWarn) warn(mc, c, crop, optimal, current);
    }

    private static void warn(Minecraft mc, TeslaMapsConfig c, GardenCrop crop, int optimal, int current) {
        if (!mc.player.onGround() || !GardenCropData.farming()) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 20_000L) return;
        boolean ranchers = "RANCHERS_BOOTS".equals(ItemUtil.skyblockId(mc.player.getItemBySlot(EquipmentSlot.FEET)));
        if (!ranchers && c.gardenOptimalSpeedRanchersOnly) return;
        lastWarn = now;
        mc.gui.setTimes(0, 40, 5);
        mc.gui.setTitle(Component.literal("§cWrong speed!"));
        mc.gui.setSubtitle(Component.empty());
        Component msg = Component.literal("§b[TeslaMaps] §cWrong speed while farming " + crop.cropName + "! §eCurrent: §f"
                + current + "§e, Optimal: §f" + optimal);
        if (ranchers) {
            String cmd = "/setmaxspeed " + optimal;
            msg = msg.copy().append(Component.literal(" §a[Set " + optimal + "]").withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eRuns " + cmd)))));
        }
        mc.player.sendSystemMessage(msg);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null || !cfg().gardenOptimalSpeedHud) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
