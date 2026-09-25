package com.teslamaps.features;

// Countdown: NEU's CountdownCalculator.kt, broken fragment: SkyblockAddons' SHOW_BROKEN_FRAGMENTS
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TooltipExtras {

    private static final Pattern TIME = Pattern.compile(
            "(?:(\\d+)y )?(?:(\\d+)d)? ?(?:(\\d+)h)? ?(?:(\\d+)m)? ?(?:(\\d+)s)?\\b");

    private static final String[][] KINDS = {
            {"Starting in:", "Starts at"},
            {"Starts in:", "Starts at"},
            {"Interest in:", "Interest at"},
            {"Until interest:", "Interest at"},
            {"Auction ends in:", "Auction ends at"},
            {"Ends in:", "Ends at"},
            {"Remaining:", "Ends at"},
            {"Duration:", "Finishes at"},
            {"Time left:", "Ends at"},
            {"Ends:", "Finishes at"},
            {"Contribute again", "Come back at"},
            {"Next Charge", "Available at"},
            {"Auction ends in", "Ends at"},
            {"Resets in:", "Resets at"},
    };

    private static final DateTimeFormatter F24 = DateTimeFormatter.ofPattern("EEE, MMM d HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter F12 = DateTimeFormatter.ofPattern("EEE, MMM d h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter F24Y = DateTimeFormatter.ofPattern("EEE, MMM d yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter F12Y = DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm a", Locale.ENGLISH);

    private static final Map<String, String> cache = new HashMap<>();
    private static long cacheTime = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature countdown = Feature.of("CountdownTooltip").when(() -> cfg().countdownTooltip);
        Feature fragment = Feature.of("BrokenFragment").when(() -> cfg().brokenFragmentTooltip);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (fragment.active()) brokenFragment(stack, lines);
            if (countdown.active()) countdown(lines);
        });
    }

    private static void brokenFragment(ItemStack stack, List<Component> lines) {
        if (!ItemUtil.skyblockId(stack).contains("_FRAGMENT")) return;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        CompoundTag tag = d.copyTag();
        if (tag.contains("bossId") && tag.contains("spawnedFor")) lines.add(0, Component.literal("§c§lBROKEN FRAGMENT"));
    }

    private static void countdown(List<Component> lines) {
        long now = System.currentTimeMillis();
        if (now - cacheTime > 1000) {
            cache.clear();
            cacheTime = now;
        }
        for (int i = 0; i < lines.size(); i++) {
            String text = StringUtil.strip(lines.get(i).getString());
            String label = null;
            for (String[] k : KINDS) {
                if (text.contains(k[0])) { label = k[1]; break; }
            }
            if (label == null) continue;
            String out = cache.get(text);
            if (out == null) {
                out = compute(text, label);
                cache.put(text, out);
            }
            if (out.isEmpty()) continue;
            lines.add(++i, Component.literal(out));
        }
    }

    private static String compute(String text, String label) {
        Matcher m = TIME.matcher(text);
        String best = null;
        long[] v = null;
        while (m.find()) {
            if (best != null && m.group().length() <= best.length()) continue;
            best = m.group();
            v = new long[5];
            for (int g = 0; g < 5; g++) v[g] = m.group(g + 1) == null ? 0 : Long.parseLong(m.group(g + 1));
        }
        if (v == null) return "";
        long total = v[0] * 31_536_000L + v[1] * 86_400L + v[2] * 3_600L + v[3] * 60L + v[4];
        if (total == 0) return "";
        ZonedDateTime at = ZonedDateTime.now().plusYears(v[0]).plusDays(v[1]).plusHours(v[2]).plusMinutes(v[3]).plusSeconds(v[4]);
        boolean h24 = cfg().countdownTooltip24h;
        DateTimeFormatter f = v[0] != 0 ? (h24 ? F24Y : F12Y) : (h24 ? F24 : F12);
        return "§b" + label + ": " + f.format(at);
    }
}
