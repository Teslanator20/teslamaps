package com.teslamaps.features;

// Ported from NotEnoughUpdates' PowerStoneStatsDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PowerStoneStats {

    private static final Pattern MP = Pattern.compile("Magical Power: ([\\d,]+)");
    private static final Pattern AT = Pattern.compile("At ([\\d,]+) Magical Power:?");
    private static final Pattern STAT = Pattern.compile("^([+-])([\\d,]+)(\\S*)(.*)$");

    private static Feature feature;
    private static int ticks;
    private static ItemStack lastStack;
    private static int lastMp;
    private static List<Component> lastOut;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PowerStoneStats").when(() -> cfg().powerStoneStats)
                .tick(PowerStoneStats::tick)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Power Stone Stats At Your Magical Power", () -> c.powerStoneStats, v -> c.powerStoneStats = v);
                    b.info("Open Your Bags once so your Magical Power is known");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg().powerStoneStats && feature.active()) onTooltip(stack, lines);
        });
    }

    private static String key() {
        String k = SbProfile.key();
        return k == null ? "" : k;
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        if (!t.equals("Your Bags") && !t.endsWith("Accessory Bag Thaumaturgy")) return;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = MP.matcher(StringUtil.stripTrim(l));
                if (!m.matches()) continue;
                int mp = Integer.parseInt(m.group(1).replace(",", ""));
                Integer old = cfg().magicalPower.put(key(), mp);
                if (old == null || old != mp) TeslaMapsConfig.save();
                return;
            }
        }
    }

    private static boolean applies(List<Component> lines) {
        for (Component l : lines) if (StringUtil.stripTrim(l.getString()).equals("Power Stone")) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).endsWith("Accessory Bag Thaumaturgy");
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        Integer mpBox = cfg().magicalPower.get(key());
        if (mpBox == null || mpBox < 1) return;
        int mp = mpBox;
        if (stack == lastStack && mp == lastMp && lastOut != null && lastOut.size() == lines.size()) {
            for (int i = 0; i < lines.size(); i++) lines.set(i, lastOut.get(i));
            return;
        }
        if (!applies(lines)) return;
        double scaledMine = scale(mp);
        double scaledShown = 0;
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String clean = StringUtil.stripTrim(lines.get(i).getString());
            Matcher at = AT.matcher(clean);
            if (at.matches()) {
                scaledShown = scale(Integer.parseInt(at.group(1).replace(",", "")));
                if (scaledShown <= 0) return;
                lines.set(i, Component.literal("§7At §6" + String.format(Locale.ROOT, "%,d", mp) + " Magical Power§7:"));
                found = true;
                continue;
            }
            if (!found) continue;
            if (clean.isEmpty()) break;
            Matcher sm = STAT.matcher(clean);
            if (!sm.matches()) continue;
            double shown = Double.parseDouble(sm.group(2).replace(",", ""));
            long real = Math.round(shown / scaledShown * scaledMine);
            String text = sm.group(1) + String.format(Locale.ROOT, "%,d", real) + sm.group(3) + sm.group(4);
            lines.set(i, Component.literal(text).withStyle(firstStyle(lines.get(i))));
        }
        if (!found) return;
        lastStack = stack;
        lastMp = mp;
        lastOut = List.copyOf(lines);
    }

    private static Style firstStyle(Component line) {
        Style[] out = {Style.EMPTY};
        line.visit((style, text) -> {
            if (!text.isBlank() && style.getColor() != null) {
                out[0] = style;
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out[0];
    }

    private static double scale(int mp) {
        return Math.pow(29.97 * Math.log(0.0019 * mp + 1), 1.2);
    }
}
