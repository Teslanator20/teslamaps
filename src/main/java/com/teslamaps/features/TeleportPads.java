package com.teslamaps.features;

// Ported from SkyHanni's TeleportPadCompactName.kt + TeleportPadInventoryNumber.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class TeleportPads {

    private record Label(int slot, String text) {}

    private static final Map<String, Integer> NUMBERS = new HashMap<>();

    static {
        String[] base = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
                "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
        String[] tens = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < base.length; i++) NUMBERS.put(base[i], i + 1);
        for (int t = 0; t < tens.length; t++) {
            int v = (t + 2) * 10;
            NUMBERS.put(tens[t], v);
            for (int i = 0; i < 9; i++) NUMBERS.put(tens[t] + base[i], v + i + 1);
        }
    }

    private static List<Label> labels = List.of();
    private static int ticks;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TeleportPads").section("Render")
                .when(() -> cfg().teleportPadCompactName || cfg().teleportPadNumbers)
                .tick(TeleportPads::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Private Island Teleport Pads");
                    b.toggle("Compact Pad Names", () -> c.teleportPadCompactName, v -> c.teleportPadCompactName = v)
                            .desc("\"Warp To X\" becomes \"X\", \"No Destination\" tags are hidden.")
                            .tags("teleport", "pad", "island");
                    b.toggle("Pad Number In Set Destination", () -> c.teleportPadNumbers, v -> c.teleportPadNumbers = v)
                            .desc("Shows each pad's number on its slot in the Set Destination menu.");
                });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        labels = c.teleportPadNumbers ? buildLabels(mc) : List.of();
        if (c.teleportPadCompactName && ticks % 10 == 0 && SkyblockUtils.isInArea("Private Island")) compactNames();
    }

    private static void compactNames() {
        for (ArmorStand stand : EntityCache.namedArmorStands()) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            String plain = StringUtil.stripTrim(name.getString());
            if (plain.equals("✦ No Destination")) {
                if (stand.isCustomNameVisible()) stand.setCustomNameVisible(false);
            } else if (plain.startsWith("✦ Warp To ")) {
                Component cut = after(name, "Warp To ");
                if (cut != null) stand.setCustomName(cut);
            }
        }
    }

    // rebuild the styled tail of a component starting after the marker text
    private static Component after(Component name, String marker) {
        StringBuilder raw = new StringBuilder();
        name.visit((style, text) -> {
            raw.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        int idx = raw.indexOf(marker);
        if (idx < 0) return null;
        int cut = idx + marker.length();
        MutableComponent out = Component.empty();
        int[] pos = {0};
        name.visit((style, text) -> {
            int start = pos[0], end = start + text.length();
            pos[0] = end;
            if (end > cut) out.append(Component.literal(text.substring(Math.max(0, cut - start))).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static List<Label> buildLabels(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return List.of();
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Set Destination")) return List.of();
        List<Label> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory || !slot.hasItem()) continue;
            String name = StringUtil.stripTrim(slot.getItem().getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (!name.endsWith(" teleport pad")) continue;
            Integer n = NUMBERS.get(name.substring(0, name.length() - " teleport pad".length()).trim());
            if (n != null) out.add(new Label(i, String.valueOf(n)));
        }
        return out;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            SlotText.draw(ctx, mc.font, l.text, acc.getX() + slot.x, acc.getY() + slot.y, 3, 0xFFFFFFFF);
        }
    }
}
