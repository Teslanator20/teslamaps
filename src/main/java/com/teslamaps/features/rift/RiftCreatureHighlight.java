package com.teslamaps.features.rift;

// After SkyHanni's rift/area/wyldwoods/RiftLarva.kt and RiftOdonata.kt
import com.mojang.authlib.properties.Property;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Larva Hook in hand: larvae on the trees glow. Empty Odonata Bottle in hand: Odonatas glow. */
public final class RiftCreatureHighlight {

    private static final String LARVA = "4ceb0ed8fc2272b3d3d820676d52a38e7b2e8da8c687a233e0dabaa16c0e96df";
    private static final String ODONATA = "9fd806defdfdf59b1f2609c8ee364666de66127a623415b5430c9358c601ef7c";
    private static final Map<String, String> TEXTURE_CACHE = new HashMap<>();

    private static List<ArmorStand> marked = List.of();
    private static int color;
    private static int ticks;

    private RiftCreatureHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("RiftCreatureHighlight").section("Rift").when(() -> cfg().riftLarvaHighlight || cfg().riftOdonataHighlight)
                .tick(RiftCreatureHighlight::tick)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Larva Highlight", () -> c.riftLarvaHighlight, v -> c.riftLarvaHighlight = v)
                            .desc("Wyld Woods: while holding the Larva Hook, larvae on the trees glow.")
                            .tags("rift", "larva", "larva hook", "wyld woods");
                    b.color("  Larva Color", () -> c.riftLarvaColor, v -> c.riftLarvaColor = v);
                    b.toggle("Odonata Highlight", () -> c.riftOdonataHighlight, v -> c.riftOdonataHighlight = v)
                            .desc("Wyld Woods: while holding an empty Odonata Bottle, Odonatas glow.")
                            .tags("rift", "odonata", "bottle", "wyld woods");
                    b.color("  Odonata Color", () -> c.riftOdonataColor, v -> c.riftOdonataColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ticks++ % 10 == 0) scan(mc);
        List<ArmorStand> l = marked;
        for (int i = 0; i < l.size(); i++) {
            ArmorStand as = l.get(i);
            if (as.isAlive()) EntityGlow.mark(as, color);
        }
    }

    private static void scan(Minecraft mc) {
        marked = List.of();
        if (mc.player == null || !SkyblockUtils.getArea().contains("Rift")) return;
        TeslaMapsConfig c = cfg();
        String held = ItemUtil.skyblockId(mc.player.getMainHandItem());
        String want;
        EquipmentSlot slot;
        if (c.riftLarvaHighlight && "LARVA_HOOK".equals(held)) {
            want = LARVA;
            slot = EquipmentSlot.HEAD;
            color = TeslaMapsConfig.parseColor(c.riftLarvaColor) & 0xFFFFFF;
        } else if (c.riftOdonataHighlight && "EMPTY_ODONATA_BOTTLE".equals(held)) {
            want = ODONATA;
            slot = EquipmentSlot.MAINHAND;
            color = TeslaMapsConfig.parseColor(c.riftOdonataColor) & 0xFFFFFF;
        } else {
            return;
        }
        List<ArmorStand> out = new ArrayList<>();
        for (ArmorStand as : EntityCache.armorStands()) {
            if (want.equals(texture(as.getItemBySlot(slot)))) out.add(as);
        }
        marked = out;
    }

    private static String texture(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return null;
        for (Property p : profile.partialProfile().properties().get("textures")) {
            if (p == null) continue;
            String v = p.value();
            String hit = TEXTURE_CACHE.get(v);
            if (hit == null) {
                if (TEXTURE_CACHE.size() > 512) TEXTURE_CACHE.clear();
                hit = decode(v);
                TEXTURE_CACHE.put(v, hit);
            }
            if (!hit.isEmpty()) return hit;
        }
        return null;
    }

    private static String decode(String value) {
        try {
            String s = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            if (s.contains(LARVA)) return LARVA;
            if (s.contains(ODONATA)) return ODONATA;
        } catch (IllegalArgumentException ignored) {
        }
        return "";
    }
}
