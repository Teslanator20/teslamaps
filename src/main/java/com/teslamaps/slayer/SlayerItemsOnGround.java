package com.teslamaps.slayer;

// Ported from SkyHanni's SlayerItemsOnGround.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SlayerItemsOnGround {

    private record Label(ItemEntity item, String text, int color) {}

    private static List<Label> labels = List.of();
    private static int ticks;

    private SlayerItemsOnGround() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerItemsOnGround").section("Combat").when(() -> cfg().slayerItemsOnGround)
                .tick(SlayerItemsOnGround::tick)
                .world(SlayerItemsOnGround::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Slayer QoL");
                    b.toggle("Slayer Items On Ground", () -> c.slayerItemsOnGround, v -> c.slayerItemsOnGround = v)
                            .desc("With a slayer quest in its area: name and price above dropped items within 15 blocks")
                            .tags("slayer", "drop", "price", "ground");
                    b.slider("  Minimum Price (k coins)", 0, 5000, () -> (float) c.slayerItemsMinPrice,
                            v -> c.slayerItemsMinPrice = Math.round(v), 0);
                });
    }

    static boolean inSlayerArea() {
        String boss = SlayerQuest.boss();
        if (boss == null || !SkyblockUtils.isInSkyblock()) return false;
        String area = SkyblockUtils.getArea();
        if (boss.contains("Revenant") || boss.contains("Atoned") || boss.contains("Sven")) return area.equals("Hub");
        if (boss.contains("Tarantula") || boss.contains("Conjoined")) return area.equals("Spider's Den") || area.equals("Crimson Isle");
        if (boss.contains("Voidgloom")) return area.equals("The End");
        if (boss.contains("Demonlord")) return area.equals("Crimson Isle");
        if (boss.contains("Bloodfiend")) return area.equals("The Rift");
        return false;
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        if (!inSlayerArea()) {
            labels = List.of();
            return;
        }
        PriceManager.ensureFresh();
        Minecraft mc = Minecraft.getInstance();
        double min = cfg().slayerItemsMinPrice * 1000.0;
        List<Label> out = new ArrayList<>();
        for (ItemEntity e : EntityCache.byType(ItemEntity.class)) {
            if (e.distanceToSqr(mc.player) > 15 * 15) continue;
            ItemStack stack = e.getItem();
            if (stack.getItem() instanceof SpawnEggItem) continue;
            String id = ItemUtil.skyblockId(stack);
            if (id.isEmpty()) continue;
            double price = PriceManager.getPrice(id) * stack.getCount();
            if (price < min) continue;
            String name = stack.getHoverName().getString();
            if (stack.getCount() > 1) name = stack.getCount() + "x " + name;
            if (price > 0) name += " §6" + ItemPrice.format(price);
            out.add(new Label(e, name, color(stack)));
        }
        labels = out;
    }

    private static int color(ItemStack stack) {
        Optional<TextColor> c = stack.getHoverName().visit((style, s) ->
                style.getColor() != null && !s.isBlank() ? Optional.of(style.getColor()) : Optional.empty(),
                net.minecraft.network.chat.Style.EMPTY);
        return 0xFF000000 | c.map(TextColor::getValue).orElse(0xFFFFFF);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (labels.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Label l : labels) {
            if (!l.item.isAlive()) continue;
            Vec3 p = l.item.getPosition(pt).add(0, 0.8, 0);
            ESPRenderer.drawText(pose, l.text, p, 1.0f, cam, true, true, l.color);
        }
    }
}
