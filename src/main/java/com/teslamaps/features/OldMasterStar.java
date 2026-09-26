package com.teslamaps.features;

// Ported from Devonian's OldMasterStar.kt
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class OldMasterStar {

    private static final String MASTER = "➊➋➌➍➎";
    private static final Map<Component, Component> CACHE = new IdentityHashMap<>();

    private static com.teslamaps.registry.Feature feature;

    private OldMasterStar() {}

    public static void register() {
        feature = com.teslamaps.registry.Feature.of("OldMasterStar").section("Inventory")
                .when(() -> TeslaMapsConfig.get().oldMasterStars && TeslaMapsConfig.get().starsStackSize);
    }

    public static void clear() {
        synchronized (CACHE) { CACHE.clear(); }
    }

    public static Component transform(Component name) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (name == null || c == null || !c.oldMasterStars || !c.starsStackSize || feature == null || !feature.active()) return name;
        synchronized (CACHE) {
            Component hit = CACHE.get(name);
            if (hit != null) return hit;
            if (CACHE.size() > 2048) CACHE.clear();
            Component out = build(name);
            CACHE.put(name, out);
            return out;
        }
    }

    private static String literal(Component c) {
        return c.getContents() instanceof PlainTextContents.LiteralContents(String text) ? text : null;
    }

    private static Component build(Component name) {
        List<Component> sib = name.getSiblings();
        int l = sib.size();
        if (l >= 2) {
            int i = 0;
            Component normal = sib.get(l - 2);
            Component stars = sib.get(l - 1);
            String text = literal(stars);
            if ("✦".equals(text)) {
                if (l < 3) return name;
                i = 1;
                normal = sib.get(l - 3);
                stars = sib.get(l - 2);
                text = literal(stars);
                if (text != null) text = text.trim();
            }
            int n = text == null || text.length() != 1 ? 0 : MASTER.indexOf(text.charAt(0)) + 1;
            if (n > 0) {
                MutableComponent comp = MutableComponent.create(name.getContents()).withStyle(name.getStyle());
                comp.getSiblings().addAll(sib.subList(0, l - 2 - i));
                comp.getSiblings().add(Component.literal("✪".repeat(n)).withStyle(stars.getStyle()));
                if (n != 5) comp.getSiblings().add(Component.literal("✪".repeat(5 - n)).withStyle(normal.getStyle()));
                if (i > 0) {
                    comp.getSiblings().add(Component.literal(" "));
                    comp.getSiblings().addAll(sib.subList(l - i, l));
                }
                return comp;
            }
        }
        return legacy(name);
    }

    // names that carry literal § codes instead of styled siblings
    private static Component legacy(Component name) {
        String s = name.getString();
        if (s.indexOf('§') < 0) return name;
        int at = -1, n = 0;
        for (int k = s.length() - 1; k >= 0; k--) {
            int idx = MASTER.indexOf(s.charAt(k));
            if (idx >= 0) { at = k; n = idx + 1; break; }
        }
        if (at < 0) return name;
        int starEnd = at;
        while (starEnd >= 2 && s.charAt(starEnd - 2) == '§') starEnd -= 2;
        if (starEnd < 5 || !s.startsWith("✪✪✪✪✪", starEnd - 5)) return name;
        int starStart = starEnd - 5;
        String starColor = s.substring(starEnd, at);
        if (starColor.isEmpty()) starColor = "§c";
        String normalColor = "§6";
        int p = starStart;
        StringBuilder codes = new StringBuilder();
        while (p >= 2 && s.charAt(p - 2) == '§') {
            codes.insert(0, s, p - 2, p);
            p -= 2;
        }
        if (!codes.isEmpty()) normalColor = codes.toString();
        String out = s.substring(0, starStart) + starColor + "✪".repeat(n)
                + (n < 5 ? normalColor + "✪".repeat(5 - n) : "") + s.substring(at + 1);
        return Component.literal(out);
    }
}
