package com.teslamaps.features;

// Ported from SkyHanni's features/fishing/ShowFishingItemName.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class FishedItemNames {

    private record Label(ItemEntity entity, String text) {}

    private static List<Label> labels = List.of();
    private static int timer;
    private static boolean areaOk;

    private FishedItemNames() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FishedItemNames").section("Fishing").when(() -> cfg().fishedItemNames)
                .tick(FishedItemNames::tick)
                .world(FishedItemNames::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Fished Items");
                    b.toggle("Fished Item Names", () -> c.fishedItemNames, v -> c.fishedItemNames = v)
                            .desc("With a rod in hand, dropped items within 15 blocks show \"+ x64 <name>\" above them")
                            .tags("fishing", "item", "name", "ground", "drop");
                    b.toggle("  Show Bait", () -> c.fishedItemNamesBait, v -> c.fishedItemNamesBait = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (timer++ % 20 == 0) areaOk = inCorrectArea();
        if (!areaOk || !FishingHelpers.holdingRod()) {
            labels = List.of();
            return;
        }
        List<Label> next = new ArrayList<>();
        for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(15), x -> x instanceof ItemEntity)) {
            ItemEntity ie = (ItemEntity) e;
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            String id = ItemUtil.skyblockId(stack);
            boolean bait = stack.getCount() == 1 && id.endsWith("_BAIT");
            if (bait && !cfg().fishedItemNamesBait) continue;
            String text;
            if (stack.is(Items.PLAYER_HEAD) && id.isEmpty()) {
                text = "§6Coins";
            } else {
                StringBuilder sb = new StringBuilder(bait ? "§c§l- §r" : "§a§l+ §r");
                if (stack.getCount() != 1) sb.append("§7x").append(stack.getCount()).append(" §r");
                sb.append(bait ? "§7" + StringUtil.strip(stack.getHoverName().getString()) : ChatText.formatted(stack.getHoverName()));
                text = sb.toString();
            }
            next.add(new Label(ie, text));
        }
        labels = next;
    }

    private static boolean inCorrectArea() {
        if (!SkyblockUtils.isInSkyblock()) return false;
        String island = SkyblockUtils.getArea();
        if (island.equalsIgnoreCase("The End")) return false;
        if (island.equalsIgnoreCase("Hub")) {
            String a = FishingHelpers.subArea();
            if (a.endsWith(" Atrium") || a.endsWith(" Museum") || a.equals("Museum")) return false;
            if (a.equals("Fashion Shop") || a.equals("Shen's Auction") || a.equals("Auction House")) return false;
        }
        return true;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Label> list = labels;
        if (list.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Label l : list) {
            ItemEntity e = l.entity;
            if (e.isRemoved()) continue;
            Vec3 p = e.getPosition(pt).add(0, 0.8, 0);
            ESPRenderer.drawText(pose, l.text, p, 1f, cam, false, false);
        }
    }
}
