package com.teslamaps.features;

// Ported from Skyblocker's ObtainedDateTooltip
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;

public class ObtainedDate {

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("MMMM d, yyyy")
            .withZone(ZoneId.systemDefault()).localizedBy(Locale.ENGLISH);
    // Hypixel reads the legacy dd/MM/yy strings as MM/dd/yy too, so the museum shows the same odd date
    private static final DateTimeFormatter OLD = DateTimeFormatter.ofPattern("M/d/yy h:m a")
            .withZone(ZoneId.of("UTC")).localizedBy(Locale.ENGLISH);

    private static ItemStack lastStack;
    private static String lastText;

    public static void register() {
        Feature feature = Feature.of("ObtainedDate").section("Inventory")
                .when(() -> TeslaMapsConfig.get().obtainedDate)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Obtained Date");
                    b.toggle("Obtained Date In Tooltip", () -> c.obtainedDate, v -> c.obtainedDate = v);
                    b.info("When the item was created, read from its item data");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (TeslaMapsConfig.get().obtainedDate && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty()) return;
        String text;
        if (stack == lastStack) {
            text = lastText;
        } else {
            text = timestamp(stack);
            lastStack = stack;
            lastText = text;
        }
        if (text == null) return;
        for (Component l : lines) if (l.getString().contains("Obtained: ")) return;
        lines.add(Component.literal("§dObtained: §c" + text));
    }

    private static String timestamp(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        TemporalAccessor at = null;
        if (tag.get("timestamp") instanceof LongTag(long value)) {
            at = Instant.ofEpochMilli(value);
        } else if (tag.get("timestamp") instanceof StringTag(String value)) {
            try {
                at = OLD.parse(value);
            } catch (Exception ignored) {
            }
        }
        return at == null ? null : OUT.format(at);
    }
}
