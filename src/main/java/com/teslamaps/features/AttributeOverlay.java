package com.teslamaps.features;

// Ported from SkyHanni's AttributeOverlay.kt + AttributeApi.kt, good rolls from SkyHanni-REPO
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class AttributeOverlay {

    private static final String REPO =
            "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/AttributeGoodRolls.json";
    private static final float SCALE = 0.5714286f;

    private static final Map<String, String> SHORT = new HashMap<>();
    static {
        String[] s = {
                "arachno", "AR", "attack_speed", "AS", "blazing", "BL", "combo", "CO", "elite", "EL", "ender", "EN",
                "ignition", "IG", "life_recovery", "LR", "mana_steal", "MS", "midas_touch", "MT", "undead", "UN",
                "warrior", "WA", "deadeye", "DE", "arachno_resistance", "AR", "blazing_resistance", "BR",
                "breeze", "BZ", "dominance", "DO", "ender_resistance", "ER", "experience", "EX", "fortitude", "FO",
                "life_regeneration", "LR", "lifeline", "LL", "magic_find", "MF", "mana_pool", "MP",
                "mana_regeneration", "MR", "mending", "VI", "speed", "SP", "undead_resistance", "UR",
                "veteran", "VE", "blazing_fortune", "BF", "fishing_experience", "FE", "infection", "IN",
                "double_hook", "DH", "fisherman", "FM", "fishing_speed", "FS", "hunter", "HU", "trophy_hunter", "TH"};
        for (int i = 0; i < s.length; i += 2) SHORT.put(s[i], s[i + 1]);
    }

    private record GoodRoll(Pattern regex, List<String[]> combos) {}
    private record Label(int slot, String[] names, String[] levels) {}

    private static volatile List<GoodRoll> goodRolls = List.of();
    private static boolean loading = false;
    private static final Map<ItemStack, Object> cache = new WeakHashMap<>();
    private static final Object NONE = new Object();

    private static List<Label> labels = List.of();
    private static int ticks = 0;
    private static int cfgSig = -1;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AttributeOverlay").section("Inventory")
                .when(() -> cfg().attributeOverlay)
                .tick(AttributeOverlay::tick);
        com.teslamaps.utils.RepoCache.onReload("AttributeOverlay", () -> { loading = false; goodRolls = List.of(); cache.clear(); });
    }

    private static void load() {
        if (loading) return;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement e = com.teslamaps.utils.RepoCache.fetch("skyhanni-attribute-good-rolls", REPO, 30);
                List<GoodRoll> out = new ArrayList<>();
                JsonObject rolls = e.getAsJsonObject().getAsJsonObject("good_rolls");
                for (Map.Entry<String, JsonElement> r : rolls.entrySet()) {
                    JsonObject o = r.getValue().getAsJsonObject();
                    List<String[]> combos = new ArrayList<>();
                    for (JsonElement c : o.getAsJsonArray("list")) {
                        JsonArray a = c.getAsJsonArray();
                        if (a.size() >= 2) combos.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
                    }
                    out.add(new GoodRoll(Pattern.compile(o.get("regex").getAsString()), combos));
                }
                goodRolls = out;
                Minecraft.getInstance().execute(cache::clear);
            } catch (Exception ex) {
                TeslaMaps.LOGGER.warn("[AttributeOverlay] failed to load good rolls: {}", ex.toString());
            }
        });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        if (cfg().attributeOverlayGoodRolls && goodRolls.isEmpty()) load();
        int sig = (cfg().attributeOverlayGoodRolls ? 1 : 0) | (cfg().attributeOverlayOnlyGood ? 2 : 0) | (cfg().attributeOverlayMinLevel << 2);
        if (sig != cfgSig) {
            cfgSig = sig;
            cache.clear();
        }
        List<Label> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            Object v = cache.get(stack);
            if (v == null) {
                Label l = build(stack);
                cache.put(stack, l == null ? NONE : l);
                v = l == null ? NONE : l;
            }
            if (v instanceof Label l) out.add(new Label(i, l.names, l.levels));
        }
        labels = out;
    }

    private static Label build(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag attrs = d.copyTag().getCompoundOrEmpty("attributes");
        if (attrs.isEmpty()) return null;
        List<String> types = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (String key : attrs.keySet()) {
            String k = key.toLowerCase(java.util.Locale.ROOT);
            if (!SHORT.containsKey(k)) continue;
            types.add(k);
            levels.add(attrs.getIntOr(key, 0));
        }
        if (types.size() != 2) return null;

        String id = ItemUtil.skyblockId(stack);
        GoodRoll roll = null;
        if (cfg().attributeOverlayGoodRolls) {
            for (GoodRoll g : goodRolls) if (g.regex.matcher(id).matches()) { roll = g; break; }
        }
        boolean good = false;
        boolean[] partial = new boolean[2];
        if (roll != null) {
            for (String[] c : roll.combos) {
                if ((c[0].equals(types.get(0)) && c[1].equals(types.get(1)))
                        || (c[1].equals(types.get(0)) && c[0].equals(types.get(1)))) good = true;
                for (int j = 0; j < 2; j++) if (c[0].equals(types.get(j)) || c[1].equals(types.get(j))) partial[j] = true;
            }
        }
        boolean anyPartial = partial[0] || partial[1];
        String color = good ? "§e" : anyPartial ? "§a" : "§b";
        int min = cfg().attributeOverlayMinLevel;
        String[] n = new String[2], lv = new String[2];
        boolean any = false;
        for (int j = 0; j < 2; j++) {
            boolean show = good || levels.get(j) >= min;
            if (cfg().attributeOverlayOnlyGood) show = good || (partial[j] && levels.get(j) >= min);
            if (!show) continue;
            n[j] = color + SHORT.get(types.get(j));
            lv[j] = "§a" + levels.get(j);
            any = true;
        }
        return any ? new Label(-1, n, lv) : null;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty() || !cfg().attributeOverlay || !cfg().section("Inventory")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        var pose = ctx.pose();
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            for (int j = 0; j < 2; j++) {
                if (l.names[j] == null) continue;
                draw(ctx, pose, mc, l.names[j], x, y, j == 1);
                draw(ctx, pose, mc, l.levels[j], x, y + 5.7f, j == 1);
            }
        }
    }

    private static void draw(GuiGraphicsExtractor ctx, org.joml.Matrix3x2fStack pose, Minecraft mc,
                             String s, float x, float y, boolean right) {
        float dx = right ? 16 - mc.font.width(s) * SCALE : 0;
        pose.pushMatrix();
        pose.translate(x + dx, y);
        pose.scale(SCALE, SCALE);
        ctx.text(mc.font, s, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
