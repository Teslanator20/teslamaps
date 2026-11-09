package com.teslamaps.features;

// Ported from Skysoft's RavengardItemComparisonTooltip.kt + RavengardWeaponDpsTooltip.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RavengardTooltips {

    private static final String NS = "hypixel_ravengard";
    private static final Pattern STAT = Pattern.compile(
            "^(?<icon>\\p{Co})(?<value>[+-]?\\d[\\d,]*(?:\\.\\d+)?)(?<percent>%?) (?<name>.+)$");
    private static final Pattern MULT = Pattern.compile(" \\([+-]?\\d+(?:\\.\\d+)?x\\)$");
    private static final int GAIN = 0x5FEC7B, LOSS = 0xFF5555;
    private static final String CROWN = "👑";

    private record Stat(String name, boolean percent, String icon, BigDecimal value, int nameOffset) {
        String key() { return name + (percent ? "%" : ""); }
    }

    private static boolean building;
    private static boolean nested;
    private static List<ClientTooltipComponent> pending;
    private static long pendingAt;
    private static Identifier pendingStyle;
    private static int frameX, frameY, frameW;
    private static boolean framed;

    private RavengardTooltips() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature feature = Feature.of("RavengardTooltips")
                .when(() -> cfg().ravengardCompare || cfg().ravengardDps);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (stack.isEmpty() || !feature.active()) return;
            Identifier style = stack.getComponents().get(DataComponents.TOOLTIP_STYLE);
            Identifier model = stack.getComponents().get(DataComponents.ITEM_MODEL);
            if (!isNs(style) && !isNs(model)) return;
            if (cfg().ravengardDps && isWeapon(style, model, stack)) addDps(lines);
            if (cfg().ravengardCompare && !building) compare(stack, model, lines);
        });
    }

    private static boolean isNs(Identifier id) {
        return id != null && NS.equals(id.getNamespace());
    }

    // ---------------------------------------------------------------- dps

    private static boolean isWeapon(Identifier style, Identifier model, ItemStack stack) {
        if (!isNs(style)) return false;
        if (stack.getComponents().has(DataComponents.WEAPON)) return true;
        return isNs(model) && model.getPath().startsWith("item/weapons/");
    }

    private static void addDps(List<Component> lines) {
        BigDecimal damage = null;
        int speedIdx = -1;
        BigDecimal speed = null;
        for (int i = 0; i < lines.size(); i++) {
            Stat s = stat(lines.get(i));
            if (s == null || s.percent) continue;
            if (damage == null && s.name.equals("Damage")) damage = s.value;
            if (speedIdx < 0 && s.name.equals("Attack Speed")) { speedIdx = i; speed = s.value; }
        }
        if (damage == null || speed == null || damage.signum() <= 0 || speed.signum() <= 0) return;
        String dps = damage.multiply(speed).stripTrailingZeros().toPlainString();
        lines.add(speedIdx + 1, Component.literal(dps + " DPS").withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- compare

    private static void compare(ItemStack hovered, Identifier model, List<Component> lines) {
        String group = group(model);
        if (group == null) return;
        ItemStack equipped = equipped(hovered, group);
        if (equipped == null || equipped.isEmpty() || equipped == hovered) return;
        ItemLore lore = equipped.getComponents().get(DataComponents.LORE);
        if (lore != null) addDeltas(lines, lore.lines());

        Minecraft mc = Minecraft.getInstance();
        building = true;
        try {
            List<ClientTooltipComponent> comps = new ArrayList<>();
            comps.add(ClientTooltipComponent.create(Component.literal("Equipped")
                    .withStyle(s -> s.withColor(TextColor.fromRgb(0xFFCE47)).withBold(true).withItalic(false))
                    .getVisualOrderText()));
            for (Component c : Screen.getTooltipFromItem(mc, equipped))
                comps.add(ClientTooltipComponent.create(c.getVisualOrderText()));
            pending = comps;
            pendingStyle = equipped.getComponents().get(DataComponents.TOOLTIP_STYLE);
            pendingAt = System.currentTimeMillis();
        } finally {
            building = false;
        }
    }

    private static String group(Identifier model) {
        if (!isNs(model)) return null;
        String path = model.getPath();
        if (path.endsWith("_greyed")) return null;
        if (path.startsWith("item/armor/")) return "item/armor";
        if (!path.startsWith("item/accessories/")) return null;
        String rest = path.substring("item/accessories/".length());
        int slash = rest.indexOf('/');
        String cat = slash < 0 ? rest : rest.substring(0, slash);
        return cat.isBlank() ? null : "item/accessories/" + cat;
    }

    private static ItemStack equipped(ItemStack hovered, String group) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        if (group.equals("item/armor")) {
            Equippable eq = hovered.getComponents().get(DataComponents.EQUIPPABLE);
            if (eq == null || eq.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return null;
            return player.getItemBySlot(eq.slot());
        }
        for (int i = 9; i <= 12; i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (group.equals(group(st.getComponents().get(DataComponents.ITEM_MODEL)))) return st;
        }
        return null;
    }

    private static void addDeltas(List<Component> lines, List<Component> equippedLines) {
        List<Stat> eqStats = new ArrayList<>();
        for (Component c : equippedLines) {
            Stat s = stat(c);
            if (s != null) eqStats.add(s);
        }
        if (eqStats.isEmpty()) return;
        Map<String, Stat> byKey = new HashMap<>();
        for (Stat s : eqStats) byKey.putIfAbsent(s.key(), s);

        Set<String> seen = new HashSet<>();
        int lastIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            Stat s = stat(lines.get(i));
            if (s == null) continue;
            seen.add(s.key());
            lastIdx = i;
            Stat eq = byKey.get(s.key());
            BigDecimal delta = s.value.subtract(eq == null ? BigDecimal.ZERO : eq.value);
            if (delta.signum() != 0) lines.set(i, withDelta(lines.get(i), s, delta));
        }

        List<Component> missing = new ArrayList<>();
        for (Stat s : eqStats) {
            if (seen.contains(s.key()) || s.value.signum() == 0) continue;
            BigDecimal delta = s.value.negate();
            missing.add(Component.literal(s.icon + fmt(delta, s.percent))
                    .withStyle(st -> st.withColor(TextColor.fromRgb(color(delta))).withItalic(false))
                    .append(Component.literal(" " + s.name)
                            .withStyle(st -> st.withColor(TextColor.fromRgb(0xFFFFFF)).withItalic(false))));
        }
        if (missing.isEmpty()) return;
        int at = lastIdx >= 0 ? lastIdx + 1 : fallbackIndex(lines);
        lines.addAll(Math.min(at, lines.size()), missing);
    }

    private static int fallbackIndex(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getString().stripLeading().startsWith(CROWN)) {
                return i > 0 && lines.get(i - 1).getString().isBlank() ? i - 1 : i;
            }
        }
        return lines.size();
    }

    private static Stat stat(Component line) {
        String text = line.getString();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        Matcher m = STAT.matcher(trimmed);
        if (!m.matches()) return null;
        BigDecimal value;
        try {
            value = new BigDecimal(m.group("value").replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        String name = MULT.matcher(m.group("name").trim()).replaceAll("");
        if (name.isEmpty()) return null;
        return new Stat(name, "%".equals(m.group("percent")), m.group("icon"), value,
                text.indexOf(trimmed) + m.start("name"));
    }

    private static Component withDelta(Component line, Stat stat, BigDecimal delta) {
        MutableComponent out = Component.empty();
        int[] offset = {0};
        boolean[] inserted = {false};
        line.visit((Style style, String seg) -> {
            int end = offset[0] + seg.length();
            if (!inserted[0] && stat.nameOffset <= end) {
                int cut = Math.max(0, Math.min(seg.length(), stat.nameOffset - offset[0]));
                out.append(Component.literal(seg.substring(0, cut)).withStyle(style));
                out.append(Component.literal("(" + fmt(delta, stat.percent) + ") ")
                        .withStyle(s -> s.withColor(TextColor.fromRgb(color(delta))).withItalic(false)));
                out.append(Component.literal(seg.substring(cut)).withStyle(style));
                inserted[0] = true;
            } else {
                out.append(Component.literal(seg).withStyle(style));
            }
            offset[0] = end;
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static String fmt(BigDecimal delta, boolean percent) {
        String v = delta.stripTrailingZeros().toPlainString();
        return (delta.signum() > 0 ? "+" + v : v) + (percent ? "%" : "");
    }

    private static int color(BigDecimal delta) {
        return delta.signum() > 0 ? GAIN : LOSS;
    }

    // ---------------------------------------------------------------- side tooltip

    public static ClientTooltipPositioner wrap(ClientTooltipPositioner base) {
        if (nested || pending == null) return base;
        framed = false;
        return (sw, sh, x, y, w, h) -> {
            Vector2ic pos = base.positionTooltip(sw, sh, x, y, w, h);
            float f = TooltipFit.scale();
            frameX = Math.round(pos.x() * f);
            frameY = Math.round(pos.y() * f);
            frameW = Math.round(w * f);
            framed = true;
            return pos;
        };
    }

    public static boolean nested() {
        return nested;
    }

    public static void renderSide(GuiGraphicsExtractor g, Font font) {
        if (nested) return;
        List<ClientTooltipComponent> comps = pending;
        pending = null;
        if (comps == null || !framed) return;
        framed = false;
        if (System.currentTimeMillis() - pendingAt > 100) return;
        int mx = frameX, my = frameY, mw = frameW;
        nested = true;
        try {
            g.nextStratum();
            g.tooltip(font, comps, 0, 0, (sw, sh, x, y, w, h) -> {
                int gap = 12 * 2 + 2;
                int right = mx + mw + gap;
                int left = mx - w - gap;
                int minX = 16;
                int maxX = Math.max(minX, sw - w - 16);
                int tx;
                if (right + w + 12 <= sw - 4) tx = right;
                else if (left - 12 >= 4) tx = left;
                else if (sw - mx - mw >= mx) tx = Math.max(minX, Math.min(maxX, right));
                else tx = Math.max(minX, Math.min(maxX, left));
                int maxY = Math.max(4, sh - h - 4);
                return new Vector2i(tx, Math.max(4, Math.min(maxY, my)));
            }, pendingStyle, true);
        } finally {
            nested = false;
        }
    }
}
