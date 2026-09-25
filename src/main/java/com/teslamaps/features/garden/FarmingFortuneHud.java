package com.teslamaps.features.garden;

// After SkyHanni's FarmingFortuneDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.PestData;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** True farming fortune (universal + crop) from the tab Stats widget, pest reduction, Philip bonus. */
public final class FarmingFortuneHud {

    private static final Pattern FORTUNE = Pattern.compile("^([A-Za-z ]+) Fortune: ☘?([\\d,.]+)");
    private static final Set<String> CROPS = Set.of("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Melon Slice",
            "Cocoa Beans", "Sugar Cane", "Cactus", "Mushroom", "Nether Wart", "Sunflower", "Moonflower", "Wild Rose");
    private static final List<String> SAMPLE = List.of("§6Farming Fortune§7: §e1,834 §8(Wheat)", "§eBonus: §6+200☘ §b29m");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static boolean philipWas;

    private FarmingFortuneHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("farmingFortuneHud", "Farming Fortune").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().farmingFortuneHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> {
                    if (lines.isEmpty()) TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE));
                    else TextHud.draw(g, mc, x, y, s, lines, width);
                });
        Feature.of("FarmingFortuneHud").section("Garden")
                .when(() -> cfg().farmingFortuneHud || cfg().farmingFortunePhilipChat)
                .tick(FarmingFortuneHud::tick)
                .hudHiddenInInventory("farmingfortunehud", FarmingFortuneHud::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Farming Fortune");
                    b.toggle("Farming Fortune HUD", () -> c.farmingFortuneHud, v -> c.farmingFortuneHud = v)
                            .desc("Universal + crop fortune from the tab Stats widget. Turns red when pests cut your fortune. "
                                    + "Enable Farming Fortune and the latest crop fortune in /widget > Stats.")
                            .tags("ff", "true ff", "fortune");
                    b.toggle("  Show Pest Fortune Bonus", () -> c.farmingFortuneBonus, v -> c.farmingFortuneBonus = v);
                    b.toggle("  Expiry Chat With Call Button", () -> c.farmingFortunePhilipChat, v -> c.farmingFortunePhilipChat = v)
                            .desc("Chat line when the Phillip bonus runs out; clicking it calls Phillip.");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        if (!SkyblockUtils.isInGarden()) {
            lines = List.of();
            philipWas = false;
            return;
        }
        TeslaMapsConfig c = cfg();
        philipCheck(c);
        if (!c.farmingFortuneHud) return;

        List<String> tab = TabListUtils.getTabListLines();
        double universal = -1, crop = -1;
        String cropName = null;
        for (String line : tab) {
            String s = StringUtil.stripTrim(line);
            if (!s.contains(" Fortune: ")) continue;
            Matcher m = FORTUNE.matcher(s);
            if (!m.find()) continue;
            String name = m.group(1).trim();
            double v;
            try {
                v = Double.parseDouble(m.group(2).replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (name.equals("Farming")) universal = v;
            else if (CROPS.contains(name)) { crop = v; cropName = name; }
        }

        List<String> out = new ArrayList<>(4);
        int reduction = pestReduction();
        if (universal < 0) {
            if (tab.size() >= 20) out.add("§cNo Farming Fortune in tab §7(/widget > Stats)");
        } else {
            double total = universal + Math.max(0, crop);
            String color = reduction > 0 ? "§c" : "§e";
            out.add("§6Farming Fortune§7: " + color + String.format(java.util.Locale.ROOT, "%,.0f", total)
                    + (cropName != null ? " §8(" + cropName + ")" : ""));
            if (crop < 0 && tab.size() >= 20) out.add("§7no crop fortune in tab, break a crop");
        }
        if (c.farmingFortuneBonus && PestData.hasPestSection()) {
            int left = PestData.philipSeconds();
            out.add(left < 0 ? "§eBonus: §cInactive!" : "§eBonus: §6+" + PestData.philipBonus() + "☘ §b"
                    + (left >= 60 ? left / 60 + "m" : left + "s"));
        }
        if (reduction > 0) out.add("§cPests are reducing your fortune by §e" + reduction + "%§c!");
        lines = out;
        width = TextHud.width(out);
    }

    // SkyHanni's table: bonus pest chance above 100% lets that many pests live for free
    private static int pestReduction() {
        int pests = Math.max(0, PestData.alive() - PestData.bonusPestChance() / 100);
        if (pests <= 3) return 0;
        return switch (pests) {
            case 4 -> 5;
            case 5 -> 15;
            case 6 -> 30;
            case 7 -> 50;
            default -> 75;
        };
    }

    private static void philipCheck(TeslaMapsConfig c) {
        if (!PestData.hasPestSection()) return;
        boolean active = PestData.hasPhilipBonus();
        if (philipWas && !active && c.farmingFortunePhilipChat) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cPest fortune buff has expired! §e[Call Phillip]")
                        .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/call philip"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to call Phillip")))));
            }
        }
        philipWas = active;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null || !cfg().farmingFortuneHud) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
